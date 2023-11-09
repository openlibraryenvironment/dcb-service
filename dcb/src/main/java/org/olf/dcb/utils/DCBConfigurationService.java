package org.olf.dcb.utils;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;

import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import org.olf.dcb.core.api.exceptions.FileUploadValidationException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Singleton;


import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.core.annotation.Introspected;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.core.model.ReferenceValueMapping;
import services.k_int.utils.UUIDUtils;

@Singleton
public class DCBConfigurationService {

	private HttpClient httpClient;
        private final NumericRangeMappingRepository numericRangeMappingRepository;
        private final ReferenceValueMappingRepository referenceValueMappingRepository;

	public DCBConfigurationService(
		HttpClient httpClient,
		ReferenceValueMappingRepository referenceValueMappingRepository,
		NumericRangeMappingRepository numericRangeMappingRepository) {
		this.httpClient = httpClient;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	private static final Logger log = LoggerFactory.getLogger(DCBConfigurationService.class);

	public Mono<ConfigImportResult> importConfiguration(String profile, String url) {

		log.debug("importConfiguration({},{})",profile,url);

		return switch ( profile ) {
			case "numericRangeMappingImport" ->
				numericRangeImport(url);
			case "referenceValueMappingImport" -> referenceValueMappingImport(url);
			default -> Mono.just(ConfigImportResult.builder().message("ERROR").build());
		};
	}

	private Mono<ConfigImportResult> referenceValueMappingImport(String url) {
                HttpRequest<?> request = HttpRequest.GET(url);

                return Mono.from(httpClient.exchange(request, String.class))
                                .flatMapMany( this::extractData )
                                .concatMap( nrmr -> {
                                        log.debug("Process ref value mapping: {}",nrmr.toString());
                                        return processReferenceValueMapping(nrmr);
                                })
                                .collectList()
                                .map(recordList -> ConfigImportResult.builder()
                                        .message("OK")
                                        .recordsImported(Long.valueOf(recordList.size()))
                                        .build());
	}

	// Below are the override methods for uploaded mappings.
	// These methods take:
	// A 'mappingCategory' - for example 'CirculationStatus' - this is supplied by the user from the admin app.
	// A 'code' - for example 'ARCHWAY' - also supplied by the user from the admin app.
	// And a CompletedFileUpload for the CSV/TSV mappings file to be processed.
	public Mono<UploadedConfigImport> importConfiguration(String mappingCategory, String code, CompletedFileUpload file) {
		// This will support all mappings which get uploaded via a file, as opposed to being taken from a URL.
		log.debug("importConfiguration({},{})",mappingCategory,file.getFilename());
		String[] expectedHeaders;
		// Switch on category - different validations will be needed for different categories as we expand this feature.
		// New cases will be added here when official support is added for them.
		switch (mappingCategory) {
			case "CirculationStatus" -> {
				// Define expected headers for "CirculationStatus"
				expectedHeaders = new String[]{"Code", "Meaning", "DCB Available or Unavailable"};
				return referenceValueMappingImport(file, code, mappingCategory, expectedHeaders);
			}
			// Example of adding a new category.
			//	case "ShelvingLocation" -> {
			//		expectedHeaders = new String[]{"This", "Is", "Test"};
			//		return referenceValueMappingImport(file, code, mappingCategory, expectedHeaders);
			//			}
			default -> {
				// Throw an error if a user tries to upload a file of unsupported category type.
				// Will currently never be seen in admin app as CirculationStatus is pre-set,
				// but could be seen if someone manually performed a POST request.
				throw new FileUploadValidationException("This mapping category is currently unsupported.");
			}
		}
	}

	// This method processes the uploaded file and builds the object to be returned.
	private Mono<UploadedConfigImport> referenceValueMappingImport(CompletedFileUpload file, String code, String mappingCategory, String[] expectedHeaders) {
		try {
			InputStreamReader reader = new InputStreamReader(file.getInputStream());
			// Parse the CSV data and convert it to a list of arrays
			List<String[]> csvData = parseCsv(reader, expectedHeaders);
			// Process each line and convert it to a Flux of Mono
				return Flux.fromIterable(csvData)
				.flatMap(line -> processReferenceValueMapping(line, code, mappingCategory))
				.collectList()
				.map(mappings -> UploadedConfigImport.builder()
					.message(mappings.size() + " mappings have been imported successfully.")
					.lastImported(Instant.now())
					.recordsImported(Long.valueOf(mappings.size()))
					.build());
		} catch (IOException e) {
			throw new FileUploadValidationException("Error reading file.");
		}
	}

	// Method for parsing the uploaded CSV/TSV file. Runs validation on the supplied data.
	public static List<String[]> parseCsv(Reader reader, String[] expectedHeaders) {
		// Make it skip the headers
		String validationError = "";
		try {
			CSVReader csvReader = new CSVReader(reader);
			// Get the header line so we can compare
			String[] headers = csvReader.readNext();
			// Check if the headers match the expectedHeaders
			if (!Arrays.equals(headers, expectedHeaders)) {
				validationError = "CSV headers do not match the expected headers: "+ Arrays.toString(expectedHeaders)+
					". Please check your CSV file and retry.";
				throw new FileUploadValidationException(validationError);
			}
			// Before skipping the headers, check that they do actually exist + are what we're expecting - pass expected headers
			csvReader.skip(1);
			// Now, validate that there are no empty entries for meaning or code
			String[] line;
			List<String[]> csvData = new ArrayList<>();
			int lineNumber = 2;
			while ((line = csvReader.readNext()) != null) {
				// Check that 'local code' and 'meaning' columns are not empty
				if (!line[0].isEmpty() && !line[1].isEmpty()) {
					csvData.add(line);
				} else {
					validationError = "Empty value in 'code' or 'meaning' column at line " + lineNumber;
					throw new FileUploadValidationException(validationError);
				}
				lineNumber++;
			}
			return csvData;
		} catch (Exception e) {
			log.debug("A FileValidationException has occurred. Details:"+validationError);
			throw new FileUploadValidationException(validationError);
		}
	}


	// This method override is used to build a CirculationStatusMapping
	private Mono<ReferenceValueMapping> processReferenceValueMapping(String[] rvm, String code, String mappingCategory) {
		// Used for circulation status mappings only at present.
		// For context see https://openlibraryfoundation.atlassian.net/browse/DCB-508
		String dcbCode = getDcbCode(rvm);
		ReferenceValueMapping rvmd= ReferenceValueMapping.builder()
			.id(UUIDUtils.dnsUUID(rvm[0]+":"+rvm[1]+":"+rvm[2]))
			.fromContext(code)
			.fromCategory(mappingCategory)
			.fromValue(rvm[0])
			.toContext("DCB")
			.toCategory("Host LMS")
			.toValue(dcbCode.toUpperCase())
			.label(rvm[1])
			.lastImported(Instant.now())
			.build();
		return Mono.from(referenceValueMappingRepository.saveOrUpdate(rvmd));
	}

	private static String getDcbCode(String[] rvm) {
		String dcbCode = "";
		if (Objects.equals(rvm[2], ""))
		{
			dcbCode = "Unknown";
		}
		else {
			dcbCode = rvm[2];
		}
		return dcbCode;
	}

	private Mono<ConfigImportResult> numericRangeImport(String url) {

		HttpRequest<?> request = HttpRequest.GET(url);

		return Mono.from(httpClient.exchange(request, String.class))
				.flatMapMany( this::extractData )
				.concatMap( nrmr -> {
					log.debug("Process range: {}",nrmr.toString());
					return processNumericRangeMapping(nrmr);
				})
				.collectList()
                                .map(recordList -> ConfigImportResult.builder()
                                        .message("OK")
                                        .recordsImported(Long.valueOf(recordList.size()))
                                        .build());
	}

	private Publisher<String[]> extractData(HttpResponse<String> httpResponse) {

		String s = httpResponse.body();
		Reader reader = new StringReader(s);
		CSVParser parser = new CSVParserBuilder().withSeparator('\t').build();
		CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).withCSVParser(parser).build();
		List<String[]> all_Rows = null;
                try { 
			all_Rows = csvReader.readAll();
			reader.close(); 
		} catch (Exception e) { }
                return Flux.fromIterable(all_Rows);
	}

