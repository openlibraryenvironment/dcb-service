package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.parseInt;
import static java.util.Calendar.YEAR;
import static java.util.Objects.nonNull;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_MISSING;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_OFFSITE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_ON_HOLDSHELF;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_RECEIVED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_REQUESTED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_RETURNED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.core.interaction.HostLmsItem.LIBRARY_USE_ONLY;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.booleanPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.integerPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.utils.DCBStringUtilities.deRestify;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.interaction.sierra.QueryEntry.buildPatronQuery;
import static services.k_int.interaction.sierra.items.SierraItem.SIERRA_ITEM_FIELDS;
import static services.k_int.utils.MapUtils.getAsOptionalString;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.BranchRecord;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.configuration.LocationRecord;
import org.olf.dcb.configuration.PickupLocationRecord;
import org.olf.dcb.configuration.RefdataRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition.IntegerHostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.MultipleVirtualPatronsFound;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.dataimport.job.SourceRecordImportChunk;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.rules.AnnotatedObject;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.storage.RawSourceRepository;
import org.olf.dcb.tracking.model.LenderTrackingEvent;
import org.olf.dcb.tracking.model.PatronTrackingEvent;
import org.olf.dcb.tracking.model.PickupTrackingEvent;
import org.olf.dcb.tracking.model.TrackingRecord;
import org.reactivestreams.Publisher;
import org.zalando.problem.Problem;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;
import services.k_int.interaction.sierra.CheckoutEntry;
import services.k_int.interaction.sierra.CheckoutResultSet;
import services.k_int.interaction.sierra.DateTimeRange;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.QueryResultSet;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.VarField;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.interaction.sierra.bibs.BibParams.BibParamsBuilder;
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
import services.k_int.micronaut.PublisherTransformationService;
import services.k_int.utils.UUIDUtils;


/**
 * See: <a href="https://sandbox.iii.com/iii/sierra-api/swagger/index.html">Sierra API Documentation</a>
 */
@Prototype
@Slf4j
public class SierraLmsClient implements HostLmsClient, MarcIngestSource<BibResult>, SourceRecordDataSource {
	private static final IntegerHostLmsPropertyDefinition GET_HOLDS_RETRY_ATTEMPTS_PROPERTY = integerPropertyDefinition(
		"get-holds-retry-attempts", "Number of retry attempts when getting holds for a patron", FALSE);

	private static final IntegerHostLmsPropertyDefinition GET_PLACE_HOLD_DELAY_PROPERTY = integerPropertyDefinition(
		"place-hold-delay", "Number of seconds to wait before placing a hold for a patron", FALSE);

	private static final IntegerHostLmsPropertyDefinition GET_FETCHING_HOLD_DELAY_PROPERTY = integerPropertyDefinition(
		"get-hold-delay", "Number of seconds to wait before getting a hold for a patron", FALSE);

	private static final IntegerHostLmsPropertyDefinition PAGE_SIZE_PROPERTY = integerPropertyDefinition(
		"page-size", "How many items to retrieve in each page", FALSE);

	private static final HostLmsPropertyDefinition VIRTUAL_PATRON_PIN = stringPropertyDefinition(
		"virtual-patron-pin", "Virtual patrons pin to use", FALSE);

	private static final HostLmsPropertyDefinition PATRON_SEARCH_TAG = stringPropertyDefinition(
		"patron-search-tag", "VarFieldTag to search for patron by", FALSE);

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
	private final ObjectRulesService objectRuleService;
  private final ObjectMapper objectMapper;
	private final SierraResponseErrorMatcher sierraResponseErrorMatcher = new SierraResponseErrorMatcher();

	private final Integer getHoldsRetryAttempts;
	private final SierraPatronMapper sierraPatronMapper;
	
	private final R2dbcOperations r2dbcOperations;

	public SierraLmsClient(@Parameter HostLms lms,
		HostLmsSierraApiClientFactory clientFactory,
		RawSourceRepository rawSourceRepository,
		ProcessStateService processStateService,
		ReferenceValueMappingService referenceValueMappingService,
		ConversionService conversionService,
		NumericPatronTypeMapper numericPatronTypeMapper,
		SierraItemMapper itemMapper,
		ObjectRulesService objectRuleService,
		PublisherTransformationService publisherTransformationService,
		SierraPatronMapper sierraPatronMapper, R2dbcOperations r2dbcOperations, ObjectMapper objectMapper) {

		this.lms = lms;
		this.objectMapper = objectMapper;

		this.getHoldsRetryAttempts = getGetHoldsRetryAttempts(lms.getClientConfig());
		this.itemMapper = itemMapper;

		// Get a sierra api client.
		client = clientFactory.createClientFor(lms);
		this.rawSourceRepository = rawSourceRepository;
		this.processStateService = processStateService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.conversionService = conversionService;
		this.numericPatronTypeMapper = numericPatronTypeMapper;
		this.objectRuleService = objectRuleService;
		this.sierraPatronMapper = sierraPatronMapper;
		this.r2dbcOperations = r2dbcOperations;
	}

	private Integer getGetHoldsRetryAttempts(Map<String, Object> clientConfig) {
		final var retries = GET_HOLDS_RETRY_ATTEMPTS_PROPERTY.getOptionalValueFrom(clientConfig, 25);
		log.info("Get holds retry attempts set to {} retries for HostLMS {}", retries, lms.getName());
		return retries;
	}

	private Integer getPlaceHoldDelay(Map<String, Object> clientConfig) {
		final var delay = GET_PLACE_HOLD_DELAY_PROPERTY.getOptionalValueFrom(clientConfig, 2);
		log.info("Place hold delay set to {} seconds for HostLMS {}", delay, lms.getName());
		return delay;
	}

