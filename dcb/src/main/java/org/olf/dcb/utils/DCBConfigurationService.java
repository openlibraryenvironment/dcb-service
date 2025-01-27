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

import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.api.exceptions.FileUploadValidationException;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.graphql.validation.LocationInputValidator;
import org.olf.dcb.storage.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Singleton;


import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;


import java.util.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.core.annotation.Introspected;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.core.model.ReferenceValueMapping;
import services.k_int.utils.UUIDUtils;

@Singleton
public class DCBConfigurationService {

	private HttpClient httpClient;
        private final NumericRangeMappingRepository numericRangeMappingRepository;
        private final ReferenceValueMappingRepository referenceValueMappingRepository;

				private final LocationRepository locationRepository;
				// This will be for save/lookup
				private final HostLmsRepository hostLmsRepository;

				private final AgencyRepository agencyRepository;

	public DCBConfigurationService(
		HttpClient httpClient,
		ReferenceValueMappingRepository referenceValueMappingRepository,
		NumericRangeMappingRepository numericRangeMappingRepository,
		LocationRepository locationRepository, HostLmsRepository hostLmsRepository,
		AgencyRepository agencyRepository) {
		this.httpClient = httpClient;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
		this.locationRepository = locationRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.agencyRepository = agencyRepository;
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
	 * @param type The type of configuration - reference value or numeric range mappings, or a Location.
	 * @param mappingCategory The category of mapping - ItemType, patronType or Location.
	 * @param code The Host LMS code - corresponds to context or domain.
	 * @param file The mappings file (.tsv or .csv format).
	 */

	// This can become just type instead.
//	public Mono<UploadedConfigImport> importConfiguration(String type, String mappingCategory, String code, CompletedFileUpload file, String reason, String changeCategory, String changeReferenceUrl) {
//		log.debug("importConfiguration({}, {}, {})", type, mappingCategory, file.getFilename());
//		if (code == null || code.isEmpty() || code.equals("undefined")) {
//			return Mono.error(new FileUploadValidationException("You must provide a Host LMS code to import mappings or locations. Please select a Host LMS in DCB Admin and retry."));
//		}
//		return Mono.defer(() -> {
//			try {
//				ParseResult parseResult = parseFile(file, type, mappingCategory, code);
//				List <String[]> data = parseResult.getParsedData();
//				List <IgnoredMapping> ignoredMappings = parseResult.getIgnoredMappings();
//				return processImport(type, mappingCategory, code, data, ignoredMappings, reason, changeCategory, changeReferenceUrl).doOnNext( uci -> log.info("The import of uploaded mappings has completed. Message: {}, records imported: {}, records marked as deleted: {}, records ignored: {}", uci.getMessage(), uci.getRecordsImported(), uci.getRecordsDeleted(), uci.ignoredMappings.size()) );
//			} catch (FileUploadValidationException e) {
//				return Mono.error(e);
//			} catch (IOException e) {
//				return Mono.error(new FileUploadValidationException("Error reading file: " + e.getMessage()));
//			}
//		});
//	}

	public Mono<UploadedConfigImport> importConfiguration(String type, String mappingCategory, String code, CompletedFileUpload file, String reason, String changeCategory, String changeReferenceUrl) {
		log.debug("importConfiguration({}, {}, {})", type, mappingCategory, file.getFilename());

		if (code == null || code.isEmpty() || code.equals("undefined")) {
			return Mono.error(new FileUploadValidationException(
				"You must provide a Host LMS code to import mappings or locations. Please select a Host LMS in DCB Admin and retry."));
		}

		return Mono.fromCallable(() -> new InputStreamReader(file.getInputStream()))
			.flatMap(reader -> {
				String[] expectedHeaders = getExpectedHeaders(type);
				if (file.getFilename().endsWith(".tsv"))
					{
						return parseFile(reader, expectedHeaders, code, mappingCategory, type, "tsv");

					}
				else
				{
					return parseFile(reader, expectedHeaders, code, mappingCategory, type, "csv");

				}
			})
			.flatMap(parseResult -> processImport(
				type,
				mappingCategory,
				code,
				parseResult.getParsedData(),
				parseResult.getIgnoredMappings(),
				reason,
				changeCategory,
				changeReferenceUrl
			))
			.doOnNext(uci -> log.info(
				"The import of uploaded mappings has completed. Message: {}, records imported: {}, records marked as deleted: {}, records ignored: {}",
				uci.getMessage(),
				uci.getRecordsImported(),
				uci.getRecordsDeleted(),
				uci.getIgnoredMappings().size()
			));
	}




		// Methods for determining which file parsing method is required, and getting the correct expected headers for validation.
	private ParseResult parseFile(CompletedFileUpload file, String type, String mappingCategory, String code) throws IOException, FileUploadValidationException {
		InputStreamReader reader = new InputStreamReader(file.getInputStream());
		String[] expectedHeaders = getExpectedHeaders(type);

		if (file.getFilename().endsWith(".tsv")) {
			return parseTsv(reader, expectedHeaders, mappingCategory, type, code);
		} else {
			return parseCsv(reader, expectedHeaders, code, mappingCategory, type);
		}
	}

	private String[] getExpectedHeaders(String type) {
		return switch (type) {
			case "Reference value mappings" -> new String[]{"fromContext", "fromCategory", "fromValue", "toContext", "toCategory", "toValue"};
			case "Numeric range mappings" -> new String[]{"context", "domain", "lowerBound", "upperBound", "toValue", "toContext"};
			case "Locations" -> new String[]{"Agency Code", "Location Code", "Display Name", "Print Name", "DeliveryStop_Ignore", "Lat", "Lon", "isPickup", "LOCTYPE", "CHK_Ignore", "Address_Ignore", "id"};
			default -> throw new IllegalArgumentException("Unsupported config type: " + type);
		};
	}

// Methods for processing the import of reference value mappings OR numeric range mappings.
	private Mono<UploadedConfigImport> processImport(String type, String mappingCategory, String code, List<String[]> data, List <IgnoredMapping> ignoredMappings, String reason, String changeCategory, String changeReferenceUrl) {
		if (type.equals("Locations")) {
			return Mono.from(hostLmsRepository.findByCode(code))
				.switchIfEmpty(Mono.error(new FileUploadValidationException("Invalid Host LMS code provided")))
				.flatMap(hostLms ->
					Flux.fromIterable(data)
						.concatMap(line -> processLocationImport(line, hostLms, reason, changeCategory, changeReferenceUrl))
						.flatMap(location -> Mono.from(locationRepository.saveOrUpdate(location)))
						.collectList()
						.map(locations -> UploadedConfigImport.builder()
							.message(locations.size() + " locations have been imported successfully.")
							.lastImported(Instant.now())
							.recordsImported((long) locations.size())
							.recordsIgnored(ignoredMappings.size())
							.ignoredMappings(ignoredMappings)
							.build())
				);
		}
		else
		{
			return cleanupMappings(type, mappingCategory, code)
				.flatMap(cleanupResult -> {
					if ("Reference value mappings".equals(type)) {
						return processReferenceValueMappings(data, cleanupResult, ignoredMappings, reason, changeCategory, changeReferenceUrl);
					} else {
						return processNumericRangeMappings(data, cleanupResult, ignoredMappings, reason, changeCategory, changeReferenceUrl);
					}
				});
		}
	}
	private Mono<Location> processLocationImport(String[] line, DataHostLms hostLms, String reason, String changeCategory, String changeReferenceUrl) {
		return Mono.from(agencyRepository.findOneByCode(line[0])).switchIfEmpty(Mono.error(new FileUploadValidationException("Agency not found for code: " + line[0])))
			.map(agency -> Location.builder()
				.id(UUIDUtils.generateLocationId(line[0], line[1]))
				.code(line[1])
				.name(line[2])
				.printLabel(line[3])
				.deliveryStops(line[4])
				.latitude(Double.valueOf(line[5]))
				.longitude(Double.valueOf(line[6]))
				.isPickup(Boolean.valueOf(line[7]))
				.type(line[8])
				.localId(line[11])
				.hostSystem(hostLms)
				.agency(agency)
				.reason(reason)
				.changeCategory(changeCategory)
				.changeReferenceUrl(changeReferenceUrl)
				.build());
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

//	private static boolean validateLocationLine(String[] line, String hostLmsCode) {
//		// Aside from main validations, this also needs to affirm that the host lms and agency codes are actually valid.
//		// This is easy enough for the host lms code as we just need to check the supplied code for validity, and then check all others against it
//		// as they should all be the same
//		// Ditto for agency code -
//		// So this might have to pass in a Host LMS and an Agency
//		Map<String, Object> input = Map.of(
//			"agencyCode", line[0], // Also used for lookup
//			"code", line[1],
//			"name", line[2],
//			"printLabel", line[3],
//			"latitude", line[4],
//			"longitude", line[5],
//			"isPickup", Boolean.valueOf(line[6]),
//			"type", line[7],
//			"localId", line[8],
//			"hostLmsCode", line[9] // To be used for lookup
//		);
//
//	}

	private Mono<Boolean> validateLocationLine(String[] line, DataHostLms hostLms) {
		// Convert the line array to the input map format expected by LocationInputValidator
		Map<String, Object> input = Map.of(
			"agencyCode", line[0], // Also used for lookup
			"code", line[1],
			"name", line[2],
			"printLabel", line[3],
			"latitude", line[4],
			"longitude", line[5],
			"isPickup", Boolean.valueOf(line[6]),
			"type", line[7],
			"localId", line[8],
			"hostLmsCode", line[9] // To be used for lookup
		);

		return LocationInputValidator.validateInput(input, hostLms)
			.thenReturn(true)
			.onErrorResume(e -> {
				if (e instanceof EntityCreationException) {
					return Mono.just(false);
				}
				return Mono.error(e);
			});
	}

	private Mono<ParseResult> processLocationFileImport(List<String[]> data, String hostLmsCode) {
		return Mono.from(hostLmsRepository.findByCode(hostLmsCode))
			.switchIfEmpty(Mono.error(new FileUploadValidationException("Invalid Host LMS code provided")))
			.flatMap(hostLms ->
				Flux.fromIterable(data)
					.index() // Keep track of line number
					.concatMap(tuple -> {
						String[] line = tuple.getT2();
						long lineNumber = tuple.getT1() + 2; // Add 2 to account for header and 0-based index
						log.debug(Arrays.toString(line));

						Map<String, Object> input = Map.ofEntries(
							Map.entry("agencyCode", line[0]), // Also used for lookup
							Map.entry("code", line[1]),
							Map.entry("name", line[2]),
							Map.entry("printLabel", line[3]),
							Map.entry("deliveryStops", line[4]),
							Map.entry("latitude", line[5]),
							Map.entry("longitude", line[6]),
							Map.entry("isPickup", Boolean.valueOf(line[7])),
							Map.entry("type", line[8]),
							Map.entry("localId", line[11]),
							Map.entry("hostLmsCode", hostLmsCode)
						);

						return LocationInputValidator.validateInput(input, hostLms)
							.thenReturn(new ValidationResult(line, null))
							.onErrorResume(e -> {
								String errorMsg = e instanceof EntityCreationException ?
									e.getMessage() :
									"Invalid location data on line " + lineNumber;
								return Mono.just(new ValidationResult(
									null,
									new IgnoredMapping(errorMsg, (int) lineNumber)
								));
							});
					})
					.collectList()
					.map(results -> {
						List<String[]> validData = new ArrayList<>();
						List<IgnoredMapping> ignoredMappings = new ArrayList<>();

						for (ValidationResult result : results) {
							if (result.validLine != null) {
								validData.add(result.validLine);
							}
							if (result.ignoredMapping != null) {
								ignoredMappings.add(result.ignoredMapping);
							}
						}

						return ParseResult.builder()
							.parsedData(validData)
							.ignoredMappings(ignoredMappings)
							.build();
					})
			);
	}

	// Helper class for validation results
	private static class ValidationResult {
		final String[] validLine;
		final IgnoredMapping ignoredMapping;

		ValidationResult(String[] validLine, IgnoredMapping ignoredMapping) {
			this.validLine = validLine;
			this.ignoredMapping = ignoredMapping;
		}
	}

	public Mono<ParseResult> parseFile(Reader reader, String[] expectedHeaders, String code, String mappingCategory, String type, String fileType) {
		return Mono.fromCallable(() -> {
			if (fileType.equals("csv"))
			{
				String validationError = "";
				try (CSVReader csvReader = new CSVReader(reader)) {
					// Get the header line
					String[] headers = csvReader.readNext();

					// Validate headers
					if (((!headers[0].equalsIgnoreCase(expectedHeaders[0])) ||
						(!headers[1].equalsIgnoreCase(expectedHeaders[1])) ||
						(!headers[2].equalsIgnoreCase(expectedHeaders[2])) ||
						(!headers[3].equalsIgnoreCase(expectedHeaders[3])) ||
						(!headers[4].equalsIgnoreCase(expectedHeaders[4])) ||
						(!headers[5].equalsIgnoreCase(expectedHeaders[5])))) {
						throw new FileUploadValidationException("CSV headers do not match the expected headers: " +
							Arrays.toString(expectedHeaders) + ". Please check your CSV file and retry.");
					}

					// Read all data
					List<String[]> csvData = new ArrayList<>();
					String[] line;
					int lineNumber = 2;
					while ((line = csvReader.readNext()) != null) {
						if (Arrays.stream(line).anyMatch(String::isBlank)) {
							throw new FileUploadValidationException("A mandatory field on line " +
								lineNumber + " has been left empty. Please check your file and try again.");
						}
						csvData.add(line);
						lineNumber++;
					}
					return csvData;
				}

			}
			else
			{
				String validationError = "";
				try (CSVReader TSVReader = new CSVReaderBuilder(reader)
					.withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
					.build();) {
					// Get the header line so we can compare
					String[] headers = TSVReader.readNext();
					// Check if the headers match the expectedHeaders
					if (((!headers[0].equalsIgnoreCase(expectedHeaders[0])) || (!headers[1].equalsIgnoreCase(expectedHeaders[1])) || (!headers[2].equalsIgnoreCase(expectedHeaders[2])) || (!headers[3].equalsIgnoreCase(expectedHeaders[3])) || (!headers[4].equalsIgnoreCase(expectedHeaders[4])) || (!headers[5].equalsIgnoreCase(expectedHeaders[5])))) {
						validationError = "TSV headers do not match the expected headers: " + Arrays.toString(expectedHeaders) +
							". Please check your TSV file and retry.";
						throw new FileUploadValidationException(validationError);
					}
					List<String[]> csvData = new ArrayList<>();
					String[] line;
					int lineNumber = 2;
					while ((line = TSVReader.readNext()) != null) {
						// Make this more specific
						if (Arrays.stream(line).anyMatch(String::isBlank)) {
							throw new FileUploadValidationException("A mandatory field on line " +
								lineNumber + " has been left empty. Please check your file and try again.");
						}
						csvData.add(line);
						lineNumber++;
					}
					return csvData;
				}
			}


			})
			.flatMap(csvData -> {
				if (type.equals("Locations")) {
					return processLocationFileImport(csvData, code);
				} else {
					// Handle existing mapping types with their current validation logic
					List<String[]> validData = new ArrayList<>();
					List<IgnoredMapping> ignoredMappings = new ArrayList<>();
					// ... existing validation logic for other types ...
					return Mono.just(ParseResult.builder()
						.parsedData(validData)
						.ignoredMappings(ignoredMappings)
						.build());
				}
			})
			.onErrorMap(e -> {
				if (e instanceof FileUploadValidationException) {
					return e;
				}
				log.debug("A FileValidationException has occurred.", e);
				return new FileUploadValidationException("Error reading file: " + e.getMessage());
			});
	}


	// Methods for parsing the uploaded CSV/TSV file and running header and line-by-line validation.
	public static ParseResult parseCsv(Reader reader, String[] expectedHeaders, String code, String mappingCategory, String type) {
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
				// Switch on validation here. If we can refactor this out into methods that would be great
				// i.e. validateRVM, validateNRM, validateLoc

				if (type.equals("Locations")){
					// Validate accordingly

					// if validation passes, add line

				}


				if (line[0].isBlank() || line[1].isBlank() || line[2].isBlank() || line[3].isBlank() || line[4].isBlank() || line[5].isBlank())
				{
					validationError = "A mandatory field on line "+lineNumber+ " has been left empty. Please check your file and try again.";
					throw new FileUploadValidationException(validationError);
				}
				// Line by line validation for numeric range mappings
				// Validate that the context and domain match what's expected, and that there isn't a clash between them and what the user has supplied.
				if (type.equalsIgnoreCase("Numeric range mappings")) {
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
