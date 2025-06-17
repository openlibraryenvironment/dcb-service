package org.olf.dcb.utils;

import java.io.InputStreamReader;
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

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.api.exceptions.FileUploadValidationException;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.graphql.validation.LocationInputValidator;
import org.olf.dcb.storage.*;
import org.reactivestreams.Publisher;
import jakarta.inject.Singleton;


import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;


import java.util.*;
import java.util.stream.Collectors;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.core.annotation.Introspected;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.core.model.ReferenceValueMapping;
import reactor.util.function.Tuple2;
import services.k_int.utils.UUIDUtils;

@Singleton
@Slf4j
public class DCBConfigurationService {

	private HttpClient httpClient;
	private final NumericRangeMappingRepository numericRangeMappingRepository;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final LocationRepository locationRepository;
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

	List<String> validPatronTypes = Arrays.asList("ADULT", "CHILD", "FACULTY", "GRADUATE", "NOT_ELIGIBLE", "PATRON", "POSTDOC", "SENIOR", "STAFF", "UNDERGRADUATE", "YOUNG_ADULT");
	List<String> validItemTypes = Arrays.asList("CIRC", "NONCIRC", "CIRCAV");

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
	 * @param category The category of mapping - ItemType, patronType or Location.
	 * @param code The Host LMS code - corresponds to context or domain.
	 * @param file The mappings file (.tsv or .csv format).
	 */

	public Mono<UploadedConfigImport> importConfiguration(String type, String category, String code, CompletedFileUpload file, String reason, String changeCategory, String changeReferenceUrl, String username) {
		log.debug("importConfiguration({}, {}, {}, for user {})", type, category, file.getFilename(), username);

		if (code == null || code.isEmpty() || code.equals("undefined")) {
			return Mono.error(new FileUploadValidationException(
				"You must provide a Host LMS code to import mappings or locations. Please select a Host LMS in DCB Admin and retry."));
		}

		return Mono.fromCallable(() -> new InputStreamReader(file.getInputStream()))
			.flatMap(reader -> {
				String[] expectedHeaders = getExpectedHeaders(type);
				if (file.getFilename().endsWith(".tsv"))
					{
						return parseFile(reader, expectedHeaders, code, category, type, "tsv");
					}
				else
				{
					return parseFile(reader, expectedHeaders, code, category, type, "csv");
				}
			})
			.flatMap(parseResult -> processImport(
				type,
				category,
				code,
				parseResult.getParsedData(),
				parseResult.getIgnoredConfigItems(),
				reason,
				changeCategory,
				changeReferenceUrl,
				username
			))
			.doOnNext(uci -> {
				if (type.equals("Locations")) {
						log.info(
							"The import of pickup locations has completed. Message: {}, records imported: {}, records marked as deleted: {}, records ignored: {}",
							uci.getMessage(),
							uci.getRecordsImported(),
							uci.getRecordsDeleted(),
							uci.getIgnoredConfigItems().size()
						);
				}
				else
				{
					log.info(
						"The import of uploaded mappings has completed. Message: {}, records imported: {}, records marked as deleted: {}, records ignored: {}",
						uci.getMessage(),
						uci.getRecordsImported(),
						uci.getRecordsDeleted(),
						uci.getIgnoredConfigItems().size()
					);
				}
			});
	}


