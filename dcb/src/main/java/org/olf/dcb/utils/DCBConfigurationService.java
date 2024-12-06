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

	/**
	 * The importConfiguration method called from the UploadMappingsController. Takes a file of uploaded mappings for import.
	 * The parameters reason, changeCategory, and changeReferenceUrl are supplied by the user for the purposes of the data change log.
	 *
	 * @param mappingType The type of mapping - reference value or numeric range.
	 * @param mappingCategory The category of mapping - ItemType, patronType or Location.
	 * @param code The Host LMS code - corresponds to context or domain.
	 * @param file The mappings file (.tsv or .csv format).
	 */
	public Mono<UploadedConfigImport> importConfiguration(String mappingType, String mappingCategory, String code, CompletedFileUpload file, String reason, String changeCategory, String changeReferenceUrl) {
		log.debug("importConfiguration({}, {}, {})", mappingType, mappingCategory, file.getFilename());
		if (code == null || code.isEmpty() || code.equals("undefined")) {
			return Mono.error(new FileUploadValidationException("You must provide a Host LMS code to import mappings. Please select a Host LMS in DCB Admin and retry."));
		}
		return Mono.defer(() -> {
			try {
				ParseResult parseResult = parseFile(file, mappingType, mappingCategory, code);
				List <String[]> data = parseResult.getParsedData();
				List <IgnoredMapping> ignoredMappings = parseResult.getIgnoredMappings();
				return processImport(mappingType, mappingCategory, code, data, ignoredMappings, reason, changeCategory, changeReferenceUrl).doOnNext( uci -> log.info("The import of uploaded mappings has completed. Message: {}, records imported: {}, records marked as deleted: {}, records ignored: {}", uci.getMessage(), uci.getRecordsImported(), uci.getRecordsDeleted(), uci.ignoredMappings.size()) );
			} catch (FileUploadValidationException e) {
				return Mono.error(e);
			} catch (IOException e) {
				return Mono.error(new FileUploadValidationException("Error reading file: " + e.getMessage()));
			}
		});
	}

	// Methods for determining which file parsing method is required, and getting the correct expected headers for validation.
	private ParseResult parseFile(CompletedFileUpload file, String mappingType, String mappingCategory, String code) throws IOException, FileUploadValidationException {
		InputStreamReader reader = new InputStreamReader(file.getInputStream());
		String[] expectedHeaders = getExpectedHeaders(mappingType);

		if (file.getFilename().endsWith(".tsv")) {
			return parseTsv(reader, expectedHeaders, mappingCategory, mappingType, code);
		} else {
			return parseCsv(reader, expectedHeaders, code, mappingCategory, mappingType);
		}
	}

	private String[] getExpectedHeaders(String mappingType) {
		return switch (mappingType) {
			case "Reference value mappings" -> new String[]{"fromContext", "fromCategory", "fromValue", "toContext", "toCategory", "toValue"};
			case "Numeric range mappings" -> new String[]{"context", "domain", "lowerBound", "upperBound", "toValue", "toContext"};
			default -> throw new IllegalArgumentException("Unsupported mapping type: " + mappingType);
		};
	}