	private Integer getFetchingHoldDelay(Map<String, Object> clientConfig) {
		final var delay = GET_FETCHING_HOLD_DELAY_PROPERTY.getOptionalValueFrom(clientConfig, 1);
		log.info("Fetching hold delay set to {} seconds for HostLMS {}", delay, lms.getName());
		return delay;
	}

	private String getVirtualPatronPin(Map<String, Object> clientConfig) {
		final var pin = VIRTUAL_PATRON_PIN.getOptionalValueFrom(clientConfig, null);
		log.info("Virtual patron pin set to {} for HostLMS {}", pin, lms.getName());
		return pin;
	}

	private String getPatronSearchTag(Map<String, Object> clientConfig) {
		final var tag = PATRON_SEARCH_TAG.getOptionalValueFrom(clientConfig, null);
		log.info("Patron search tag set to {} for HostLMS {}", tag, lms.getName());
		return tag;
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
			GET_HOLDS_RETRY_ATTEMPTS_PROPERTY,
			GET_PLACE_HOLD_DELAY_PROPERTY,
			GET_FETCHING_HOLD_DELAY_PROPERTY,
			VIRTUAL_PATRON_PIN);
	}

  public Flux<SourceRecord> getChunk(JsonNode parameters) {
  	return Flux.just( conversionService.convertRequired(parameters, BibParams.class) )
  		.flatMap( client::bibs )
  		.map( items -> conversionService.convertRequired(parameters, SourceRecord.class) );
  }
	


	@Override
	public boolean isSourceImportEnabled() {
		return this.isEnabled();
	}
	
	public BibParams mergeApiParameters(Optional<BibParams> parameters) {
		
		final int limit = 500;
		
		return parameters
			// Create builder from the existing params.
			.map( BibParams::toBuilder )
			
			// No current properties just set offset to 0
			.orElse(BibParams.builder()
					.offset(0)) // Default 0
			
			// Ensure we add the constant parameters.
			.limit(limit)
			.fields(List.of("id", "updatedDate", "createdDate", "deletedDate", "deleted", "marc", "suppressed", "fixedFields", "varFields" ))
			.build();
		
	}
	
	@Override
  public Mono<SourceRecordImportChunk> getChunk( Optional<JsonNode> checkpoint ) {
  	
		try {

			// Use the inbuilt marshalling to convert into the BibParams.
			final Optional<BibParams> optParams = checkpoint.isPresent() ? Optional.of( objectMapper.readValueFromTree(checkpoint.get(), BibParams.class) ) : Optional.empty();
			
			final BibParams apiParams = mergeApiParameters(optParams);
	  	
	  	final Instant now = Instant.now();
			return Mono.just( apiParams )
				.flatMap( params -> Mono.from( client.bibsRawResponse(params) ))
				.mapNotNull( itemPage -> itemPage.get("entries") )
				.filter( entries -> {
					if (entries.isArray()) {
						return true;
					}
					
					log.debug("[.entries] property received from sierra is not an array");
					return false;
				})
				.cast( JsonArray.class )
				.flatMap( jsonArr -> {
					
					try {
						
						boolean lastChunk = jsonArr.size() != apiParams.getLimit();
						BibParamsBuilder paramsBuilder;
						if (lastChunk) {
							LocalDateTime fromLdt = now.atZone(ZoneId.of("UTC")).toLocalDateTime();
							log.trace("Setting from date for {} to {}", lms.getName(), fromLdt);

							paramsBuilder = BibParams.builder()
								.updatedDate(DateTimeRange.builder()
										.fromDate(fromLdt)
										.build());
						} else {
							int previous_offset = apiParams.getOffset() != null ? apiParams.getOffset().intValue() : 0;
							paramsBuilder = apiParams.toBuilder().offset( previous_offset + jsonArr.size());
						}

						
						// We return the current data with the Checkpoint that will return the next chunk.
						final JsonNode newCheckpoint = objectMapper.writeValueToTree(paramsBuilder.build());
						
						final var builder = SourceRecordImportChunk.builder()
								.lastChunk( lastChunk )
								.checkpoint( newCheckpoint );
						
						jsonArr.values().forEach(rawJson -> {
							
							try {
								builder.dataEntry( SourceRecord.builder()
				  				.hostLmsId( lms.getId() )
				  				.lastFetched( now )
				  				.remoteId( rawJson.get("id").coerceStringValue() )
				  				.sourceRecordData( rawJson )
				  				.build());
			  			} catch (Throwable t) {  				
			  				if (log.isDebugEnabled()) {
			    				log.error( "Error creating SourceRecord from JSON '{}' \ncause: {}", rawJson, t);
			  				} else {
			  					log.error( "Error creating SourceRecord from JSON", t );
			  				}
			  			}
						});
						
						return Mono.just( builder.build() );
						
					} catch (Exception e) {
						return Mono.error( e );
					}
				});
		} catch (Exception e) {
			return Mono.error( e );
		}
  	
  }
	
	private Mono<BibResultSet> fetchPage(Instant since, int offset, int limit) {
		log.trace("Creating subscribable batch;  since={} offset={} limit={}", since, offset, limit);
		return Mono.from(client.bibs(params -> {
			params
				.offset(offset)
				.limit(limit)
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

					// Track the highest record timestamp we have seen so we know where to pick up next run
					for ( BibResult br : bibs ) {
						if ( br.updatedDate() != null ) {
							long ts = br.updatedDate().toInstant(ZoneOffset.UTC).toEpochMilli();
							if ( ts > state.highest_record_timestamp ) {
								log.debug("Advancing highest timestamp");
								state.highest_record_timestamp = ts;
							}
						}
					}

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
						state.storred_state.put("highest_record_timestamp", Long.valueOf(state.highest_record_timestamp).toString());

						log.info("No more results to fetch from {}", lms.getName());
						return Mono.empty();

					} else {
						log.trace("Exhausted current page from {} , prep next", lms.getName());
						// We have finished consuming a page of data, but there is more to come.
						// Remember where we got up to and stash it in the DB
						state.storred_state.put("highest_record_timestamp", Long.valueOf(state.highest_record_timestamp).toString());
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
							.doOnNext( bib -> log.debug("SEEN: [{}]", bib.id()) )
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
	
	private Mono<ObjectRuleset> _lmsBibSuppressionRuleset = null;
	private synchronized Optional<ObjectRuleset> getLmsBibSuppressionRuleset() {
		
		if (_lmsBibSuppressionRuleset == null) {
			var supSetName = lms.getSuppressionRulesetName();
			
			_lmsBibSuppressionRuleset = Mono.justOrEmpty( supSetName )
				.flatMap( name -> objectRuleService.findByName(name)
						.doOnSuccess(val -> {
							if (val == null) {
								log.warn("Host LMS [{}] specified using ruleset [{}] for bib suppression, but no ruleset with that name could be found", lms.getCode(), name);
								return;
							}
							
							log.debug("Found bib suppression ruleset [{}] for Host LMS [{}]", name, lms.getCode());
						}))
				.cache();
		}
		
		// TODO: Blocking!!!! Needs refactoring
		return  _lmsBibSuppressionRuleset.blockOptional();
	}
	
	private Mono<Optional<ObjectRuleset>> _lmsItemSuppressionRuleset = null;
	private synchronized Mono<Optional<ObjectRuleset>> getLmsItemSuppressionRuleset() {
		
		if (_lmsBibSuppressionRuleset == null) {
			var supSetName = lms.getItemSuppressionRulesetName();
			
			_lmsItemSuppressionRuleset = Mono.justOrEmpty( supSetName )
				.flatMap( name -> objectRuleService.findByName(name)
						.doOnSuccess(val -> {
							if (val == null) {
								log.warn("Host LMS [{}] specified using ruleset [{}] for item suppression, but no ruleset with that name could be found", lms.getCode(), name);
								return;
							}
							
							log.debug("Found item suppression ruleset [{}] for Host LMS [{}]", name, lms.getCode());
						}))
				.map( Optional::of )
				.defaultIfEmpty(Optional.empty())
				.cache();
		}
		return  _lmsItemSuppressionRuleset;
	}
	
	private boolean derriveBibSuppressedFlag( BibResult resource ) {

		List<String> decisionLog = new ArrayList<String>();
		
		if ( Boolean.TRUE.equals(resource.suppressed()) ) return true;
		
		// Grab the suppression rules set against the Host Lms
		// False is the default value for suppression if we can't find the named ruleset
		// or if there isn't one.
		return getLmsBibSuppressionRuleset()
		  .map( rules -> rules.negate().test(new AnnotatedObject(resource, decisionLog)) ) // Negate as the rules evaluate "true" for inclusion
		  .orElse(FALSE);
	}

	@Override
	public IngestRecordBuilder initIngestRecordBuilder(BibResult resource) {

		// Use the host LMS as the
		IngestRecordBuilder irb = IngestRecord.builder().uuid(uuid5ForBibResult(resource))
			.sourceSystem(lms)
			.sourceRecordId(resource.id())
			.suppressFromDiscovery(derriveBibSuppressedFlag(resource))
			.deleted(resource.deleted());

		// log.info("resource id {}",resource.id());

		// If fixedField.get(26) - it's a map with int keys - contains a string "MULTI" then the bib is held at multiple locations
		if ( resource.fixedFields() != null ) {
			FixedField location = resource.fixedFields().get(26);
			// log.info("Got location {}",location);
			if ( ( location != null ) && ( location.getValue() != null ) ) {
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
							.owningLocations(List.of(cic.getLocationCode()))
							.fixedFields(fixedFields).build()));
			})
			.doOnSuccess(result -> log.debug("the result of createItem({})", result))
			.map(result -> deRestify(result.getLink()))
			// .map(result -> deRestify(result.getLink())).map(localId -> HostLmsItem.builder().localId(localId).build())
			// Try to read back the created item until we succeed or reach max retries - Sierra returns an ID but the ID is not ready for consumption
			// immediately.
      .flatMap(itemId -> Mono.defer(() -> getItem(HostLmsItem.builder().localId(itemId).build())).retry(getHoldsRetryAttempts))
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
				log.error("Unable to map item type DCB:{} to target system {}",itemTypeCode,targetSystemCode);
				return Mono.just("UNKNOWN");
			}));
		}

		log.warn("Request to map item type was missing required parameters");
		return Mono.just("UNKNOWN");
	}

	private Mono<Item> mapItemWithRuleset( SierraItem item, String localBibId ) {
		return getLmsItemSuppressionRuleset()
			.flatMap( resultSet -> itemMapper.mapResultToItem(item, lms.getCode(), localBibId, resultSet));
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
			.map(ResultSet::getItems)
			.flatMapMany(Flux::fromIterable)
			.flatMap(result -> mapItemWithRuleset(result, localBibId))
			.collectList();
	}

	public Mono<Patron> patronFind(String varFieldTag, String varFieldContent) {
		log.debug("patronFind({}, {})", varFieldTag, varFieldContent);

		return Mono.from(client.patronFind(varFieldTag, varFieldContent))
			.flatMap(this::validatePatronRecordResult)
			.flatMap(patronRecord -> sierraPatronMapper.sierraPatronToHostLmsPatron(patronRecord,
				this.lms.getCode()))
			.onErrorResume(NullPointerException.class, error -> {
				log.error("NullPointerException occurred when finding Patron: {}", error.getMessage());
				return Mono.empty();
			});
	}

	private Mono<SierraPatronRecord> validatePatronRecordResult(SierraPatronRecord result) {

		final var isNotDeletedRecord = result.getDeleted() != null ? !result.getDeleted() : TRUE;

		if (nonNull(result.getId())
			&& nonNull(result.getPatronType())
			&& isNotDeletedRecord) {

			log.info("SierraPatronRecord was validated :: id:{}", result.getId());
			return Mono.just(result);
		}

		log.warn("SierraPatronRecord was not validated :: {}", result);
		log.warn("Returning empty.");
		return Mono.empty();
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

		// Use used to required at least 4 characters for a user auth
		// if ((name == null) || (name.length() < 4)) return Mono.empty();

		// If the provided name is present in any of the names coming back from the
		// client
		return patronFind(principalField, principal)
			.doOnSuccess(patron -> log.info("Testing {}/{} to see if {} is present", patron, patron.getLocalNames(), name))
			.filter(patron -> patron.getLocalNames().stream()
				.anyMatch(s -> s.toLowerCase().trim().startsWith(name.toLowerCase().trim())));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		// Look up virtual patron using generated unique ID string
		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);

		log.debug("findVirtualPatron, uniqueId:{}", uniqueId);

		final var queryEntry = buildPatronQuery(uniqueId);
		final var offset = 0;
		final var limit = 10;

		return Mono.from(client.patronsQuery(offset, limit, queryEntry))
			.map(queryResultSet -> {

				final var entries = getValueOrNull(queryResultSet, QueryResultSet::getEntries);
				final var entriesSize = (entries != null) ? entries.size() : 0;

				if (entriesSize < 1) {
					log.warn("No virtual Patron found.");

					throw VirtualPatronNotFound.builder()
						.withDetail(entriesSize + " records found")
						.with("offset", offset)
						.with("limit", limit)
						.with("queryEntry", queryEntry)
						.with("Response", queryResultSet)
						.build();
				}

				if (entriesSize > 1) {
					log.error("More than one virtual patron found: {}", queryResultSet);

					throw MultipleVirtualPatronsFound.builder()
						.withDetail(entriesSize + " records found")
						.with("offset", offset)
						.with("limit", limit)
						.with("queryEntry", queryEntry)
						.with("Response", queryResultSet)
						.build();
				}

				final var localPatronId = deRestify(entries.get(0).getLink());

				if (localPatronId == null) {
					log.error("localPatronId could not be extracted from: {}", entries);

					throw Problem.builder()
						.withTitle("Virtual patron ID could not be extracted")
						.withDetail(entriesSize + " records found")
						.with("offset", offset)
						.with("limit", limit)
						.with("queryEntry", queryEntry)
						.with("Response", queryResultSet)
						.build();
				}

				log.debug("findVirtualPatron, patron id successfully extracted: {}", localPatronId);
				return localPatronId;
			})
			.flatMap(this::getPatronByLocalId);
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
			.pin(getVirtualPatronPin(getConfig()))
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
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		log.debug("{}", getHostLms().getName() + " attempting to cancel local hold " + parameters.getLocalRequestId());

		final var localRequestId = getValueOrNull(parameters, CancelHoldRequestParameters::getLocalRequestId);
		final var deleteCommand = DeleteCommand.builder().requestId(localRequestId).build();

		return deleteHold(deleteCommand).thenReturn(localRequestId);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		// This is left as a comment for now - there is some discussion as to what the best thing to do is in sierra 
		// when placing a hold. For now we revert to the original plan - set the pickup location to the 

		// When placing the hold on a suppling location, the item will be "Picked up" by the transit van at the
		// location where the item currently resides
		// return placeHoldRequest(parameters, parameters.getSupplyingLocalItemLocation());
		/*
		String pickup_location_code = parameters.getPickupLocationCode();

		// We would like pickup location to be code@agency not just agency
    if ( parameters.getPickupLocation() != null ) {
      if ( parameters.getPickupLocation().getCode() != null ) {
				pickup_location_code = parameters.getPickupLocation().getCode() + "@" + parameters.getPickupLocationCode();
			}
		}
		*/
		String pickup_location_code = parameters.getPickupNote();

		return placeHoldRequest(parameters, parameters.getPickupLocationCode(), "supplier");
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);

		final var pickupLocationCode = getValueOrNull(parameters,
			PlaceHoldRequestParameters::getPickupLocation,
			Location::getCode);

		if (pickupLocationCode == null) {
			throw new IllegalArgumentException("Pickup location cannot be null when placing pickup hold");
		}

		return placeHoldRequest(parameters, pickupLocationCode, "pickup");
	}


	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtLocalAgency({})", parameters);

		return placeHoldRequestAtBorrowingAgency(parameters);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {

		// When placing the hold at a pickup system we want to use the pickup location code as selected by
		// the patron

		// Start with the default - a fallback - but unlikely to be correct
		String pickup_location_code = parameters.getPickupLocationCode();

		// Now, look to see if we have attached the location record corresponding to a user selection. If so,
		// Sierra expects us to use the right code for the local pickup location - extract that from the code field
		// of the location record. N.B. This is different to polaris and FOLIO which use local-id because in those
		// systems, a location can have BOTH a code(e.g. "DB") and an ID(e.g. 24).
    if ( parameters.getPickupLocation() != null ) {
      if ( parameters.getPickupLocation().getCode() != null ) {
        log.debug("Overriding pickup location code with code from location record");
        pickup_location_code = parameters.getPickupLocation().getCode();
			}
    }

		return placeHoldRequest(parameters, pickup_location_code, "borrower");
	}

	private Mono<LocalRequest> placeHoldRequest(
		PlaceHoldRequestParameters parameters, String pickupLocation, String role) {
		log.debug("placeHoldRequest({},{},{})", role, pickupLocation, parameters);

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

		AtomicInteger retryCount = new AtomicInteger(0);

		// getPatronHoldRequestId completing... Either
		// we need retries or a delay.
		return Mono.just(patronHoldPost)
			// Ian: NOTE... SIERRA needs time between placeHoldRequest and
			// Allow a grace period for Sierra
			.delayElement( Duration.ofSeconds(getPlaceHoldDelay(getConfig())) )
			.flatMap(holdPost -> Mono.from(client.placeHoldRequest(parameters.getLocalPatronId(), holdPost)))
			.then(Mono.defer(() -> getPatronHoldRequestId(parameters.getLocalPatronId(),
					recordNumber, parameters.getNote(), parameters.getPatronRequestId()))
				.doOnSuccess(resp -> log.info("Full log resp of getting hold after placing in {}: {}", getHostLmsCode(), resp))
				.doOnError(e -> log.error("Full log resp of getting hold after placing in {}", getHostLmsCode(), e))
				.doOnError(e -> log.debug("Retry attempt: " + retryCount.incrementAndGet()))
				.retry(getHoldsRetryAttempts))
			.doOnSuccess(__ -> log.info("Successfully got hold after {} attempts", retryCount.get()))
			// If we were lucky enough to get back an Item ID, go fetch the barcode, otherwise this is just a bib or volume request
			.flatMap(localRequest -> addBarcodeIfItemIdPresent(localRequest) )
			.doOnError(e -> log.error("Full log resp of getting hold after placing in {}", getHostLmsCode(), e))
			.onErrorResume(NullPointerException.class, error -> {
				log.debug("NullPointerException occurred when creating Hold: {}", error.getMessage());
				return Mono.error(new RuntimeException("Error occurred when creating Hold"));
			});
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {

		log.debug("renew({})", hostLmsRenewal);

		final var patronId = hostLmsRenewal.getLocalPatronId();
		final var itemId = hostLmsRenewal.getLocalItemId();

		return Mono.from(client.getItemCheckouts(itemId))
			.map(checkoutResultSet -> chooseCheckout(checkoutResultSet, patronId))
			.flatMap(checkoutID -> Mono.from(client.renewal(checkoutID)))
			.doOnSuccess(resp -> log.info("Renewal successful in {}", getHostLmsCode()))
			.map(resp -> {
				final var respItemId = deRestify(resp.getItem());
				final var respPatronId = deRestify(resp.getPatron());
				final var itemBarcode = deRestify(resp.getBarcode());

				return HostLmsRenewal.builder()
					.localRequestId(hostLmsRenewal.getLocalRequestId())
					.localPatronId(respPatronId)
					.localItemId(respItemId)
					.localItemBarcode(itemBarcode)
					.localPatronBarcode(hostLmsRenewal.getLocalPatronBarcode())
					.build();
			})
			.doOnError(e -> log.error("Renewal failed in {}", getHostLmsCode(), e));
	}

	private String chooseCheckout(CheckoutResultSet checkoutResultSet, String patronId) {

		if (checkoutResultSet == null || checkoutResultSet.getEntries() == null) {
			throw Problem.builder()
				.withTitle("Checkout ID not found for renewal")
				.withDetail("No checkout records returned")
				.with("checkoutResultSet", checkoutResultSet)
				.build();
		}

		final var entries =  checkoutResultSet.getEntries();

		log.debug("chooseCheckout( entries:{})", entries);

		final var matchingEntries = entries.stream()
			.filter(entry -> entry.getPatron() != null)
			.filter(entry -> Objects.equals(patronId, deRestify(entry.getPatron())))
			.toList();

		// Check if a single match is found
		if (matchingEntries.size() == 1) {
			// Return the checkout ID of the single match
			final var matchingEntry = matchingEntries.get(0);
			return deRestify(matchingEntry.getId());
		} else {
			throw Problem.builder()
				.withTitle("Checkout ID not found for renewal")
				.withDetail(getDetail(entries, matchingEntries))
				.with("entries", entries)
				.build();
		}
	}

	private String getDetail(List<CheckoutEntry> entries, List<CheckoutEntry> matchingEntries) {
		if (entries.isEmpty()) {
			return "No checkout records returned";
		} else if (matchingEntries.isEmpty()) {
			return "No checkouts matching local patron id found";
		} else {
			return "Multiple checkouts matching local patron id found";
		}
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		log.info("updatePatronRequest({})", localRequest);

		final var itemId = localRequest.getRequestedItemId();
		final var barcode = localRequest.getRequestedItemBarcode();
		final var supplyingAgencyCode = localRequest.getSupplyingAgencyCode();

		return getMappedItemType(lms.getCode(), localRequest.getCanonicalItemType())
			.flatMap(itemType -> {
					log.debug("updateItem in SierraLmsClient - itemType will be {}", itemType);

					// https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
					final var fixedFields = Map.of(
						61, FixedField.builder().label("DCB-" + itemType).value(itemType).build(),
						88, FixedField.builder().label("REQUEST").value("&").build());

					final var itemPatch = ItemPatch.builder()
						.barcodes(List.of(barcode))
						.location(supplyingAgencyCode)
						.owningLocations(List.of(supplyingAgencyCode))
						.fixedFields(fixedFields)
						.build();

					return updateItem(itemId, itemPatch);
				})
			.flatMap(_itemID -> getRequest(HostLmsRequest.builder().localId(localRequest.getLocalId()).build()))
			.map(hostLmsRequest -> LocalRequest.builder()
				.localId(hostLmsRequest.getLocalId())
				.localStatus(hostLmsRequest.getStatus())
				.rawLocalStatus(hostLmsRequest.getRawStatus())
				.requestedItemId(hostLmsRequest.getRequestedItemId())
				.build())
			.doOnSuccess(returnValue -> log.info("Successfully updated patron request, returning {}.", localRequest));
	}

	private Mono<LocalRequest> addBarcodeIfItemIdPresent(LocalRequest localRequest) {
		if ( localRequest.getRequestedItemId() != null ) {
			return getItem(HostLmsItem.builder().localId(localRequest.getRequestedItemId()).localRequestId(localRequest.getLocalId()).build())
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
		log.debug("checking hold for, patronRequestId {}, hold {} ", patronRequestId, hold);
		return (hold != null) && (hold.note() != null) && (hold.note().contains(patronRequestId));
	}

	private Mono<LocalRequest> getPatronHoldRequestId(String patronLocalId,
		String localItemId, String note, String patronRequestId) {

		log.debug("getPatronHoldRequestId({}, {}, {}, {})", patronLocalId,
			localItemId, note, patronRequestId);

		return Mono.just(patronLocalId)
			// Ian: TEMPORARY WORKAROUND - Wait for sierra to process the hold and make it
			// visible
			.delayElement( Duration.ofSeconds(getFetchingHoldDelay(getConfig())) )
			.flatMap(id -> Mono.from(client.patronHolds(id)))
			.map(SierraPatronHoldResultSet::entries)
			.doOnNext(entries -> log.debug("Hold entries: {}", entries))
			.flatMapMany(Flux::fromIterable)
			.filter(hold -> shouldIncludeHold(hold, patronRequestId))
			.collectList()
			.map(filteredHolds -> chooseHold(patronLocalId, note, filteredHolds))
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
			case "m" -> HostLmsRequest.HOLD_MISSING;
			default -> code;
		};
	}

	// Informed by
	// https://techdocs.iii.com/sierraapi/Content/zObjects/holdObjectDescription.htm
	private String mapSierraItemStatusToDCBItemStatus(Status status) {
		final var statusCode = getValue(status, Status::getCode, null);
		final var dueDate = getValue(status, Status::getDuedate, null);

		if (statusCode == null) {
			return null;
		}

		return switch (statusCode) {
			case "-" -> {
				if (isNotEmpty(dueDate)) {
					log.info("Item has a due date, setting item status to LOANED");
					yield ITEM_LOANED;
				} else {
					yield ITEM_AVAILABLE;
				}
			}
			case "t" -> ITEM_TRANSIT; // IN Transit
			case "@" -> ITEM_OFFSITE;
			case "#" -> ITEM_RECEIVED;
			case "!" -> ITEM_ON_HOLDSHELF;
			case "o" -> LIBRARY_USE_ONLY;
			case "%" -> ITEM_RETURNED;
			case "m" -> ITEM_MISSING;
			case "&" -> ITEM_REQUESTED;
			default -> statusCode;
		};
	}

	private LocalRequest chooseHold(String localPatronId, String note, List<SierraPatronHold> filteredHolds) {
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
				.rawLocalStatus(sph.status().toString())
				.requestedItemId(requestedItemId)
				.build();

		} else if (filteredHolds.size() > 1) {
			throw new RuntimeException("Multiple hold requests found for the given note: " + note);
		} else {
			throw Problem.builder()
				.withTitle("No holds to process for local patron id: " + localPatronId)
				.withDetail("Match attempted : note %s".formatted(note))
				.with("filteredHolds", filteredHolds)
				.build();
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

		if (safeParseLong(localPatronId) == null) {
			log.error("GUARD CLAUSE : localPatronId '{}' cannot be parsed as long", localPatronId);

			return Mono.error(patronNotFound(localPatronId, getHostLmsCode()));
		}

		return Mono.from(client.getPatron(Long.valueOf(localPatronId)))
			.flatMap(patronRecord -> sierraPatronMapper.sierraPatronToHostLmsPatron(patronRecord,
				this.lms.getCode()))
			.switchIfEmpty(Mono.error(patronNotFound(localPatronId, getHostLmsCode())));
	}

	private Long safeParseLong(String str) {
		try {
			return Long.parseLong(str);
		} catch (NumberFormatException e) {
			log.debug("NumberFormatException caught for string {} returning null", str);
			return null;
		}
	}

	public Mono<Patron> getPatronByIdentifier(String identifier) {
		log.debug("getPatronByIdentifier({})", identifier);

		final var tag = getPatronSearchTag(getConfig());

		// When the tag has not been set in the Host LMS for patron search we default to finding patron by local ID
		if (isEmpty(tag)) {
			log.warn("getPatronByIdentifier, no \"{}\" configuration value found", PATRON_SEARCH_TAG.getName());
			log.info("getPatronByIdentifier, using localId: {}", identifier);

			return getPatronByLocalId(identifier)
				.switchIfEmpty(Mono.error(patronNotFound(identifier, getHostLmsCode())));
		}

		log.info("getPatronByIdentifier, identifier: {} tag: {}", identifier, tag);
		return patronFind(tag, identifier)
			.switchIfEmpty(Mono.error(patronNotFound(identifier, getHostLmsCode())));
	}

	public Mono<Patron> getPatronByUsername(String username) {
		log.debug("getPatronByUsername({})", username);
		// This is complicated because in sierra, users log on by different fields - some systems are
		// configured to have users log in by barcode, and others use uniqueId. Here we're going to try a
		// belt and braces approach and look up by UniqueId first, then Barcode
		return patronFind("u", username)
			.switchIfEmpty(Mono.defer(() -> patronFind("b", username)));
	}

	@Override
	public Mono<Patron> updatePatron(String localPatronId, String patronType) {
		log.debug("updatePatron localPatronId {} patronType {}", localPatronId, patronType);

		final var parsedPatronType = parseInt(patronType);
		PatronPatch patronPatch = PatronPatch.builder().patronType(parsedPatronType).build();

		return Mono.from(client.updatePatron(Long.valueOf(localPatronId), patronPatch))
			.doOnSuccess(__ -> log.info("Patron successfully updated."))
			.then(Mono.defer(() -> getPatronByLocalId(localPatronId)))
			.doOnError(error -> log.error("Error updating patron: {}", error.getMessage()));
	}

	public Mono<HostLmsRequest> sierraPatronHoldToHostLmsHold(SierraPatronHold sierraHold) {
		log.debug("sierraHoldToHostLmsHold({})", sierraHold);

		if ((sierraHold == null) || (sierraHold.id() == null)) {
			return Mono.just(new HostLmsRequest());
		}

		final var holdId = deRestify(sierraHold.id());

		final var requestedItemId = (sierraHold.recordType() != null) && (sierraHold.recordType().equals("i"))
			? deRestify(sierraHold.record())
			: null;

		// Map the hold status into a canonical value
		final var status = sierraHold.status() != null
			? mapSierraHoldStatusToDCBHoldStatus(sierraHold.status().code(), requestedItemId)
			: "";

		final var rawStatus = sierraHold.status() != null ? sierraHold.status().toString() : null;

		if (requestedItemId == null) {
			return Mono.just(HostLmsRequest.builder().localId(holdId).status(status).rawStatus(rawStatus).build());
		}

		return getItem(HostLmsItem.builder().localId(requestedItemId).localRequestId(holdId).build())
			.map(item -> HostLmsRequest.builder().localId(holdId)
				.status(status).rawStatus(rawStatus)
				.requestedItemId(requestedItemId).requestedItemBarcode(item.getBarcode())
				.build());
	}

	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {

		final var localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);

		log.debug("getRequest({})", localRequestId);

		return parseLocalRequestId(localRequestId)
			.flatMap(id -> Mono.from(client.getHold(id)))
			.flatMap(this::sierraPatronHoldToHostLmsHold)
			.defaultIfEmpty(new HostLmsRequest(localRequestId, "MISSING"));
	}

	private Mono<Long> parseLocalRequestId(String localRequestId) {
		try {
			Long parsedLocalRequestId = Long.valueOf(localRequestId);
			return Mono.just(parsedLocalRequestId);
		} catch (NumberFormatException e) {
			return Mono.error(new NumberFormatException("Cannot convert localRequestId: " + localRequestId + " to a Long type."));
		} catch (NullPointerException e) {
			return Mono.error(new NullPointerException("Cannot use null localRequestId to fetch local request."));
		}
	}

	// II: We need to talk about this in a review session
	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		log.debug("updateItemStatus({},{})", itemId, crs);

		// Guard clause for NullPointerException DCB-1398
		if (CanonicalItemState.COMPLETED.equals(crs)) {
			log.info("GUARD CLAUSE : " + getName() + " cannot update item status for CanonicalItemState.COMPLETED");

			return Mono.just("OK");
		}

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
					.content("") // 2024-03-09 trying "" instead of "-" to clear the message
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
			return Mono.error(new NullPointerException("Could not update item "+itemId+" status for hostlms: "+getHostLms().getName()));
		}
	}

	public Mono<String> updateItem(String itemId,ItemPatch itemPatch) {

		return Mono.from(client.updateItem(itemId, itemPatch))
			.doOnSuccess(__ -> log.info("Item successfully updated."))
			.doOnError(error -> log.error("Error updating item: {}", error.getMessage()))
			.thenReturn(itemId);
	}

	private HostLmsItem sierraItemToHostLmsItem(SierraItem item) {
		log.debug("convert {} to HostLmsItem", item);

		final var status = item.getStatus();

		if ((status == null) || (item.getBarcode() == null) || (item.getId() == null)) {
			log.warn("Detected a sierra item with null status: {}", item);
		}

		final var deleted = getValue(item, SierraItem::getDeleted, false);

		final var resolvedStatus = status != null
			? mapSierraItemStatusToDCBItemStatus(status)
			: (deleted ? "MISSING" : "UNKNOWN");

		final var renewalCount = determineLocalRenewalCount(item.getFixedFields());

		return HostLmsItem.builder()
			.localId(item.getId())
			.barcode(item.getBarcode())
			.status(resolvedStatus)
			.rawStatus(getValue(status, Status::getCode, null))
			.renewalCount(renewalCount)
			.holdCount(item.getHoldCount())
			.build();
	}

	private int determineLocalRenewalCount(Map<Integer, FixedField> fixedFields) {
		// The number of times the item has been renewed by the patron who currently has the item checked out.
		// https://documentation.iii.com/sierrahelp/Default.htm#sril/sril_records_fixed_field_types_item.html
		final var FIXED_FIELD_71 = 71;

		log.info("Attempting to determine renewal count from: {}", fixedFields);

		int localRenewalCount = 0;

		if (fixedFields != null) {
			final var fixedField71 = fixedFields.get(FIXED_FIELD_71);
			if (fixedField71 != null) {
				Object fieldValue = fixedField71.getValue();
				if ( fieldValue instanceof Integer value) {
					log.info("Found renewal counter : {} assigned to {}",fixedField71.getValue(),value);
					localRenewalCount = value;
				}
				else if ( fieldValue instanceof Long value) {
					log.info("Found renewal counter : {} assigned to {}",fixedField71.getValue(),value);
					localRenewalCount = value.intValue();
				}
				else if ( fieldValue instanceof String value) {
					log.info("Found renewal counter : {} assigned to {}",fixedField71.getValue(),value);
					localRenewalCount = Integer.parseInt(value);
				}
				else {
					log.warn("fixedField71 did not contain an Integer: {}",fieldValue.getClass().getName());
				}
			}
			else {
				log.warn("GET fixedField71 returned null");
			}
		}

		return localRenewalCount;
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {

		final var localItemId = parseLocalItemId(hostLmsItem.getLocalId());
		final var localRequestId = hostLmsItem.getLocalRequestId();

		log.debug("getItem({}, {})", localItemId, localRequestId);

		return Mono.from(client.getItem(localItemId, String.join(",", SIERRA_ITEM_FIELDS)))
			.flatMap(sierraItem -> Mono.just(sierraItemToHostLmsItem(sierraItem)))
			.defaultIfEmpty(HostLmsItem.builder()
				.localId(localItemId)
				.status("MISSING")
				.build());
	}

	private String parseLocalItemId(String localItemId) {
		if (localItemId == null) {
			throw new NullPointerException("Cannot use null localItemId to fetch local item.");
		}
		return localItemId;
	}

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkout) {

		final var itemId = getValueOrNull(checkout, CheckoutItemCommand::getItemId);
		final var localRequestId = getValueOrNull(checkout, CheckoutItemCommand::getLocalRequestId);
		final var patronBarcode = getValueOrNull(checkout, CheckoutItemCommand::getPatronBarcode);
		final var virtualPatronPin = getVirtualPatronPin(getConfig());

		if (itemId == null) {
			return Mono.error(new MissingParameterException("itemId"));
		}

		if (localRequestId == null) {
			log.warn("checkOutItemToPatron: localRequestId is null");
		}

		if (patronBarcode == null || patronBarcode.isEmpty()) {
			return Mono.error(new MissingParameterException("patronBarcode"));
		}

		if (virtualPatronPin == null) {
			log.warn("Virtual patron pin not configured for HostLMS {}", lms.getCode());
		}

		log.debug("checkOutItemToPatron({},{})", itemId, patronBarcode);

		return updateItemStatus(itemId, AVAILABLE, localRequestId)
			.flatMap(ok -> {

				final var itemBarcode = getValueOrNull(checkout, CheckoutItemCommand::getItemBarcode);

				if (itemBarcode != null) {
					log.info("checkOutItemToPatron: Item barcode already known '{}'", itemBarcode);

					return Mono.just(itemBarcode);
				}

				log.debug("checkOutItemToPatron: Item barcode not known");
				return getItem(HostLmsItem.builder().localId(itemId).localRequestId(localRequestId).build()).map(HostLmsItem::getBarcode);

			})
			// Sierra checkout operation uses patron barcode and item barcode
			.flatMap(itemBarcode -> Mono.from(client.checkOutItemToPatron(itemBarcode, patronBarcode, virtualPatronPin)))
			.thenReturn("OK")
			.switchIfEmpty(Mono.error(() ->
				new RuntimeException("Check out of " + itemId + " to " + patronBarcode + " at " + lms.getCode() + " failed")));
	}

	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {
		final var id = getValueOrNull(deleteCommand, DeleteCommand::getItemId);

		log.debug("deleteItem({})", id);

		return Mono.from(client.deleteItem(id)).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
	public Mono<String> deleteBib(String id) {
		log.debug("deleteBib({})", id);

		return Mono.from(client.deleteBib(id)).thenReturn("OK").defaultIfEmpty("ERROR");
	}

	@Override
  public Mono<String> deleteHold(DeleteCommand deleteCommand) {

		final var id = getValueOrNull(deleteCommand, DeleteCommand::getRequestId);

		log.debug("deleteHold({})", id);

		return Mono.from(client.deleteHold(id)).thenReturn("OK").defaultIfEmpty("ERROR");
	}

  public Mono<String> deletePatron(String id) {
		log.info("Sierra delete patron {}",id);
		return Mono.from(client.deletePatron(id)).thenReturn("OK").defaultIfEmpty("ERROR");
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

		// If we have a recorded highest record timestampe, reconstitute it here
		String highest_record_timestamp_as_str = (String) current_state.get("highest_record_timestamp");
		if ( highest_record_timestamp_as_str != null ) {
			generator_state.highest_record_timestamp = Long.parseLong(highest_record_timestamp_as_str);
		}

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

	@Override
	public @NonNull String getClientId() {
		
		// Uri "toString" behaviour will sometimes return the string provided at initialization.
		// While this is OK for general operation, we need to compare values here. Resolving a relative URI
		// will force the toString method to construct a new string representation, meaning it's more comparable.
		return client.getRootUri().toString();
	}

	public R2dbcOperations getR2dbcOperations() {
		return r2dbcOperations;
	}

	@Override
	@NonNull
	public BibResult convertSourceToInternalType(@NonNull SourceRecord source) {
		return conversionService.convertRequired(source.getSourceRecordData(), BibResult.class);
	}

  @Override
  public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		// Item fixed field 71 contains the renewal count https://techdocs.iii.com/sierraapi/Content/zObjects/itemObjectDescription.htm?Highlight=renew
		log.info("Sierra prevent renewal {}",prc);

		if ( prc.getItemId() == null )
			throw new RuntimeException("Prevent Renewal command had null itemId "+prc.toString());

		final var fixedFields = Map.of(
            71, FixedField.builder().label("No. of Renewals").value("255").build());
            // 75, FixedField.builder().label("Recall date").value("&").build());

		final var itemPatch = ItemPatch.builder()
            .fixedFields(fixedFields)
            .build();

		return updateItem(prc.getItemId(), itemPatch)
			.then();
  }

  public Mono<PingResponse> ping() {
		Instant start = Instant.now();
		return Mono.from(client.getTokenInfo())
			.flatMap( tokenInfo -> {
	  	  return Mono.just(PingResponse.builder()
  		 	  .target(getHostLmsCode())
    		  .status("OK")
					.versionInfo(getHostSystemType()+":"+getHostSystemVersion())
      		.pingTime(Duration.between(start, Instant.now()))
  	    	.build());
			})
			.onErrorResume( e -> {
	  	  return Mono.just(PingResponse.builder()
  		 	  .target(getHostLmsCode())
    		  .status("ERROR")
					.versionInfo(getHostSystemType()+":"+getHostSystemVersion())
					.additional(e.getMessage())
      		.pingTime(Duration.ofMillis(0))
  	    	.build());
			})

		;
  }

  public String getHostSystemType() {
    return "SIERRA";
  }

  public String getHostSystemVersion() {
    return "v1";
  }

  public String getHostLmsCode() {
    String result = lms.getCode();
    if ( result == null ) {
      log.warn("getCode from hostLms returned NULL : {}",lms);
    }
    return result;
  }

}
