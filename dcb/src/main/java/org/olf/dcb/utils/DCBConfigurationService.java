package org.olf.dcb.utils;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Instant;

import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.transaction.Transactional;
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


import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

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

		log.info("importConfiguration({},{})",profile,url);

		return switch ( profile ) {
			case "numericRangeMappingImport" -> numericRangeImport(url);
			case "referenceValueMappingImport" -> referenceValueMappingImport(url);
			default -> Mono.just(ConfigImportResult.builder().message("ERROR").build());
		};
	}

	private Mono<ConfigImportResult> referenceValueMappingImport(String url) {
		HttpRequest<?> request = HttpRequest.GET(url);

		long start_time = System.currentTimeMillis();

		return Mono.from(httpClient.exchange(request, String.class))
			.flatMapMany( this::extractData )
			.concatMap( rvm -> {
				log.debug("Process ref value mapping from {}: {}",url,Arrays.toString(rvm));
				return processReferenceValueMapping(rvm);
			})
			.collectList()
			.map(recordList -> ConfigImportResult.builder()
				.message("OK")
				.recordsImported(Long.valueOf(recordList.size()))
				.elapsed(System.currentTimeMillis() - start_time)
				.build())
			.doOnNext( icr -> log.info("Import completed {}", icr) );
	}

	// Below are the override methods for uploaded mappings.
	// These methods take:
	// A 'mappingCategory' - for example 'ItemType' - this is supplied by the user from the admin app.
	// A Host LMS code - for example 'ARCHWAY' - also supplied by the user from DCB Admin
	// And a CompletedFileUpload for the CSV/TSV mappings file to be processed.
	public Mono<UploadedConfigImport> importConfiguration(String mappingCategory, String code, CompletedFileUpload file, String reason, String changeCategory, String changeReferenceUrl) {
		// This will support all mappings which get uploaded via a file, as opposed to being taken from a URL.
		log.debug("importConfiguration({},{})",mappingCategory,file.getFilename());
		String[] expectedHeaders;
		boolean noCode = code == null || code.isEmpty() || code.equals("undefined");

		switch (mappingCategory) {
			// Two main categories: ReferenceValueMappings and NumericRangeMappings.
			case "Reference value mappings" -> {
				if (noCode) {
					throw new FileUploadValidationException("You must provide a Host LMS code to import mappings. Please select a Host LMS in DCB Admin and retry.");
				}
				expectedHeaders = new String[]{"fromContext", "fromCategory", "fromValue", "toContext", "toCategory", "toValue"};
				return cleanupMappings(mappingCategory, code)
					.flatMap(cleanupResult ->
						referenceValueMappingImport(file, code, mappingCategory, expectedHeaders, cleanupResult, reason, changeCategory, changeReferenceUrl)
					);
			}
			// Numeric range mappings will be added subsequently in DCB-1153 and this code will be restored.
			case "Numeric range mappings" -> {
				if (noCode) {
					throw new FileUploadValidationException("You must provide a Host LMS code to import mappings. Please select a Host LMS in DCB Admin and retry.");
				}
				expectedHeaders = new String[]{"context", "domain", "lowerBound", "upperBound", "toValue", "toContext"};
				return cleanupMappings(mappingCategory, code)
					.flatMap(cleanupResult ->
						numericRangeImport(file, code, mappingCategory, expectedHeaders, cleanupResult)
					);
			}
				default -> {
				// Throw an error if a user tries to upload a file of unsupported category type.
				// Will currently never be seen in admin app as CirculationStatus is pre-set,
				// but could be seen if someone manually performed a POST request.
				throw new FileUploadValidationException("This mapping category is currently unsupported.");
			}
		}
	}

	// This method processes the uploaded file and builds the object to be returned.
	private Mono<UploadedConfigImport> referenceValueMappingImport(CompletedFileUpload file, String code, String mappingCategory,
																																 String[] expectedHeaders, Long cleanupResult, String reason,
																																 String changeCategory, String changeReferenceUrl) {
		try {
			InputStreamReader reader = new InputStreamReader(file.getInputStream());
			if (file.getFilename().contains(".tsv"))
			{
				List<String[]> tsvData = parseTsv(reader, expectedHeaders, mappingCategory, code);
					return Flux.fromIterable(tsvData)
						.concatMap(rvm -> processReferenceValueMapping(rvm, reason, changeCategory, changeReferenceUrl)) // Pass user supplied values here
						.collectList()
						.map(mappings -> UploadedConfigImport.builder()
							.message(mappings.size() + " mappings have been imported successfully.")
							.lastImported(Instant.now())
							.recordsImported((long) mappings.size())
							.recordsDeleted(cleanupResult)
							.build());
			}
			else {
				List<String[]> csvData = parseCsv(reader, expectedHeaders, code);
				{
					return Flux.fromIterable(csvData)
						.concatMap(rvm -> processReferenceValueMapping(rvm, reason, changeCategory, changeReferenceUrl)) // Pass reason here
						.collectList()
						.map(mappings -> UploadedConfigImport.builder()
							.message(mappings.size() + " mappings have been imported successfully.")
							.lastImported(Instant.now())
							.recordsImported((long) mappings.size())
							.recordsDeleted(cleanupResult)
							.build());
				}
			}
		} catch (IOException e) {
			throw new FileUploadValidationException("Error reading file.");
		}
	}

	private Mono<UploadedConfigImport> numericRangeImport(CompletedFileUpload file, String code, String mappingCategory, String[] expectedHeaders, Long cleanupResult) {
		try {
			InputStreamReader reader = new InputStreamReader(file.getInputStream());
			if (file.getFilename().contains(".tsv"))
			{
				List<String[]> tsvData = parseTsv(reader, expectedHeaders, mappingCategory, code);
				return Flux.fromIterable(tsvData)
					.concatMap(this::processNumericRangeMapping)
					.collectList()
					.map(mappings -> UploadedConfigImport.builder()
						.message(mappings.size() + " mappings have been imported successfully.")
						.lastImported(Instant.now())
						.recordsImported((long) mappings.size())
						.recordsDeleted(cleanupResult)
						.build());
			}
			else {
				List<String[]> csvData = parseCsv(reader, expectedHeaders, code);
				{
					return Flux.fromIterable(csvData)
						.concatMap(this::processNumericRangeMapping)
						.collectList()
						.map(mappings -> UploadedConfigImport.builder()
							.message(mappings.size() + " mappings have been imported successfully.")
							.lastImported(Instant.now())
							.recordsImported((long) mappings.size())
							.recordsDeleted(cleanupResult)
							.build());
				}
			}
		} catch (IOException e) {
			throw new FileUploadValidationException("Error reading file.");
		}
	}

	// Method for parsing the uploaded CSV/TSV file. Runs validation on the supplied data.
	public static List<String[]> parseCsv(Reader reader, String[] expectedHeaders, String code) {
		String validationError = "";
		try {
			CSVReader csvReader = new CSVReader(reader);
			// Get the header line so we can compare
			String[] headers = csvReader.readNext();
			// Check if the headers match the expectedHeaders
			if (((!headers[0].equalsIgnoreCase(expectedHeaders[0])) || (!headers[1].equalsIgnoreCase(expectedHeaders[1])) || (!headers[2].equalsIgnoreCase(expectedHeaders[2])) || (!headers[3].equalsIgnoreCase(expectedHeaders[3])) || (!headers[4].equalsIgnoreCase(expectedHeaders[4])) || (!headers[5].equalsIgnoreCase(expectedHeaders[5]))))
			{
				validationError = "CSV headers do not match the expected headers: "+ Arrays.toString(expectedHeaders)+
					". Please check your CSV file and retry.";
				throw new FileUploadValidationException(validationError);
			}
			String[] line;
			List<String[]> csvData = new ArrayList<>();
			int lineNumber = 2;
			while ((line = csvReader.readNext()) != null) {
				// Make sure that the contexts match what's expected, and that there isn't a clash between them and what the user has supplied.
				if ((line[0].equalsIgnoreCase("DCB") || line[0].equalsIgnoreCase(code)) && ((line[3].equalsIgnoreCase("DCB")) || line[3].equalsIgnoreCase(code)))
				{
					csvData.add(line);
				}
				else {
					validationError="Either the fromContext or toContext values in your .csv file do not match the Host LMS code you supplied. Please check your file and try again.";
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

	public static List<String[]> parseTsv(Reader reader, String[] expectedHeaders, String mappingCategory, String code) {
		String validationError = "";
		log.debug("Parsing for"+ mappingCategory, code);
		try {
			CSVReader TSVReader = new CSVReaderBuilder(reader)
				.withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
				.build();
			// Get the header line so we can compare
			String[] headers = TSVReader.readNext();
			// Check if the headers match the expectedHeaders
			if (((!headers[0].equalsIgnoreCase(expectedHeaders[0])) || (!headers[1].equalsIgnoreCase(expectedHeaders[1])) || (!headers[2].equalsIgnoreCase(expectedHeaders[2])) || (!headers[3].equalsIgnoreCase(expectedHeaders[3])) || (!headers[4].equalsIgnoreCase(expectedHeaders[4])) || (!headers[5].equalsIgnoreCase(expectedHeaders[5]))))
			{
				validationError = "TSV headers do not match the expected headers: "+ Arrays.toString(expectedHeaders)+
					". Please check your TSV file and retry.";
				throw new FileUploadValidationException(validationError);
			}
			// Validate NRMs also
			String[] line;
			List<String[]> tsvData = new ArrayList<>();
			int lineNumber = 2;
			while ((line = TSVReader.readNext()) != null) {
					// Line by line validation goes here
					// Validate that the contexts match what's expected, and that there isn't a clash between them and what the user has supplied.
				if (mappingCategory.equalsIgnoreCase("Numeric range mappings")) {
					if (line[0].equals(code))
					{
						tsvData.add(line);
					}
					else {
						validationError="The context does not match the Host LMS code you supplied. Please check your file and try again.";
						throw new FileUploadValidationException(validationError);
					}
					lineNumber++;
				}
				else
				{
					if ((line[0].equalsIgnoreCase("DCB") || line[0].equalsIgnoreCase(code)) && ((line[3].equalsIgnoreCase("DCB")) || line[3].equalsIgnoreCase(code)))
					{
						tsvData.add(line);
					}
					else {
						validationError="Either the fromContext or toContext values in your file do not match the Host LMS code you supplied. Please check your file and try again.";
						throw new FileUploadValidationException(validationError);
					}
					lineNumber++;
				}
			}
			return tsvData;
		} catch (Exception e) {
			log.debug("A FileValidationException has occurred. Details:"+validationError);
			throw new FileUploadValidationException(validationError);
		}
	}

	@Transactional
	protected Mono<Long> cleanupMappings(String category, String context) {
		// This method marks any existing mappings for the given category and context as deleted.
		switch (category) {
			// Mark all existing reference value mappings for a Host LMS as deleted.
			case "Reference value mappings":
				return Mono.from(referenceValueMappingRepository.markAsDeleted(context));
			case "Numeric range mappings":
				return Mono.from(numericRangeMappingRepository.markAsDeleted(context));
			default:
				return Mono.from(referenceValueMappingRepository.markAsDeleted(context));
		}
	}

	private Mono<ConfigImportResult> numericRangeImport(String url) {

		HttpRequest<?> request = HttpRequest.GET(url);

		long start_time = System.currentTimeMillis();

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
					.elapsed(System.currentTimeMillis() - start_time)
					.build())
				.doOnNext( cir -> log.info("Completed numeric range mapping import {}",cir));
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
		} catch (Exception e) { 
			log.warn("Error reading stream",e);
		}
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
			.lastImported(Instant.now())
			.deleted(false)
			.build();

		// If there was an optional label, set it
		if ( rvm.length > 6 )
			rvmd.setLabel(rvm[6]);

		return Mono.from(referenceValueMappingRepository.saveOrUpdate(rvmd));
	}

	private Mono<ReferenceValueMapping> processReferenceValueMapping(String[] rvm, String reason, String changeCategory, String changeReferenceUrl) {
		ReferenceValueMapping rvmd= ReferenceValueMapping.builder()
			.id(UUIDUtils.dnsUUID(rvm[0]+":"+rvm[1]+":"+rvm[2]+":"+rvm[3]+":"+rvm[4]))
			.fromContext(rvm[0])
			.fromCategory(rvm[1])
			.fromValue(rvm[2])
			.toContext(rvm[3])
			.toCategory(rvm[4])
			.toValue(rvm[5])
			.lastImported(Instant.now())
			.deleted(false)
			.reason(reason)
			.changeCategory(changeCategory)
			.changeReferenceUrl(changeReferenceUrl)
			.build();

		// If there was an optional label, set it
		if ( rvm.length > 6 )
			rvmd.setLabel(rvm[6]);

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
												.lastImported(Instant.now())
												.deleted(false)
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
		Long elapsed;
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
			Long recordsDeleted;
	}
}