	private Mono<ReferenceValueMapping> processReferenceValueMapping(String[] rvm) {
		ReferenceValueMapping rvmd= ReferenceValueMapping.builder()
			.id(UUIDUtils.dnsUUID(rvm[0]+":"+rvm[1]+":"+rvm[2]+":"+rvm[3]+":"+rvm[4]))
			.fromContext(rvm[0])
			.fromCategory(rvm[1])
			.fromValue(rvm[2])
			.toContext(rvm[3])
			.toCategory(rvm[4])
			.toValue(rvm[5])
			.build();
		log.debug("This line is: "+ rvmd);
		return Mono.from(referenceValueMappingRepository.saveOrUpdate(rvmd));
	}
	private Mono<NumericRangeMapping> processNumericRangeMapping(String[] nrmr) {

                NumericRangeMapping nrm = NumericRangeMapping.builder()
                        .id(UUIDUtils.dnsUUID(nrmr[0]+":"+nrmr[1]+":"+nrmr[2]+":"+nrmr[5]))
                        .context(nrmr[0])
                        .domain(nrmr[1])
                        .lowerBound(Long.valueOf(nrmr[2]))
                        .upperBound(Long.valueOf(nrmr[3]))
                        .targetContext(nrmr[5])
                        .mappedValue(nrmr[4])
                        .build();

		return Mono.from(numericRangeMappingRepository.saveOrUpdate(nrm));
	}

	@Data
	@Builder
	@Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
	@Introspected
	public static class ConfigImportResult {
		String message;
		Long recordsImported;
	}

	@Data
	@Builder
	@Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
	@Introspected
	public static class UploadedConfigImport {
			String message;
			Long recordsImported;
			Instant lastImported;
	}
}
