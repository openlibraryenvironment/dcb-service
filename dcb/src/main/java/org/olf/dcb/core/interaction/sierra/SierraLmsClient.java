package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Calendar.YEAR;
import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.booleanPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.integerPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.utils.DCBStringUtilities.deRestify;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static services.k_int.utils.MapUtils.getAsOptionalString;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.BranchRecord;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.configuration.LocationRecord;
import org.olf.dcb.configuration.PickupLocationRecord;
import org.olf.dcb.configuration.RefdataRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition.IntegerHostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.RawSourceRepository;
import org.olf.dcb.tracking.model.LenderTrackingEvent;
import org.olf.dcb.tracking.model.PatronTrackingEvent;
import org.olf.dcb.tracking.model.PickupTrackingEvent;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.VarField;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.BranchInfo;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.PatronValidation;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.utils.UUIDUtils;



/**
 * See: <a href="https://sandbox.iii.com/iii/sierra-api/swagger/index.html">Sierra API Documentation</a>
 */
@Prototype
@Slf4j
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult> {
	private static final IntegerHostLmsPropertyDefinition GET_HOLDS_RETRY_ATTEMPTS_PROPERTY = integerPropertyDefinition(
		"get-holds-retry-attempts", "Number of retry attempts when getting holds for a patron", FALSE);

	private static final IntegerHostLmsPropertyDefinition PAGE_SIZE_PROPERTY = integerPropertyDefinition(
		"page-size", "How many items to retrieve in each page", FALSE);

	private static final String UUID5_PREFIX = "ingest-source:sierra-lms";
	private static final Integer FIXED_FIELD_158 = 158;

	private final ConversionService conversionService;
	private final HostLms lms;
	private final SierraApiClient client;
	private final ProcessStateService processStateService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final RawSourceRepository rawSourceRepository;
	private final NumericPatronTypeMapper numericPatronTypeMapper;
	private final SierraItemMapper itemMapper;

	private final Integer getHoldsRetryAttempts;

	public SierraLmsClient(@Parameter HostLms lms,
		HostLmsSierraApiClientFactory clientFactory,
		RawSourceRepository rawSourceRepository,
		ProcessStateService processStateService,
		ReferenceValueMappingService referenceValueMappingService,
		ConversionService conversionService,
		NumericPatronTypeMapper numericPatronTypeMapper, 
		SierraItemMapper itemMapper) {

		this.lms = lms;

		this.getHoldsRetryAttempts = getGetHoldsRetryAttempts(lms.getClientConfig());
		this.itemMapper = itemMapper;

		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
		this.rawSourceRepository = rawSourceRepository;
		this.processStateService = processStateService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.conversionService = conversionService;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
	}

	private static Integer getGetHoldsRetryAttempts(Map<String, Object> clientConfig) {
		return GET_HOLDS_RETRY_ATTEMPTS_PROPERTY.getOptionalValueFrom(clientConfig, 25);
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			urlPropertyDefinition("base-url", "Base URL Of Sierra System", TRUE),
			stringPropertyDefinition("key", "Key for this system", TRUE),
			PAGE_SIZE_PROPERTY,
			stringPropertyDefinition("secret", "Secret for this Sierra system", TRUE),
			booleanPropertyDefinition("ingest", "Enable record harvesting for this source", TRUE),
			GET_HOLDS_RETRY_ATTEMPTS_PROPERTY);
	}

	private Mono<BibResultSet> fetchPage(Instant since, int offset, int limit) {
		log.trace("Creating subscribable batch;  since={} offset={} limit={}", since, offset, limit);
		return Mono.from(client.bibs(params -> {
			params.offset(offset).limit(limit)
				.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "deleted", "marc", "suppressed", "fixedFields", "varFields" ));

			if (since != null) {
				params.updatedDate(dtr -> {
					LocalDateTime from_as_local_date_time = since.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime();
					log.trace("Setting from date for {} to {}", lms.getName(), from_as_local_date_time);

					dtr.to(LocalDateTime.now()).fromDate(from_as_local_date_time);
				});
			}
		})).doOnSubscribe(_s -> log.info("Fetching batch from Sierra {} with since={} offset={} limit={}", lms.getName(),
				since, offset, limit));
	}

	// processStateService now sets the transactional boundary
	// @Transactional(value = TxType.REQUIRES_NEW)
	@Override
	public Mono<PublisherState> saveState(@NonNull UUID id, @NonNull String processName, @NonNull PublisherState state) {
		log.debug("Update state {} - {}", state, lms.getName());

//		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state)).thenReturn(state);
		return Mono.from(processStateService.updateState(id, processName, state.storred_state)).thenReturn(state);
	}

	private Publisher<BibResult> pageAllResults(int pageSize, Publisher<String> terminator) {
		return Mono.from(getInitialState(lms.getId(), "ingest"))
				.flatMap(
						state -> Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state.since, state.offset, pageSize)))
				.expand(TupleUtils.function((state, results) -> {

					var bibs = results.entries();
					log.trace("Fetched a chunk of {} records for {}", bibs.size(), lms.getName());

					state.storred_state.put("lastRequestHRTS", new Date().toString());
					state.storred_state.put("status", "RUNNING");

					log.trace("got page {} of data, containing {} results", state.page_counter++, bibs.size());
					state.possiblyMore = bibs.size() == pageSize;

					// Increment the offset for the next fetch
					state.offset += bibs.size();

					// If we have exhausted the currently cached page, and we are at the end,
					// terminate.
					if (!state.possiblyMore) {
						log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(),
								state.request_start_time);

						// Make a note of the time at which we started this run, so we know where
						// to pick up from next time
						state.storred_state.put("cursor", "deltaSince:" + state.request_start_time);
						state.storred_state.put("name", lms.getName());
						state.storred_state.put("lastCompletedHRTS", new Date().toString());
						state.storred_state.put("status", "COMPLETED");

						log.info("No more results to fetch from {}", lms.getName());
						return Mono.empty();

					} else {
						log.trace("Exhausted current page from {} , prep next", lms.getName());
						// We have finished consuming a page of data, but there is more to come.
						// Remember where we got up to and stash it in the DB
						if (state.since != null) {
							state.storred_state.put("deltaHRTS", new Date(state.sinceMillis).toString());
							state.storred_state.put("cursor", "deltaSince:" + state.sinceMillis + ":" + state.offset);
						} else {
							state.storred_state.put("cursor", "bootstrap:" + state.offset);
						}
					}
					
					// Create a new mono that first saves the state and then uses it to fetch
					// another page.
					return Mono.just(state.toBuilder().build()) // toBuilder().build() should copy the object.
							.zipWhen(updatedState -> fetchPage(updatedState.since, updatedState.offset, pageSize));
				}))
				
				.takeUntilOther( Mono.from(terminator)
					.doOnNext( reason -> log.info("Ejecting from collect sequence. Reason: {}", reason) ))
				
				.concatMap(TupleUtils.function((state, page) -> {
					return Flux.fromIterable(page.entries())
							// Concatenate with the state so we can propagate signals from the save
							// operation.
							.concatWith(Mono.defer(() -> saveState(lms.getId(), "ingest", state))
									.flatMap(_s -> {
										log.debug("Updating state...");
										return Mono.empty();
									}))

							.doOnComplete(() -> log.debug("Consumed {} items", page.entries().size()));
				}));
	}

	@Override
	@NotNull
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

	@Override
	public Publisher<BibResult> getResources(Instant since, Publisher<String> terminator) {
		log.info("Fetching MARC JSON from Sierra for {}", lms.getName());

		final int pageSize = PAGE_SIZE_PROPERTY.getOptionalValueFrom(lms.getClientConfig(),
			DEFAULT_PAGE_SIZE);

		return Flux.from(pageAllResults(pageSize, terminator))
				.filter(sierraBib -> sierraBib.marc() != null)
				.onErrorResume(t -> {
					log.error("Error ingesting data {}", t.getMessage());
					t.printStackTrace();
					return Mono.empty();
				}).switchIfEmpty(Mono.fromCallable(() -> {
					log.info("No results returned. Stopping");
					return null;
		}));
	}

	public UUID uuid5ForBibResult(@NotNull final BibResult result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public IngestRecordBuilder initIngestRecordBuilder(BibResult resource) {

		// Use the host LMS as the
		IngestRecordBuilder irb =  IngestRecord.builder().uuid(uuid5ForBibResult(resource))
			.sourceSystem(lms)
			.sourceRecordId(resource.id())
			.suppressFromDiscovery(resource.suppressed())
			.deleted(resource.deleted());

		// log.info("resource id {}",resource.id());

		// If fixedField.get(26) - it's a map with int keys - contains a string "MULTI" then the bib is held at multiple locations
		if ( resource.fixedFields() != null ) {
			FixedField location = resource.fixedFields().get(26);
			// log.info("Got location {}",location);
			if ( location.getValue() != null ) {
				if ( location.getValue().toString().equalsIgnoreCase("MULTI")) {
					// log.info("multi");
					// The resource is held in multiple locations = SOME of those may be electronic, some may be physical - we need to actually parse the MARC to work out which is which
				}
				else {
					// log.info("Adding single location {}",location.getValue());
					irb.heldAtLocation(location.getValue().toString());
				}
			}
		}

		return irb;
	}

	public UUID uuid5ForRawJson(@NotNull final BibResult result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.id();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public RawSource resourceToRawSource(BibResult resource) {
		final var rawJson = conversionService.convertRequired(resource.marc(), JsonNode.class);

		@SuppressWarnings("unchecked")
		final Map<String, ?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);

		return RawSource.builder()
			.id(uuid5ForRawJson(resource))
			.hostLmsId(lms.getId())
			.remoteId(resource.id())
			.json(rawJsonString)
			.build();
	}

	@Override
	public Record resourceToMarc(BibResult resource) {
		return resource.marc();
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		// We start by working out what the right item type is for the target system.
		// E.g. DCB Item Type "STD" is a standard
		// circulating item, and for the "ARTHUR" system, the target iType is "209"
		return getMappedItemType(lms.getCode(), cic.getCanonicalItemType())
			.flatMap(itemType -> {
				log.debug("createItem in SierraLmsClient - itemType will be {}", itemType);

				// https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
				final var fixedFields = Map.of(
					61, FixedField.builder().label("DCB-" + itemType).value(itemType).build(),
					88, FixedField.builder().label("REQUEST").value("&").build());

				return Mono.from(
					client.createItem(
						ItemPatch.builder()
							.bibIds(List.of(Integer.parseInt(cic.getBibId())))
							.location(cic.getLocationCode())
							.barcodes(List.of(cic.getBarcode()))
							.fixedFields(fixedFields).build()));
			})
			.doOnSuccess(result -> log.debug("the result of createItem({})", result))
			.map(result -> deRestify(result.getLink()))
			// .map(result -> deRestify(result.getLink())).map(localId -> HostLmsItem.builder().localId(localId).build())
			// Try to read back the created item until we succeed or reach max retries - Sierra returns an ID but the ID is not ready for consumption
			// immediately.
      .flatMap(itemId -> Mono.defer(() -> getItem(itemId, null)).retry(getHoldsRetryAttempts))
			.switchIfEmpty(Mono.error(new RuntimeException("Unable to map canonical item type " + cic.getCanonicalItemType() + " for " + lms.getCode() + " context")));
	}

	// The item type passed with the item is our canonical system item type, we need
	// to look that up
	// into a local item type using a system mapping
	private Mono<String> getMappedItemType(String targetSystemCode, String itemTypeCode) {

		log.debug("getMappedItemType({},{})", targetSystemCode, itemTypeCode);

		// ToDo: Refactor tests so that this test causes a failure. both values should
		// always be present in
		// a production system, but we have many tests that don't set up the context, so
		// this test is to
		// allow the tests to work without needing ephemeral context.
		if ((targetSystemCode != null) && (itemTypeCode != null)) {
			// Map from the canonical DCB item type to the appropriate type for the target
			// system

			// HostLMS entries now have the ability to specify a cascading list of contexts from most specific to most general
			// E.G. Lib1, Consortium1, LMSType, Global. findMappingUsingHierarchy will follow this tree, returning the most
			// specific mapping possible, but falling back to global config. If no contextHierarchy is set, default to the old
			// behaviour of using the hostLMSCode as the context to search. Usually item types will be declared at the consortial level.
			List<String> contextHierarchy = (List<String>) lms.getClientConfig().get("contextHierarchy");
			if ( contextHierarchy == null )
				contextHierarchy = List.of(targetSystemCode);

			return referenceValueMappingService.findMappingUsingHierarchy("ItemType", "DCB", itemTypeCode, "ItemType", contextHierarchy)
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.defer(() -> {
				log.warn("Unable to map item type DCB:{} to target system {}",itemTypeCode,targetSystemCode);
				return Mono.just("UNKNOWN");
			}));
		}

		log.warn("Request to map item type was missing required parameters");
		return Mono.just("UNKNOWN");
	}

	public Mono<List<Item>> getItems(BibRecord bib) {
		log.debug("getItems({})", bib);

		final var localBibId = bib.getSourceRecordId();

		return Mono.from(client.items(params -> params
				.deleted(false)
				.bibIds(List.of(localBibId))
				.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "suppressed", "bibIds", "location",
					"status", "volumes", "barcode", "callNumber", "itemType", "transitInfo", "copyNo", "holdCount",
					"fixedFields", "varFields"))))
			.map(ResultSet::getEntries)
			.flatMapMany(Flux::fromIterable)
			.flatMap(result -> itemMapper.mapResultToItem(result, lms.getCode(), localBibId))
			.collectList();
	}

	public Mono<Patron> patronFind(String varFieldTag, String varFieldContent) {
		log.debug("patronFind({}, {})", varFieldTag, varFieldContent);

		return Mono.from(client.patronFind(varFieldTag, varFieldContent))
			.filter(result -> nonNull(result.getId()) && nonNull(result.getPatronType()))
			.flatMap(this::sierraPatronToHostLmsPatron)
			.onErrorResume(NullPointerException.class, error -> {
				log.error("NullPointerException occurred when finding Patron: {}", error.getMessage());
				return Mono.empty();
			});
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		log.info("{} attempt patronAuth({},{},...)", lms.getCode(), authProfile, patronPrinciple);

		return switch (authProfile) {
			case "BASIC/BARCODE+PIN" -> validatePatronCredentials(patronPrinciple, secret, "b");
			case "BASIC/BARCODE+NAME" -> validatePatronByIDAndName(patronPrinciple, secret, "b");
			case "BASIC/UNIQUE-ID+PIN" -> validatePatronCredentials(patronPrinciple, secret,"u");
			case "BASIC/UNIQUE-ID+NAME" -> validatePatronByIDAndName(patronPrinciple, secret, "u");
			default -> Mono.empty();
		};
	}

	// In sierra validate == process with pin and whatever identifier the patron wants to send but it only returns boolean
	private Mono<Patron> validatePatronCredentials(String principal, String pin, String principalType) {

		final var patronValidationRequest = PatronValidation.builder()
			.barcode(principal)
			.pin(pin)
			.caseSensitivity(Boolean.FALSE)
			.build();

		log.info("Attempt client patron validation : {}", patronValidationRequest);

		return Mono.from(client.validatePatron(patronValidationRequest))
			.doOnError(error -> log.debug("response of validatePatronCredentials for {}", patronValidationRequest, error))
			.filter(result -> result == Boolean.TRUE)
			.flatMap( result -> patronFind(principalType,principal));
	}

	// The correct URL for validating patrons in sierra is
	// "/iii/sierra-api/v6/patrons/validate";
	// If field is b we will lookup by barcode before attempting name check, if u it will be unique ID
	private Mono<Patron> validatePatronByIDAndName(String principal, String name, String principalField) {
		log.debug("validatePatronByIDAndName({},{},{})", principal, name, principalField);

		if ((name == null) || (name.length() < 4))
			return Mono.empty();

		// If the provided name is present in any of the names coming back from the
		// client
		return patronFind(principalField, principal)
			.doOnSuccess(patron -> log.info("Testing {}/{} to see if {} is present", patron, patron.getLocalNames(), name))
			.filter(patron -> patron.getLocalNames().stream()
				.anyMatch(s -> s.toLowerCase()
				.startsWith(name.toLowerCase())));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		// Look up virtual patron using generated unique ID string
		final var uniqueId = getValue(patron, org.olf.dcb.core.model.Patron::determineUniqueId);

		return patronFind("u", uniqueId);
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		log.debug("postPatron({})", patron);

		if (patron.getExpiryDate() == null) {
			// No patron expiry - default to 10 years hence
			final var calendar = Calendar.getInstance();
			calendar.setTime(new Date());
			calendar.add(YEAR, 10);
			patron.setExpiryDate(calendar.getTime());
		}

		final var df = new SimpleDateFormat("yyyy-MM-dd");
		final var tz = TimeZone.getTimeZone("UTC");
		df.setTimeZone(tz);

		final String patronExpirationDate = df.format(patron.getExpiryDate());

		final var patronPatch = PatronPatch.builder()
			.patronType(parseInt(patron.getLocalPatronType()))
			.uniqueIds(Objects.requireNonNullElseGet(patron.getUniqueIds(), Collections::emptyList))
			// Unique IDs are used for names to avoid transmission of personally
			// identifiable information
			.names(Objects.requireNonNullElseGet(patron.getUniqueIds(), Collections::emptyList))
			.barcodes(Objects.requireNonNullElseGet(patron.getLocalBarcodes(), Collections::emptyList))
			.expirationDate(patronExpirationDate)
			.build();

		return Mono.from(client.patrons(patronPatch))
			.doOnSuccess(result -> log.debug("the result of createPatron({})", result))
			.map(patronResult -> deRestify(patronResult.getLink()))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Patron: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Patron"));
			});
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		log.debug("createBib(bib: {})", bib);

		// Setting fixedField 031 to n which indicates that the record should be
		// suppressed from discovery.
		final var fixedFields = Map.of(31, FixedField.builder().label("suppress").value("n").build());
		final var authors = (bib.getAuthor() != null) ? List.of(bib.getAuthor()) : null;
		final var titles = (bib.getTitle() != null) ? List.of(bib.getTitle()) : null;

		final var bibPatch = BibPatch.builder().authors(authors).titles(titles).fixedFields(fixedFields).build();

		log.debug("create bib using Bib patch {}", bibPatch);

		return Mono.from(client.bibs(bibPatch))
			.doOnSuccess(result -> log.debug("the result of createBib({})", result))
			.map(bibResult -> deRestify(bibResult.getLink()))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Bib: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Bib"));
			})
			.switchIfEmpty(Mono.error(new RuntimeException("Failed to create bib record at " + lms.getCode())));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		// This is left as a comment for now - there is some discussion as to what the best thing to do is in sierra 
		// when placing a hold. For now we revert to the original plan - set the pickup location to the 

		// When placing the hold on a suppling location, the item will be "Picked up" by the transit van at the
		// location where the item currently resides
		// return placeHoldRequest(parameters, parameters.getSupplyingLocalItemLocation());

		return placeHoldRequest(parameters, parameters.getPickupLocation());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {
		// When placing the hold at a borrower system we want to use the pickup location code as selected by
		// the patron
		return placeHoldRequest(parameters, parameters.getPickupLocation());
	}

	private Mono<LocalRequest> placeHoldRequest(
		PlaceHoldRequestParameters parameters, String pickupLocation) {
		log.debug("placeHoldRequest({})", parameters);

		final String recordNumber;
		final String recordType;

		if (useTitleLevelRequest()) {
			log.debug("place title level request with ID: {} for local patron ID: {}",
				parameters.getLocalBibId(), parameters.getLocalPatronId());

			recordType = "b";
			recordNumber = parameters.getLocalBibId();
		}
		else if (useItemLevelRequest()) {
			log.debug("place item level request for ID: {} for local patron ID: {}",
				parameters.getLocalItemId(), parameters.getLocalPatronId());

			recordType = "i";
			recordNumber = parameters.getLocalItemId();
		}
		else {
			return Mono.error(new RuntimeException(
				"Invalid hold policy for Host LMS \"" + getHostLmsCode() + "\""));
		}

		var patronHoldPost = PatronHoldPost.builder()
			.recordType(recordType)
			.recordNumber(convertToInteger(recordNumber, "hold record number"))
			// We suspect that Sierra needs the pickup location at the supplying agency to be the loc where the item
			// is currently held. We can do this via the RTAC result or by looking up the item and finding the loc.
			// Easiest is to use the RTAC result.
			.pickupLocation(pickupLocation)
			.note(parameters.getNote())
			.build();

		// Ian: NOTE... SIERRA needs time between placeHoldRequest and
		// getPatronHoldRequestId completing... Either
		// we need retries or a delay.
		return Mono.from(client.placeHoldRequest(parameters.getLocalPatronId(), patronHoldPost))
			.then(Mono.defer(() -> getPatronHoldRequestId(parameters.getLocalPatronId(),
					recordNumber, parameters.getNote(), parameters.getPatronRequestId()))
				.retry(getHoldsRetryAttempts))
			// If we were lucky enough to get back an Item ID, go fetch the barcode, otherwise this is just a bib or volume request
			.flatMap(localRequest -> addBarcodeIfItemIdPresent(localRequest) )
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Hold: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Hold"));
			});
	}

	private Mono<LocalRequest> addBarcodeIfItemIdPresent(LocalRequest localRequest) {
		if ( localRequest.getRequestedItemId() != null ) {
			return getItem(localRequest.getRequestedItemId(), localRequest.getLocalId())
          .map(item -> LocalRequest.builder()
            .localId(localRequest.getLocalId())
            .localStatus(localRequest.getLocalStatus())
            .requestedItemId(localRequest.getRequestedItemId())
            .requestedItemBarcode(item.getBarcode())
            .build());
		}
		else {
			return Mono.just(localRequest);
		}
	}

	private boolean shouldIncludeHold(SierraPatronHold hold, String patronRequestId) {
		return (hold != null) && (hold.note() != null) && (hold.note().contains(patronRequestId));
	}

	private Mono<LocalRequest> getPatronHoldRequestId(String patronLocalId,
		String localItemId, String note, String patronRequestId) {

		log.debug("getPatronHoldRequestId({}, {}, {}, {})", patronLocalId,
			localItemId, note, patronRequestId);

		// Ian: TEMPORARY WORKAROUND - Wait for sierra to process the hold and make it
		// visible
		synchronized (this) {
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
			}
		}

		return Mono.from(client.patronHolds(patronLocalId))
			.map(SierraPatronHoldResultSet::entries)
			.doOnNext(entries -> log.debug("Hold entries: {}", entries))
			.flatMapMany(Flux::fromIterable)
			.filter(hold -> shouldIncludeHold(hold, patronRequestId))
			.collectList()
			.map(filteredHolds -> chooseHold(note, filteredHolds))
			// We should retrieve the item record for the selected hold and store the barcode here
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when getting Hold: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when getting Hold"));
			});
	}

	// Informed by
	// https://techdocs.iii.com/sierraapi/Content/zObjects/holdObjectDescription.htm
	private String mapSierraHoldStatusToDCBHoldStatus(String code, String itemId) {

		if (isEmpty(code)) {
			throw new RuntimeException("Hold from Host LMS \"%s\" has no status code"
				.formatted(getHostLmsCode()));
		}

		return switch (code) {
			case "0" -> ( itemId != null ? HostLmsRequest.HOLD_CONFIRMED : HostLmsRequest.HOLD_PLACED );
			case "b" -> HostLmsRequest.HOLD_READY; // Bib ready for pickup
			case "j" -> HostLmsRequest.HOLD_READY; // volume ready for pickup
			case "i" -> HostLmsRequest.HOLD_READY; // Item ready for pickup
			case "t" -> HostLmsRequest.HOLD_TRANSIT; // IN Transit
			default -> code;
		};
	}

	// Informed by
	// https://techdocs.iii.com/sierraapi/Content/zObjects/holdObjectDescription.htm
	// private String mapSierraItemStatusToDCBHoldStatus(String code) {
	private String mapSierraItemStatusToDCBHoldStatus(Status status) {
		String result;

		if ((status.getDuedate() != null) && (!status.getCode().trim().isEmpty())) {
			log.info("Item has a due date, setting item status to LOANED");
			result = HostLmsItem.ITEM_LOANED;
		} else {
			switch (status.getCode()) {
				case "-" -> result = HostLmsItem.ITEM_AVAILABLE;
				case "t" -> result = HostLmsItem.ITEM_TRANSIT; // IN Transit
				case "@" -> result = HostLmsItem.ITEM_OFFSITE;
				case "#" -> result = HostLmsItem.ITEM_RECEIVED;
				case "!" -> result = HostLmsItem.ITEM_ON_HOLDSHELF;
				case "o" -> result = HostLmsItem.LIBRARY_USE_ONLY;
				case "%" -> result = HostLmsItem.ITEM_RETURNED;
				case "m" -> result = HostLmsItem.ITEM_MISSING;
				case "&" -> result = HostLmsItem.ITEM_REQUESTED;
				default -> result = status.getCode();
			}
		}

		return result;
	}

	private LocalRequest chooseHold(String note, List<SierraPatronHold> filteredHolds) {
		log.debug("chooseHold({},{})", note, filteredHolds);

		if (filteredHolds.size() == 1) {

			SierraPatronHold sph = filteredHolds.get(0);

			final var extractedId = deRestify(sph.id());

			String requestedItemId = null;

			if ( ( sph.recordType().equals("i") ) && ( sph.record() != null ) ) {	
				log.info("Found item ID returned by hold.... get the details and set requested item ID to {}", sph.record());
				requestedItemId = deRestify(sph.record());
			}
			final var localStatus = mapSierraHoldStatusToDCBHoldStatus(sph.status().code(), requestedItemId);

			// 
			// It's not an error if the hold returns a bib record - that just means that this hold has not yet confirmed
			// Choose hold is about selecting the hold for the request by checking that the note contains the request ID
			// IF it's become an item level hold then we return the selected item ID, but we don't want to throw an exception
			// just because the bib hold has not yet been converted to an item hold
			//
			// else {
			// 	final var errorMessage = "chooseHold returned a record which was NOT an item or record was null %s:%s"
			// 		.formatted(sph.recordType(), sph.record());
			// 	log.warn(errorMessage);
				// Retries are triggered on error signal
			// 	throw new RuntimeException(errorMessage);
			// }

			return LocalRequest.builder()
				.localId(extractedId)
				.localStatus(localStatus)
				.requestedItemId(requestedItemId)
				.build();

		} else if (filteredHolds.size() > 1) {
			throw new RuntimeException("Multiple hold requests found for the given note: " + note);
		} else {
			throw new RuntimeException("No hold request found for the given note: " + note);
		}
	}

	private static int convertToInteger(String integer, String parameterDescription) {
		try {
			return parseInt(integer);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
				"Invalid integer value: \"%s\" for parameter \"%s\""
					.formatted(integer, parameterDescription), e);
		}
	}

	private boolean useTitleLevelRequest() {
		return holdPolicyIs("title");
	}

	private boolean useItemLevelRequest() {
		return holdPolicyIs("item");
	}

	private boolean holdPolicyIs(String expectedHoldPolicy) {
		final var actualHoldPolicy = Optional.ofNullable(getConfig())
			.map(config -> config.get("holdPolicy"))
			.orElse("item");

		return actualHoldPolicy.equals(expectedHoldPolicy);
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	@Override
	public boolean isEnabled() {
		return getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(TRUE);
	}

	private UUID uuid5ForBranch(@NotNull final String hostLmsCode, @NotNull final String localBranchId) {
		final String concat = UUID5_PREFIX + ":BRANCH:" + hostLmsCode + ":" + localBranchId;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	private UUID uuid5ForLocation(@NotNull final String hostLmsCode, @NotNull final String localBranchId,
			@NotNull final String locationCode) {
		final String concat = UUID5_PREFIX + ":LOC:" + hostLmsCode + ":" + localBranchId + ":" + locationCode;
		log.debug("Create uuid5ForLocation {}",concat);
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	private UUID uuid5ForPickupLocation(@NotNull final String hostLmsCode, @NotNull final String locationCode) {
		// ToDo - work out if this shoud be :PL:
		final String concat = UUID5_PREFIX + ":SL:" + hostLmsCode + ":" + locationCode;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public RawSourceRepository getRawSourceRepository() {
		return rawSourceRepository;
	}

	private ConfigurationRecord mapSierraBranchToBranchConfigurationRecord(BranchInfo bi) {
		final var locations = new ArrayList<LocationRecord>();

		if (bi.locations() != null) {
			for (Map<String, String> sub_location : bi.locations()) {
				// It appears these are not only shelving locations but more general location
				// records attached to the location
				locations.add(new LocationRecord(uuid5ForLocation(lms.getCode(), bi.id(), sub_location.get("code").trim()),
						sub_location.get("code").trim(), sub_location.get("name")));
			}
		}

		return BranchRecord.builder()
			.id(uuid5ForBranch(lms.getCode(), bi.id()))
			.lms(lms)
			.localBranchId(bi.id())
			.branchName(bi.name())
			.lat(bi.latitude() != null ? Float.valueOf(bi.latitude()) : null)
			.lon(bi.longitude() != null ? Float.valueOf(bi.longitude()) : null)
			.subLocations(locations)
			.build();
	}

	private ConfigurationRecord mapSierraPickupLocationToPickupLocationRecord(PickupLocationInfo pli) {
		return PickupLocationRecord.builder()
			.id(uuid5ForPickupLocation(lms.getCode(), pli.code().trim()))
			.lms(lms)
			.code(pli.code().trim())
			.name(pli.name())
			.build();
	}

	private Publisher<ConfigurationRecord> getBranches() {
		final var fields = List.of("name", "address", "emailSource", "emailReplyTo",
			"latitude", "longitude", "locations");

		return Flux.from(client.branches(100, 0, fields))
			.flatMap(results -> Flux.fromIterable(results.entries()))
			.map(this::mapSierraBranchToBranchConfigurationRecord);
	}

	private Publisher<ConfigurationRecord> getPickupLocations() {
		return Flux.from(client.pickupLocations())
			.flatMap(Flux::fromIterable)
			.map(this::mapSierraPickupLocationToPickupLocationRecord);
	}

	private Publisher<ConfigurationRecord> getPatronMetadata() {
		return Flux.from(client.patronMetadata())
			.flatMap(Flux::fromIterable)
			.flatMap(result -> Flux.fromIterable(result.values())
				.flatMap(item -> Mono.just(Tuples.of(item, result.field()))))
			.map(tuple -> mapSierraPatronMetadataToConfigurationRecord(tuple.getT1(), tuple.getT2()));
	}

	private ConfigurationRecord mapSierraPatronMetadataToConfigurationRecord(
		Map<String, Object> rdv, String field) {

		return RefdataRecord.builder()
			.category(field)
			.context(lms.getCode())
			.id(uuid5ForConfigRecord(field, rdv.get("code").toString().trim()))
			.lms(lms)
			.value(rdv.get("code").toString().trim())
			.label(rdv.get("desc").toString())
			.build();
	}

	private UUID uuid5ForConfigRecord(String field, String code) {
		final String concat = UUID5_PREFIX + ":RDV:" + lms.getCode() + ":" + field + ":" + code;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {

		// II: This is just creating confusion disabling for now
		return Mono.empty();

		// return Flux.from(getBranches())
		// 	.concatWith(getPickupLocations())
		// 	.concatWith(getPatronMetadata());
	}

	public TrackingRecord sierraPatronHoldToTrackingData(SierraPatronHold sph) {
		// log.debug("Convert {}",sph);
		TrackingRecord result = null;
		if (sph.patron().contains("@")) {
			// The patron identifier contains a % - this hold is either a supplier OR a
			// pickup Hold
			if (sph.record().contains("@")) {
				// The record contains a remote reference also - this is a pickup record
				result = PickupTrackingEvent.builder().hostLmsCode(lms.getCode()).build();
			} else {
				// This is a lender hold - shipping an item to a pickup location or direct to a
				// patron home
				result = LenderTrackingEvent.builder().hostLmsCode(lms.getCode()).normalisedRecordType(sph.recordType())
					.localHoldId(deRestify(sph.id())).localPatronReference(deRestify(sph.patron()))
					.localRecordId(deRestify(sph.record())).localHoldStatusCode(sph.status().code())
					.localHoldStatusName(sph.status().name())
					.pickupLocationCode(sph.pickupLocation() != null ? sph.pickupLocation().code() : null)
					.pickupLocationName(sph.pickupLocation() != null ? sph.pickupLocation().name() : null).build();
			}
		} else if (sph.record().contains("@")) {
			// patron does not contain % but record does - this is a request from a remote
			// site to
			// a patron at this system
			result = PatronTrackingEvent.builder().hostLmsCode(lms.getCode()).build();
		} else {
			// Hold record relates to internal activity and can be skipped
			// log.debug("No remote indications for this hold
			// {}/{}/{}",sph.patron(),sph.record(),sph.pickupLocation());
		}
		return result;
	}

	public Publisher<TrackingRecord> getTrackingData() {
		log.debug("getTrackingData");

		final var init = new SierraPatronHoldResultSet(0, 0, new ArrayList<>());

		return Flux.just(init).expand(lastPage -> {
			log.debug("Fetch pages of data from offset {}, total: {}", lastPage.start(), lastPage.total());

			return Mono
				.from(client.getAllPatronHolds(250, lastPage.start() + lastPage.entries().size()))
				.filter(m -> !m.entries().isEmpty()).switchIfEmpty(Mono.empty());

		}).flatMapIterable(SierraPatronHoldResultSet::entries) // <- prefer this to this ->.flatMapIterable(Function.identity())
				// Note to self: *Don't do this* it turns the expand above into an eager hot
				// publisher that will kill the system
				// .onBackpressureBuffer(100, null, BufferOverflowStrategy.ERROR)
				.flatMap(ri -> {
					TrackingRecord tr = sierraPatronHoldToTrackingData(ri);
					if (tr != null)
						return Mono.just(tr);
					else
						return Mono.empty();
				});
	}

	private Mono<Patron> sierraPatronToHostLmsPatron(SierraPatronRecord spr) {
		log.debug("sierraPatronToHostLmsPatron({})", spr);
		String patronLocalAgency = null;

		// If we were supplied fixed fields, and we can find an entry for fixed field
		// 158, grab the patron agency
		if ((spr.getFixedFields() != null) && (spr.getFixedFields().get(FIXED_FIELD_158) != null)) {
			patronLocalAgency = spr.getFixedFields().get(FIXED_FIELD_158).getValue().toString();
		}

		final var result = Patron.builder()
			.localId(singletonList(valueOf(spr.getId())))
			.localPatronType(valueOf(spr.getPatronType()))
			.localBarcodes(spr.getBarcodes())
			.localNames(spr.getNames())
			.localHomeLibraryCode(spr.getHomeLibraryCode())
			.build();

		if ((result.getLocalBarcodes() == null) || (result.getLocalBarcodes().isEmpty()) )
			log.warn("Returned patron has NO BARCODES : {} -> {}",spr, result);

		return Mono.just(result)
			.flatMap(this::enrichWithCanonicalPatronType);
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron p) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(lms.getCode(),
				p.getLocalPatronType(), p.getLocalId().stream().findFirst().orElse(null))
			.map(p::setCanonicalPatronType)
			.defaultIfEmpty(p);
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {

		List<String> contextHierarchy = (List<String>) lms.getClientConfig().get("contextHierarchy");
		if ( contextHierarchy == null )
			contextHierarchy = List.of(getHostLmsCode());

		// Map from DCB-PatronType-e.g.11 to Local, consortial, global
		return referenceValueMappingService.findMappingUsingHierarchy("patronType", "DCB", canonicalPatronType, "patronType", contextHierarchy)
			.map(ReferenceValueMapping::getToValue);
	}

	@Override
	public Mono<String> findCanonicalPatronType(String localPatronType, String localId) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(
			getHostLmsCode(), localPatronType, localId);
	}

	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		log.debug("getPatronByLocalId({})", localPatronId);

		return Mono.from(client.getPatron(Long.valueOf(localPatronId)))
			.flatMap(this::sierraPatronToHostLmsPatron)
			.switchIfEmpty(Mono.error(patronNotFound(localPatronId, getHostLmsCode())));
	}

        public Mono<Patron> getPatronByUsername(String username) {
		log.debug("getPatronByUsername({})", username);
		// This is complicated because in sierra, users log on by different fields - some systems are
		// configured to have users log in by barcode, and others use uniqueId. Here we're going to try a
		// belt and braces approach and look up by UniqueId first, then Barcode
		return patronFind("u", username)
			.switchIfEmpty(Mono.defer(() -> { return patronFind("b", username); }));
	}


	@Override
	public Mono<Patron> updatePatron(String localPatronId, String patronType) {
		log.debug("updatePatron({})", localPatronId);

		final var patronPatch = PatronPatch.builder().patronType(parseInt(patronType)).build();

		return Mono.from(client.updatePatron(Long.valueOf(localPatronId), patronPatch))
			.flatMap(this::sierraPatronToHostLmsPatron)
			.switchIfEmpty(Mono.error(patronNotFound(localPatronId, getHostLmsCode())));
	}

	public HostLmsRequest sierraPatronHoldToHostLmsHold(SierraPatronHold sierraHold) {
		log.debug("sierraHoldToHostLmsHold({})", sierraHold);
		if ((sierraHold != null) && (sierraHold.id() != null)) {
			// Hold API sends back a hatheos style URI - we just want the hold ID
			String holdId = sierraHold.id().substring(sierraHold.id().lastIndexOf('/') + 1);

			String requestedItemId = null;
			if ( ( sierraHold.recordType() != null ) && ( sierraHold.recordType().equals("i")) ) {
				requestedItemId = deRestify(sierraHold.record());
			}

			// Map the hold status into a canonical value
			return new HostLmsRequest(holdId,
				sierraHold.status() != null ? mapSierraHoldStatusToDCBHoldStatus(sierraHold.status().code(), requestedItemId) : "",
				requestedItemId);
		} else {
			return new HostLmsRequest();
		}
	}

	@Override
	public Mono<HostLmsRequest> getRequest(String localRequestId) {
		log.debug("getRequest({})", localRequestId);
		return Mono.from(client.getHold(Long.valueOf(localRequestId)))
			.flatMap(sh -> Mono.just(sierraPatronHoldToHostLmsHold(sh)))
			.defaultIfEmpty(new HostLmsRequest(localRequestId, "MISSING"));
	}

	// II: We need to talk about this in a review session
	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		log.debug("updateItemStatus({},{})", itemId, crs);
		// See
		// https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#Standard
		// In Sierra "-" == AVAILABLE, !=ON_HOLDSHELF, $=BILLED PAID, m=MISSING,
		// n=BILLED NOT PAID, z=CL RETURNED, o=Lib Use Only, t=In Transit
		// # - RECEIVED
		String status = switch (crs) {
			case TRANSIT -> "t";
			case OFFSITE -> "@";
			case AVAILABLE -> "-";
			case RECEIVED -> "#";
			default -> null;
		};

		if (status != null) {
			final var messages = new ArrayList<String>();
			final var var_fields = new ArrayList<VarField>();
			var_fields.add(
				VarField.builder()
					.fieldTag("m")
					.content("-")
					.build()
			);

			final var ip = ItemPatch.builder()
				.status(status)
				// .itemMessage("-") - 21-01-2024 TA asked to have this field (Sierra FixedField) NOT updated by this call
				.messages(messages)
				.varFields(var_fields) // - 21-01-2024 Trying this instead to clear in transit status
				.build();

			return Mono.from(client.updateItem(itemId, ip))
				.thenReturn("OK");
		} else {
			log.warn("Update item status requested for {} and we don't have a sierra translation for that", crs);
			return Mono.just("OK");
		}
	}

	private HostLmsItem sierraItemToHostLmsItem(SierraItem si) {
		log.debug("convert {} to HostLmsItem", si);

		if ( ( si.getStatus() == null ) || ( si.getBarcode() == null ) || ( si.getId() == null ) ) {
			log.warn("Detected a sierra item with null status: {}",si);
		}

		String resolved_status = si.getStatus() != null
			? mapSierraItemStatusToDCBHoldStatus(si.getStatus())
			: (si.getDeleted() ? "MISSING" : "UNKNOWN");

		return HostLmsItem.builder()
			.localId(si.getId())
			.barcode(si.getBarcode())
			.status(resolved_status).build();
	}

	@Override
	public Mono<HostLmsItem> getItem(String localItemId, String localRequestId) {
		log.debug("getItem({}, {})", localItemId, localRequestId);

		if ( localItemId != null ) {
			return Mono.from(client.getItem(localItemId))
				.flatMap(sierraItem -> Mono.just(sierraItemToHostLmsItem(sierraItem)))
				.defaultIfEmpty(HostLmsItem.builder().localId(localItemId).status("MISSING").build());
		}

		log.warn("getItem called with null itemId");
		return Mono.empty();
	}

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	@Override
	public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode, String localRequestId) {
		log.debug("checkOutItemToPatron({},{})", itemId, patronBarcode);

		return Mono.from(client.checkOutItemToPatron(itemId, patronBarcode))
			.thenReturn("OK")
			.switchIfEmpty(Mono.error(() ->
				new RuntimeException("Check out of " + itemId + " to " + patronBarcode + " at " + lms.getCode() + " failed")));
	}

	@Override
	public Mono<String> deleteItem(String id) {
		log.debug("deleteItem({})", id);

		return Mono.from(client.deleteItem(id)).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public Mono<String> deleteBib(String id) {
		log.debug("deleteBib({})", id);

		return Mono.from(client.deleteBib(id)).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public ProcessStateService getProcessStateService() {
		return this.processStateService;
	}

	@Override
	public PublisherState mapToPublisherState(Map<String, Object> current_state) {
		PublisherState generator_state = new PublisherState(current_state);
		log.info("backpressureAwareBibResultGenerator - state=" + current_state + " lmsid=" + lms.getId() + " thread="
				+ Thread.currentThread().getName());

		String cursor = (String) current_state.get("cursor");
		if (cursor != null) {
			log.debug("Cursor: " + cursor);
			String[] components = cursor.split(":");

			if (components[0].equals("bootstrap")) {
				// Bootstrap cursor is used for the initial load where we need to just page
				// through everything
				// from day 0
				generator_state.offset = parseInt(components[1]);
				log.info("Resuming bootstrap for " + lms.getName() + " at offset " + generator_state.offset);
			} else if (components[0].equals("deltaSince")) {
				// Delta cursor is used after the initial bootstrap and lets us know the point
				// in time
				// from where we need to fetch records
				generator_state.sinceMillis = Long.parseLong(components[1]);
				generator_state.since = Instant.ofEpochMilli(generator_state.sinceMillis);
				if (components.length == 3) {
					// We're recovering from an interuption whilst processing a delta
					generator_state.offset = parseInt(components[2]);
				}
				log.info("Resuming delta at timestamp " + generator_state.since + " offset=" + generator_state.offset + " name="
						+ lms.getName());
			}
		} else {
			log.info("Start a fresh ingest");
		}

		// Make a note of the time before we start
		generator_state.request_start_time = System.currentTimeMillis();
		log.debug("Create generator: name={} offset={} since={}", lms.getName(), generator_state.offset,
				generator_state.since);

		return generator_state;
	}

	private static RuntimeException patronNotFound(String localId, String hostLmsCode) {
		return new PatronNotFoundInHostLmsException(localId, hostLmsCode);
	}

	public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
    log.debug("SIERRA Supplier Preflight {} {} {} {}",borrowingAgencyCode,supplyingAgencyCode,canonicalItemType,canonicalPatronType);
		return Mono.just(Boolean.TRUE);
	}
}