	// Methods for determining which file parsing method is required, and getting the correct expected headers for validation.
	private String[] getExpectedHeaders(String type) {
		return switch (type) {
			case "Reference value mappings" -> new String[]{"fromContext", "fromCategory", "fromValue", "toContext", "toCategory", "toValue"};
			case "Numeric range mappings" -> new String[]{"context", "domain", "lowerBound", "upperBound", "toValue", "toContext"};
			case "Locations" -> new String[]{"Agency Code", "Location Code", "Display Name", "Print Name", "DeliveryStop_Ignore", "Lat", "Lon", "isPickup", "LOCTYPE", "CHK_Ignore", "Address_Ignore", "id"};
			default -> throw new IllegalArgumentException("Unsupported config type: " + type);
		};
	}

// Methods for processing the import of reference value mappings OR numeric range mappings.
	private Mono<UploadedConfigImport> processImport(String type, String category, String code, List<String[]> data, List <IgnoredConfigItem> ignoredConfigItems, String reason, String changeCategory, String changeReferenceUrl, String username) {
		if (type.equals("Locations")) {
			return Mono.from(hostLmsRepository.findByCode(code))
				.switchIfEmpty(Mono.error(new FileUploadValidationException("Invalid Host LMS code provided")))
				.flatMap(hostLms -> {
					Set<Integer> ignoredLines = ignoredConfigItems.stream()
						.map(IgnoredConfigItem::getLineNumber)
						.collect(Collectors.toSet());

					// Filter out any row that corresponds to an ignored line
					List<String[]> validData = new ArrayList<>();
					for (int i = 0; i < data.size(); i++) {
						// Add 2 to i to match the line numbers in ignoredConfigItems (which account for header)
						if (!ignoredLines.contains(i + 2)) {
							validData.add(data.get(i));
						}
					}
					// First validate all locations
					return Flux.fromIterable(validData)
						.concatMap(line -> processLocationImport(line, hostLms, reason,
							changeCategory, changeReferenceUrl, username))
						.collectList()
						// After validation passes, delete existing locations and capture the count
						.flatMap(validatedLocations ->
							cleanupLocations(hostLms)
								.map(deletedCount -> new ValidationDeleteResult(validatedLocations, deletedCount))
						)
						// Then save all new locations
						.flatMap(result -> {
							List<Location> validatedLocations = result.validatedLocations();
							Long deletedCount = result.deletedCount();
							return Flux.fromIterable(validatedLocations)
								.concatMap(location ->
									Mono.from(locationRepository.saveOrUpdate(location))
								)
								.collectList()
								.map(locations -> UploadedConfigImport.builder()
									.message(locations.size()+" locations have been imported successfully.")
									.lastImported(Instant.now())
									.recordsImported((long) locations.size())
									.recordsDeleted(deletedCount)
									.recordsIgnored(ignoredConfigItems.size())
									.ignoredConfigItems(ignoredConfigItems)
									.build());
						});
				});
		} else {
			return cleanupMappings(type, category, code)
				.flatMap(cleanupResult -> {
					if ("Reference value mappings".equals(type)) {
						return processReferenceValueMappings(data, cleanupResult, ignoredConfigItems, reason, changeCategory, changeReferenceUrl, username);
					} else {
						return processNumericRangeMappings(data, cleanupResult, ignoredConfigItems, reason, changeCategory, changeReferenceUrl);
					}
				});
		}
	}

	private Mono<Location> processLocationImport(String[] line, DataHostLms hostLms, String reason, String changeCategory, String changeReferenceUrl, String username) {
		return Mono.from(agencyRepository.findOneByCode(line[0])).switchIfEmpty(Mono.error(new FileUploadValidationException("Agency not found for code: " + line[0])))
			.map(agency -> Location.builder()
				.id(UUIDUtils.generateLocationId(line[0], line[1]))
				.code(line[1])
				.name(line[2])
				.printLabel(line[3].isBlank() ? line[2] : line[3]) // If printLabel is blank, default to display name
				.deliveryStops(line[4].isBlank() ? line[0] : line[3]) // If delivery stops is blank, default to agency code
				.latitude(Double.valueOf(line[5]))
				.longitude(Double.valueOf(line[6]))
				.isPickup(Boolean.valueOf(line[7]))
				.type(line[8])
				.localId(line[11])
				.lastImported(Instant.now())
				.hostSystem(hostLms)
				.agency(agency)
				.reason(reason)
				.changeCategory(changeCategory)
				.changeReferenceUrl(changeReferenceUrl)
				.lastEditedBy(username)
				.build());
	}
	private Mono<UploadedConfigImport> processReferenceValueMappings(List<String[]> data, Long cleanupResult, List <IgnoredConfigItem> ignoredConfigItems, String reason, String changeCategory, String changeReferenceUrl, String username) {
		return Flux.fromIterable(data)
			.concatMap(rvm -> processReferenceValueMapping(rvm, reason, changeCategory, changeReferenceUrl, username))
			.collectList()
			.map(mappings -> UploadedConfigImport.builder()
				.message(mappings.size() + " mappings have been imported successfully.")
				.lastImported(Instant.now())
				.recordsImported((long) mappings.size())
				.recordsDeleted(cleanupResult)
				.recordsIgnored(ignoredConfigItems.size())
				.ignoredConfigItems(ignoredConfigItems)
				.build());
	}