// Methods for processing the import of reference value mappings OR numeric range mappings.
	private Mono<UploadedConfigImport> processImport(String mappingType, String mappingCategory, String code, List<String[]> data, List <IgnoredMapping> ignoredMappings, String reason, String changeCategory, String changeReferenceUrl) {
		return cleanupMappings(mappingType, mappingCategory, code)
			.flatMap(cleanupResult -> {
				if ("Reference value mappings".equals(mappingType)) {
					return processReferenceValueMappings(data, cleanupResult, ignoredMappings, reason, changeCategory, changeReferenceUrl);
				} else {
					return processNumericRangeMappings(data, cleanupResult, ignoredMappings, reason, changeCategory, changeReferenceUrl);
				}
			});
	}

	private Mono<UploadedConfigImport> processReferenceValueMappings(List<String[]> data, Long cleanupResult, List <IgnoredMapping> ignoredMappings, String reason, String changeCategory, String changeReferenceUrl) {
		return Flux.fromIterable(data)
			.concatMap(rvm -> processReferenceValueMapping(rvm, reason, changeCategory, changeReferenceUrl))
			.collectList()
			.map(mappings -> UploadedConfigImport.builder()
				.message(mappings.size() + " mappings have been imported successfully.")
				.lastImported(Instant.now())
				.recordsImported((long) mappings.size())
				.recordsDeleted(cleanupResult)
				.recordsIgnored(ignoredMappings.size())
				.ignoredMappings(ignoredMappings)
				.build());
	}

	private Mono<UploadedConfigImport> processNumericRangeMappings(List<String[]> data, Long cleanupResult,List <IgnoredMapping> ignoredMappings, String reason, String changeCategory, String changeReferenceUrl) {
		return Flux.fromIterable(data)
			.concatMap(nrm -> processNumericRangeMapping(nrm, reason, changeCategory, changeReferenceUrl))
			.collectList()
			.map(mappings -> UploadedConfigImport.builder()
				.message(mappings.size() + " mappings have been imported successfully.")
				.lastImported(Instant.now())
				.recordsImported((long) mappings.size())
				.recordsDeleted(cleanupResult)
				.recordsIgnored(ignoredMappings.size())
				.ignoredMappings(ignoredMappings)
				.build());
	}

	// Methods for parsing the uploaded CSV/TSV file and running header and line-by-line validation.
	public static ParseResult parseCsv(Reader reader, String[] expectedHeaders, String code, String mappingCategory, String mappingType) {
		String validationError = "";
		try (CSVReader csvReader = new CSVReader(reader)) {
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
			List<IgnoredMapping> ignoredMappings = new ArrayList<>();
			int lineNumber = 2;
			while ((line = csvReader.readNext()) != null) {
				if (line[0].isBlank() || line[1].isBlank() || line[2].isBlank() || line[3].isBlank() || line[4].isBlank() || line[5].isBlank())
				{
					validationError = "A mandatory field on line "+lineNumber+ " has been left empty. Please check your file and try again.";
					throw new FileUploadValidationException(validationError);
				}
				// Line by line validation for numeric range mappings
				// Validate that the context and domain match what's expected, and that there isn't a clash between them and what the user has supplied.
				if (mappingType.equalsIgnoreCase("Numeric range mappings")) {
					if (!line[0].equals(code))
					{
						validationError="The context does not match the Host LMS code you supplied. Please check your file and try again.";
						throw new FileUploadValidationException(validationError);
					}
					else if (!Objects.equals(mappingCategory, "all") && !line[1].equals(mappingCategory))
					{
						// If there is a mis-match between category and domain, treat this mapping as invalid, add it to the ignored list, and proceed to the next one.
						// If category is "all", we don't need to run this validation.
						validationError="The category of this mapping does not match the domain in the file you have supplied and it will not be imported.";
						IgnoredMapping ignoredMapping = new IgnoredMapping(validationError, lineNumber);
						ignoredMappings.add(ignoredMapping);
					}
					else {
						csvData.add(line);
					}
					lineNumber++;
				}
				else
				// Line by line validation for reference value mappings
				// Validate that the contexts and categories match what's expected, and that there isn't a clash between them and what the user has supplied.
				{
					// If the fromContext or toContext are invalid (not matching either DCB or the supplied Host LMS code), we will throw an error
					if(!((line[0].equalsIgnoreCase("DCB") && line[3].equalsIgnoreCase(code)) || (line[0].equalsIgnoreCase(code) && line[3].equalsIgnoreCase("DCB"))))
					{
						validationError="Either the fromContext or toContext values in your file do not match the Host LMS code you supplied. Please check your file and try again.";
						// We do throw an exception here, because if the context doesn't match the code something has gone badly wrong.
						throw new FileUploadValidationException(validationError);
					}
					// If category is specified as all, we do not need to run this validation
					else if (!Objects.equals(mappingCategory, "all") && !(line[1].equals(mappingCategory )|| line[4].equals(mappingCategory)))
					{
						// If there is a mis-match between the supplied category and the one in the file, treat this mapping as invalid, add it to the ignored list, and proceed to the next one.
						// This means that if a user selects "ItemType" and supplies all their mappings, DCB adds only the ItemType mappings
						// Which saves them the job of having to pick them out themselves.
						validationError="The fromCategory or toCategory of the mapping on line "+lineNumber+" of your file does not match the category "+mappingCategory+" you have specified. it will not be imported.";
						IgnoredMapping ignoredMapping = new IgnoredMapping(validationError, lineNumber);
						ignoredMappings.add(ignoredMapping);
					}
					else {
						csvData.add(line);
					}
					lineNumber++;
				}
			}
			return ParseResult.builder().ignoredMappings(ignoredMappings).parsedData(csvData).build();
		} catch (Exception e) {
			log.debug("A FileValidationException has occurred. Details:"+validationError);
			throw new FileUploadValidationException(validationError);
		}
	}

	public static ParseResult parseTsv(Reader reader, String[] expectedHeaders, String mappingCategory, String mappingType, String code) {
		String validationError = "";
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
			String[] line;
			List<String[]> tsvData = new ArrayList<>();
			List<IgnoredMapping> ignoredMappings = new ArrayList<>();
			int lineNumber = 2;
			while ((line = TSVReader.readNext()) != null) {
				if (line[0].isBlank() || line[1].isBlank() || line[2].isBlank() || line[3].isBlank() || line[4].isBlank() || line[5].isBlank())
				{
					validationError = "A mandatory field on line "+lineNumber+ " has been left empty. Please check your file and try again.";
					throw new FileUploadValidationException(validationError);
				}
					// Line by line validation for numeric range mappings
					// Validate that the context and domain match what's expected, and that there isn't a clash between them and what the user has supplied.
				if (mappingType.equalsIgnoreCase("Numeric range mappings")) {
					if (!line[0].equals(code))
					{
						validationError="The context does not match the Host LMS code you supplied. Please check your file and try again.";
						throw new FileUploadValidationException(validationError);
					}
					else if (!Objects.equals(mappingCategory, "all") && !line[1].equals(mappingCategory))
					{
						validationError="The category of this mapping does not match the domain in the file you have supplied and it will not be imported.";
						IgnoredMapping ignoredMapping = new IgnoredMapping(validationError, lineNumber);
						ignoredMappings.add(ignoredMapping);
					}
					else {
						tsvData.add(line);
					}
					lineNumber++;
				}
				else
				// Line by line validation for reference value mappings
				// Validate that the contexts and categories match what's expected, and that there isn't a clash between them and what the user has supplied.
				{
					// If the fromContext or toContext are invalid (not matching either DCB or the supplied Host LMS code), we will throw an error
					if(!((line[0].equalsIgnoreCase("DCB") && line[3].equalsIgnoreCase(code)) || (line[0].equalsIgnoreCase(code) && line[3].equalsIgnoreCase("DCB"))))
					{
						validationError="Either the fromContext or toContext values in your file do not match the Host LMS code you supplied. Please check your file and try again.";
						throw new FileUploadValidationException(validationError);
					}
					// If the category supplied does not match the fromCategory or toCategory, throw a validation error.
					// If category is specified as all, we do not need to run this validation
					else if (!Objects.equals(mappingCategory, "all") && !(line[1].equals(mappingCategory )|| line[4].equals(mappingCategory)))
					{
						validationError="The fromCategory or toCategory of the mapping on line "+lineNumber+" of your file does not match the category "+mappingCategory+" you have specified. it will not be imported.";
						IgnoredMapping ignoredMapping = new IgnoredMapping(validationError, lineNumber);
						ignoredMappings.add(ignoredMapping);
					}
					else {
						tsvData.add(line);
					}
					lineNumber++;
				}
			}
			return ParseResult.builder().ignoredMappings(ignoredMappings).parsedData(tsvData).build();
		} catch (Exception e) {
			log.debug("A FileValidationException has occurred. Details:"+validationError);
			throw new FileUploadValidationException(validationError);
		}
	}

	@Transactional
	protected Mono<Long> cleanupMappings(String mappingType, String mappingCategory, String context) {
		// This method marks any existing mappings for the given category and context as deleted.
		if (mappingCategory.equals("all")) {
			// If "all" selected, we can mark as deleted based on context only.
			if (mappingType.equals("Reference value mappings")) {
				return Mono.from(referenceValueMappingRepository.markAsDeleted(context)).doOnNext(affectedRows -> log.info("Marked {} rows as deleted", affectedRows));
			} else {
				return Mono.from(numericRangeMappingRepository.markAsDeleted(context)).doOnNext(affectedRows -> log.info("Marked {} rows as deleted", affectedRows));
			}
		} else {
			// Otherwise there has been a specific category selected and we need to take this into account
			if (mappingType.equals("Reference value mappings")) {
				return Mono.from(referenceValueMappingRepository.markAsDeleted(context, mappingCategory)).doOnNext(affectedRows -> log.info("Marked {} rows as deleted", affectedRows));
			} else {
				return Mono.from(numericRangeMappingRepository.markAsDeleted(context, mappingCategory)).doOnNext(affectedRows -> log.info("Marked {} rows as deleted", affectedRows));
			}
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

	private Mono<NumericRangeMapping> processNumericRangeMapping(String[] nrmr, String reason, String changeCategory, String changeReferenceUrl) {
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
			.reason(reason)
			.changeCategory(changeCategory)
			.changeReferenceUrl(changeReferenceUrl)
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
			Integer recordsIgnored;
			@Serdeable.Serializable
			List<IgnoredMapping> ignoredMappings;
	}

	@Data
	@Builder
	@Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
	@Introspected
	public static class ParseResult {
		List<String[]> parsedData;
		List<IgnoredMapping> ignoredMappings;
	}
	@Data
	@Builder
	@Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
	@Introspected
	public static class IgnoredMapping {
		String reason;
		Integer lineNumber;
	}
}