	private Mono<UploadedConfigImport> processNumericRangeMappings(List<String[]> data, Long cleanupResult, List <IgnoredConfigItem> ignoredConfigItems, String reason, String changeCategory, String changeReferenceUrl) {
		return Flux.fromIterable(data)
			.concatMap(nrm -> processNumericRangeMapping(nrm, reason, changeCategory, changeReferenceUrl))
			.collectList()
			.map(mappings -> UploadedConfigImport.builder()
				.message(mappings.size() + " mappings have been imported successfully.")
				.lastImported(Instant.now())
				.recordsImported((long) mappings.size())
				.recordsDeleted(cleanupResult)
				.recordsIgnored(ignoredConfigItems.size())
				.ignoredConfigItems(ignoredConfigItems)
				.build());
	}

	private Mono<ParseResult> processLocationFileImport(List<String[]> data, String hostLmsCode) {
		return Mono.from(hostLmsRepository.findByCode(hostLmsCode))
			.switchIfEmpty(Mono.error(new FileUploadValidationException("Invalid Host LMS code provided")))
			.flatMap(hostLms -> {

				Set<String> localIds = new HashSet<>();
				Set<String> locationCodes = new HashSet<>();
				List<IgnoredConfigItem> duplicateItems = new ArrayList<>();

				for (int i = 0; i < data.size(); i++) {
					String[] line = data.get(i);
					String localId = line[11]; // localId
					String locationCode = line[1]; // location code
					int lineNumber = i + 2; // Accounting for header

					if (!localIds.add(localId)) {
						duplicateItems.add(new IgnoredConfigItem(
							"Duplicate localId " + localId + " found in the import file",
							lineNumber
						));
					}

					if (!locationCodes.add(locationCode)) {
						duplicateItems.add(new IgnoredConfigItem(
							"Duplicate location code " + locationCode + " found in the import file",
							lineNumber
						));
					}
				}
				return Flux.fromIterable(data)
					.index() // Keep track of line number
					.concatMap(tuple -> {
						String[] line = tuple.getT2();
						long lineNumber = tuple.getT1() + 2; // Add 2 to account for header and 0-based index

						Map<String, Object> input = Map.ofEntries(
							Map.entry("agencyCode", line[0]), // Also used for lookup
							Map.entry("code", line[1]),
							Map.entry("name", line[2]),
							Map.entry("printLabel", line[3].isBlank() ? line[2] : line[3]),
							Map.entry("deliveryStops", line[4].isBlank() ? line[0] : line[3]),
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
								log.debug("Error {}", e.getMessage());
								String errorMessage = e instanceof EntityCreationException ?
									e.getMessage() :
									"Invalid location data on line " + lineNumber;
								return Mono.just(new ValidationResult(
									null,
									new IgnoredConfigItem(errorMessage, (int) lineNumber)
								));
							});
					})
					.collectList()
					.map(results -> {
						List<String[]> validData = new ArrayList<>();
						List<IgnoredConfigItem> ignoredConfigItems = new ArrayList<>(duplicateItems);

						for (ValidationResult result : results) {
							if (result.validLine != null) {
								validData.add(result.validLine);
							}
							if (result.ignoredConfigItem != null) {
								ignoredConfigItems.add(result.ignoredConfigItem);
							}
						}
						return ParseResult.builder()
							.parsedData(validData)
							.ignoredConfigItems(ignoredConfigItems)
							.build();
					});
			});
	}


	private Mono<ValidationResult> validateMappingLine(String[] line, String code, String category, String type, int lineNumber) {
		if (type.equalsIgnoreCase("Numeric range mappings")) {
			return validateNumericRangeMapping(line, code, category, lineNumber);
		} else {
			return validateReferenceValueMapping(line, code, category, lineNumber);
		}
	}

	protected Mono<Long> cleanupLocations(DataHostLms hostLms) {
		log.debug("Cleaning up existing locations for host system {}", hostLms.getCode());
		return Mono.from(locationRepository.deleteByHostSystem(hostLms))
			.doOnNext(affectedRows -> log.info("Deleted {} existing locations for host system {}",
				affectedRows, hostLms.getCode()));
	}

	private Mono<ValidationResult> validateNumericRangeMapping(String[] line, String code, String category, int lineNumber) {
		// NRM line by line validation
		// Context must match code
		if (!line[0].equals(code)) {
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"The context does not match the Host LMS code you supplied. Please check your file and try again.",
					lineNumber
				)
			));
		}
		// If category is specified, domain must match
		if (!Objects.equals(category, "all") && !line[1].equals(category)) {
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"The category of this mapping does not match the domain in the file you have supplied and it will not be imported.",
					lineNumber
				)
			));
		}
		return Mono.just(new ValidationResult(line, null));
	}

	private Mono<ValidationResult> validateReferenceValueMapping(String[] line, String code, String category, int lineNumber) {
		String fromContext = line[0];
		String fromCategory = line[1];
		String fromValue = line[2];
		String toContext = line[3];
		String toCategory = line[4];
		String toValue = line[5];

		// Validate fromContext and toContext first, as these are category agnostic and one must match Host LMS code.
		boolean validContext = (fromContext.equalsIgnoreCase("DCB") && toContext.equalsIgnoreCase(code)) ||
			(fromContext.equalsIgnoreCase(code) && toContext.equalsIgnoreCase("DCB"));
		if (!validContext) {
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"Either the fromContext or toContext values in your file do not match the Host LMS code you supplied. Please check your file and try again.",
					lineNumber
				)
			));
		}
		// Validate category if specified
		if (!Objects.equals(category, "all") && !(fromCategory.equals(category) || toCategory.equals(category))) {
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"The fromCategory or toCategory of this mapping does not match the category you have specified. It will not be imported.",
					lineNumber
				)
			));
		}
		if (fromContext.equals(toContext)) {
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"Mapping could not be imported: 'fromContext' and 'toContext' cannot be the same.",
				  lineNumber
				)
			));
		}
		if ((category.equals("Location")) && !toContext.equals("DCB"))
		{
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"Mapping could not be imported: Location mapping must have a toContext value of 'DCB'.",
					lineNumber
				)
			));
		}

		if ((category.equals("Location") && !toCategory.equals("AGENCY")))
		{
			return Mono.just(new ValidationResult(
				null,
				new IgnoredConfigItem(
					"Mapping could not be imported: Location mapping must have a toCategory value of 'AGENCY'.",
					lineNumber
				)
			));
		}

		if ((category.equals("ItemType")  && (fromContext.equals("DCB") || toContext.equals("DCB"))))
		{
			if (fromContext.equals("DCB") && !validItemTypes.contains(fromValue))
			{
				log.debug("fromContext: {}, fromValue: {}", line[0], line[2]);
				return Mono.just(new ValidationResult(
					null,
					new IgnoredConfigItem(
						"This ItemType mapping will not be imported because its fromValue is invalid. Valid from values for this mapping are"+validItemTypes,
						lineNumber
					)
				));
			}
			else if (toContext.equals("DCB") && !validItemTypes.contains(toValue))
			{
				log.debug("toContext: {}, toValue: {}", line[3], line[5]);
				return Mono.just(new ValidationResult(
					null, new IgnoredConfigItem("This ItemType mapping will not be imported because its toValue is not valid. Valid to values for this mapping are"+validItemTypes, lineNumber)));
			}
		}
		if ((fromCategory.equals("patronType") || toCategory.equals("patronType")) && (fromContext.equals("DCB") || toContext.equals("DCB")))
		{
			if (fromContext.equals("DCB") && !validPatronTypes.contains(fromValue))
				return Mono.just(new ValidationResult(
			null, new IgnoredConfigItem("This patronType mapping will not be imported because its fromValue is not valid. Valid from values for this mapping are"+validPatronTypes, lineNumber)));
			else if (toContext.equals("DCB") && !validPatronTypes.contains(toValue))
			{
				return Mono.just(new ValidationResult(
					null, new IgnoredConfigItem("The toValue for this patronType mapping is not valid. Valid to values for this mapping are"+validPatronTypes, lineNumber)));
			}
		}
		return Mono.just(new ValidationResult(line, null));
	}
	private Mono<ParseResult> processMappingFileImport(List<String[]> fileData, String code, String category, String type) {
		return Flux.fromIterable(fileData)
			.index()
			.concatMap(tuple -> {
				String[] line = tuple.getT2();
				int lineNumber = (int) (tuple.getT1() + 2); // Add 2 to account for header and 0-based index
				return validateMappingLine(line, code, category, type, lineNumber);
			})
			.collectList()
			.map(results -> {
				List<String[]> validData = new ArrayList<>();
				List<IgnoredConfigItem> ignoredConfigItems = new ArrayList<>();
				for (ValidationResult result : results) {
					if (result.validLine() != null) {
						validData.add(result.validLine());
					}
					if (result.ignoredConfigItem() != null) {
						ignoredConfigItems.add(result.ignoredConfigItem());
					}
				}
				return ParseResult.builder()
					.parsedData(validData)
					.ignoredConfigItems(ignoredConfigItems)
					.build();
			});
	}

	// Method for parsing config from TSV or CSV files.
	public Mono<ParseResult> parseFile(Reader reader, String[] expectedHeaders, String code, String category, String type, String fileType) {
		return Mono.fromCallable(() -> {
			if (fileType.equals("csv"))
			{
				String validationError;
				try (CSVReader csvReader = new CSVReader(reader)) {
					// Get the header line
					String[] headers = csvReader.readNext();
					// Validate headers
					if (headers.length < expectedHeaders.length) {
						validationError = "File has fewer columns than expected. Expected " + expectedHeaders.length +
							" columns but found " + headers.length + ". Please check your CSV file and retry.";
						throw new FileUploadValidationException(validationError);
					}
					// Then validate each header matches
					for (int i = 0; i < expectedHeaders.length; i++) {
						if (!headers[i].equalsIgnoreCase(expectedHeaders[i])) {
							validationError = String.format("Header mismatch at column %d. Expected '%s' but found '%s'. " +
									"CSV headers do not match the expected headers: %s. Please check your CSV file and retry.",
								i + 1,
								expectedHeaders[i],
								headers[i],
								Arrays.toString(expectedHeaders));
							throw new FileUploadValidationException(validationError);
						}
					}
					// Read all data
					List<String[]> fileData = new ArrayList<>();
					String[] line;
					int lineNumber = 2;
					// Must be type specific
					// For NRMs and RVMs we're not bothered about anything after [5]
					// For locations it's a bit different and more header-based.

					while ((line = csvReader.readNext()) != null) {
						for (int i = 0; i < 6; i++) {
							if (i >= line.length || line[i] == null || line[i].trim().isEmpty() &&
								!(headers[i].equalsIgnoreCase("Print Name") || headers[i].equalsIgnoreCase("DeliveryStop_Ignore"))) {
								throw new FileUploadValidationException("A mandatory field (column " + (i + 1) +
									") on line " + lineNumber + " has been left empty. Please check your file and try again.");
							}
						}
						fileData.add(line);
						lineNumber++;
					}
					return fileData;
				}
			}
			else
			{
				String validationError;
				try (CSVReader TSVReader = new CSVReaderBuilder(reader)
					.withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
					.build()) {
					// Get the header line so we can compare
					String[] headers = TSVReader.readNext();
					// Validate headers
					if (headers.length < expectedHeaders.length) {
						validationError = "File has fewer columns than expected. Expected " + expectedHeaders.length +
							" columns but found " + headers.length + ". Please check your TSV file and retry.";
						throw new FileUploadValidationException(validationError);
					}
					// Then validate each header matches
					for (int i = 0; i < expectedHeaders.length; i++) {
						if (!headers[i].equalsIgnoreCase(expectedHeaders[i])) {
							validationError = String.format("Header mismatch at column %d. Expected '%s' but found '%s'. " +
									"TSV headers do not match the expected headers: %s. Please check your TSV file and retry.",
								i + 1,
								expectedHeaders[i],
								headers[i],
								Arrays.toString(expectedHeaders));
							throw new FileUploadValidationException(validationError);
						}
					}
					List<String[]> fileData = new ArrayList<>();
					String[] line;
					int lineNumber = 2;
					while ((line = TSVReader.readNext()) != null) {
						// Make this more specific
						for (int i = 0; i < 6; i++) {
							if (i >= line.length || line[i] == null || line[i].trim().isEmpty() &&
								!(headers[i].equalsIgnoreCase("Print Name") || headers[i].equalsIgnoreCase("DeliveryStop_Ignore"))) {
								throw new FileUploadValidationException("A mandatory field (column " + (i + 1) +
									") on line " + lineNumber + " has been left empty. Please check your file and try again.");
							}
						}
						fileData.add(line);
						lineNumber++;
					}
					return fileData;
				}
			}
			})
			.flatMap(fileData -> {
				if (type.equals("Locations")) {
					return processLocationFileImport(fileData, code);
				} else {
					return processMappingFileImport(fileData, code, category, type);
				}
			})
			.flatMap(parseResult -> {
				if (parseResult.getParsedData().isEmpty()) {
					// Create a comprehensive error message combining validation failures
					StringBuilder errorMessage = new StringBuilder("Import failed: No valid records were found in the file.");

					List<IgnoredConfigItem> ignoredMappings = parseResult.getIgnoredConfigItems();
					if (ignoredMappings != null && !ignoredMappings.isEmpty()) {
						errorMessage.append("\n\nValidation errors encountered:");
						// Group errors by reason to avoid repetition
						Map<String, List<Integer>> errorsByReason = ignoredMappings.stream()
							.collect(Collectors.groupingBy(
								IgnoredConfigItem::getReason,
								Collectors.mapping(IgnoredConfigItem::getLineNumber, Collectors.toList())
							));

						errorsByReason.forEach((reason, lines) -> {
							errorMessage.append("\n- ").append(reason);
							errorMessage.append(" (Lines: ").append(
								lines.stream()
									.map(String::valueOf)
									.collect(Collectors.joining(", "))
							).append(")");
						});
					}

					errorMessage.append("\n\nPlease correct these issues and try again.");

					return Mono.error(new FileUploadValidationException(errorMessage.toString()));
				}
				return Mono.just(parseResult);
			})
			.onErrorMap(e -> {
				if (e instanceof FileUploadValidationException) {
					return e;
				}
				log.debug("A FileValidationException has occurred.", e);
				return new FileUploadValidationException("Error reading file: " + e.getMessage());
			});
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

	private Mono<ReferenceValueMapping> processReferenceValueMapping(String[] rvm, String reason, String changeCategory, String changeReferenceUrl, String username) {
		ReferenceValueMapping rvmd= ReferenceValueMapping.builder()
			.id(UUIDUtils.dnsUUID(rvm[0]+":"+rvm[1]+":"+rvm[2]+":"+rvm[3]+":"+rvm[4]))
			.fromContext(rvm[0].trim())
			.fromCategory(rvm[1].trim())
			.fromValue(rvm[2].trim())
			.toContext(rvm[3].trim())
			.toCategory(rvm[4].trim())
			.toValue(rvm[5].trim())
			.lastImported(Instant.now())
			.deleted(false)
			.reason(reason)
			.changeCategory(changeCategory)
			.changeReferenceUrl(changeReferenceUrl)
			.lastEditedBy(username)
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
			List<IgnoredConfigItem> ignoredConfigItems;
	}

	@Data
	@Builder
	@Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
	@Introspected
	public static class ParseResult {
		List<String[]> parsedData;
		List<IgnoredConfigItem> ignoredConfigItems;
	}
	@Data
	@Builder
	@Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
	@Introspected
	public static class IgnoredConfigItem {
		String reason;
		Integer lineNumber;
	}
	// Helper class for validation results
	private record ValidationResult(String[] validLine, IgnoredConfigItem ignoredConfigItem) {
	}
	private record ValidationDeleteResult(List<Location> validatedLocations, Long deletedCount) {}

}

