package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Integer.parseInt;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.marc4j.marc.Record;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.dataimport.job.SourceRecordImportChunk;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.marc.MarcIngestSource;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonArray;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import services.k_int.interaction.oaipmh.ListRecordsParams;
import services.k_int.interaction.oaipmh.ListRecordsParams.ListRecordsParamsBuilder;
import services.k_int.interaction.oaipmh.ListRecordsResponse;
import services.k_int.interaction.oaipmh.OaiRecord;
import services.k_int.interaction.oaipmh.OaiRecord.Metadata;
import services.k_int.interaction.oaipmh.Response;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

@Prototype
public class FolioOaiPmhIngestSource implements MarcIngestSource<OaiRecord>, SourceRecordDataSource {
	private static final String CONCURRENCY_GROUP_KEY = "folio-oai";
	
	private static final String CONFIG_API_KEY = "apikey";

	private static final String CONFIG_METADATA_PREFIX = "metadata-prefix";

	private static final String CONFIG_RECORD_SYNTAX = "record-syntax";

	private static final String PARAM_METADATA_PREFIX = "metadataPrefix";

	private static final String PARAM_RECORD_SYNTAX = "recordSyntax";

	private static final Logger log = LoggerFactory.getLogger(FolioOaiPmhIngestSource.class);
	
	private static final String UUID5_PREFIX = "ingest-source:folio-oai";

	private static final String CLIENT_BASE_URL = "base-url";

	private final RawSourceRepository rawSourceRepository;

	private final HostLms lms;
	private final HttpClient client;

	private final URI rootUri;

	private final ConversionService conversionService;
	
	private final String recordSyntax;
	
	private final String metadataPrefix;
	
	private final String apiKey;

	private final ProcessStateService processStateService;
	
	private final R2dbcOperations r2dbcOperations;
	
	private final ObjectMapper objectMapper;

  @Override
  public String getConcurrencyGroupKey() {
  	return CONCURRENCY_GROUP_KEY;
  }

	public FolioOaiPmhIngestSource(@Parameter("hostLms") HostLms hostLms, 
		RawSourceRepository rawSourceRepository, 
		HttpClient client, 
		ConversionService conversionService, 
		ProcessStateService processStateService, R2dbcOperations r2dbcOperations, ObjectMapper objectMapper) {

		this.lms = hostLms;
		this.rawSourceRepository = rawSourceRepository;
		this.client = client;
		
		rootUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
		
		this.conversionService = conversionService;
		this.processStateService = processStateService;
		this.objectMapper = objectMapper;
		
		recordSyntax = MapUtils.getAsOptionalString(
			lms.getClientConfig(), CONFIG_RECORD_SYNTAX).get();
		
		metadataPrefix = MapUtils.getAsOptionalString(
			lms.getClientConfig(), CONFIG_METADATA_PREFIX).get();
		
		apiKey = MapUtils.getAsOptionalString(
			lms.getClientConfig(), CONFIG_API_KEY).get();
		this.r2dbcOperations = r2dbcOperations;
	}
	
	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
	}

	@Override
	public RawSourceRepository getRawSourceRepository() {
		return rawSourceRepository;
	}

	@Override
	public Record resourceToMarc(OaiRecord resource) {
    if ( resource.metadata() == null )
      return null;

		return resource.metadata().record();
	}
	
	public UUID uuid5ForOAIResult(@NotNull final OaiRecord result) {
		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.header().identifier();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public IngestRecordBuilder initIngestRecordBuilder(OaiRecord resource) {
		final var oaiIdentifier = resource.header().identifier();

		if (!oaiIdentifier.contains("/")) {
			log.warn("OAI identifier \"" + oaiIdentifier + "\" does not contain a forward slash");

			// This return value should be used in a Mono.justOrEmpty to lead to an empty mono
			return null;
		}

		final var splitByForwardSlash = oaiIdentifier.split("/");

		final var instanceId = splitByForwardSlash[splitByForwardSlash.length - 1];

		return IngestRecord.builder()
			.uuid(uuid5ForOAIResult(resource))
			.sourceSystem(lms)
			.sourceRecordId(instanceId);
	}

	@Override
	public Publisher<OaiRecord> getResources(Instant since, Publisher<String> terminator) {
		return Flux.from(pageAllResults(terminator))
			.filter(rec -> Optional.ofNullable(rec)
				.map(OaiRecord::metadata)
				.map(Metadata::record)
				.isPresent())
			.onErrorResume(t -> {
				log.error("Error ingesting data from {}/{} : {}",  lms.getCode(), rootUri, t.getMessage());
				t.printStackTrace();
				return Mono.empty();
			}).switchIfEmpty(Mono.fromCallable(() -> {
				log.info("No results returned {}/{}. Stopping", lms.getCode(),rootUri);
				return null;
		}));
	}
		
	private Mono<ListRecordsResponse> handleErrors ( Mono<Response> stream ) {
		
		return stream.flatMap( resp -> {
			var error = resp.error();
			if (error != null) {
				return switch( error.code() ) {
					case noRecordsMatch -> Mono.just( new ListRecordsResponse(null, Collections.emptyList()) );
					case badResumptionToken -> {
						final String message = error.detail() != null ? String.format("%s: %s", error.code(), error.detail()) : error.code().toString();
						yield Mono.error(new OaiResumptionTokenError(message));
					}
					
					default -> {
						final String message = error.detail() != null ? String.format("%s: %s", error.code(), error.detail()) : error.code().toString();
						yield Mono.error(new DcbError(message));
					}
				};
			}
			
			return Mono.justOrEmpty( resp.listRecords() );
		});
	}
	
	
	private Publisher<OaiRecord> pageAllResults(Publisher<String> terminator) {

		return Mono.from( getInitialState(lms.getId(), "ingest") )
			.map(state -> state.toBuilder().build())
			.zipWhen(state -> {
				if ( state.since == null) {
					return fetchPage(null,
							MapUtils.getAsOptionalString(state.storred_state, "resumptionToken") );
				}
				return fetchPage( state.since, Optional.empty() );
			})
			.expand(TupleUtils.function((state, response) -> {
				
				var records = response.records();
				log.info("Fetched a chunk of {} records for {}", records.size(), lms.getName());
				
				final String resumptionToken = response.resumptionToken();
				
				if ( ( resumptionToken == null ) || ( resumptionToken.length() == 0 ) ) {

					log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(), state.request_start_time);
	
					// Make a note of the time at which we started this run, so we know where
					// to pick up from next time
					state.storred_state.put("cursor", "deltaSince:" + state.request_start_time);
					state.storred_state.put("name", lms.getName());
					
					state.storred_state.remove("resumptionToken");
	
					log.info("No more results to fetch from FOLIO OAI at {} - saving state as {}", lms.getName(), state);
					
					// Need to ensure we store the new state here.
					
					return Mono.defer(() -> saveState(lms.getId(), "ingest", state))
						.flatMap(_s -> {
							log.debug("Ensuring removedresumption token is saved...{}",_s);
							return Mono.empty();
						});
				} else {
					log.info("Exhausted current page from {} , prep next", lms.getName());
					// We have finished consuming a page of data, but there is more to come.
					// Remember where we got up to and stash it in the DB
					if (state.since != null) {
						state.storred_state.put("cursor", "deltaSince:" + state.sinceMillis + ":" + state.offset);
					} else {
						state.storred_state.put("cursor", "bootstrap:" + state.offset);
					}
					state.storred_state.put("resumptionToken", resumptionToken);
				}

				log.info("fetching next page of FOLIO OAI data {}",state);

				return Mono.just(state.toBuilder().build())
					.zipWhen(updatedState -> fetchPage(updatedState.since, MapUtils.getAsOptionalString(updatedState.storred_state, "resumptionToken")));
			}))

			.takeUntilOther( Mono.from(terminator)
				.doOnNext( reason -> log.info("Ejecting from collect sequence. Reason: {}", reason) ))
			
			.concatMap(TupleUtils.function((state, response) -> {
				

				// Concatenate with the state so we can propagate signals from the save
				// operation.
				return Flux.fromIterable(response.records())
					.concatWith(Mono.defer(() -> saveState(lms.getId(), "ingest", state))
						.flatMap(_s -> {
							log.debug("Updating state...");
							return Mono.empty();
						}))

				.doOnComplete(() -> log.debug("Consumed {} items", response.records().size()));
			}));
	}

	
	private <T> Mono<T> get(String path, Argument<T> argumentType,
		Consumer<UriBuilder> uriBuilderConsumer) {

		return createRequest(GET, path)
      .doOnNext(req -> log.info("fetch using {}",req.getUri()) )
			.map(req -> req
					.header(HttpHeaders.AUTHORIZATION, apiKey)
					.uri(uriBuilderConsumer))
			.flatMap(req -> doRetrieve(req, argumentType));
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}

	private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String path) {
		return Mono.just(UriBuilder.of(path).build())
			.map(this::resolve)
			.doOnNext(resolvedUri -> log.info("Fetching records from: \"{}\"", resolvedUri))
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString())
				.accept(APPLICATION_JSON));
	}

	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request, Argument<T> argumentType) {
		return doRetrieve(request, argumentType, true);
	}

//	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Class<T> type) {
//		return Mono.from(client.exchange(request, Argument.of(type))); //, ERROR_TYPE))
//	}

	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request, Argument<T> argumentType, boolean mapErrors) {
		
		request.accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML);
		
		var response = Mono.from(client.retrieve(request, argumentType)); //, ERROR_TYPE));

		return response;
	}
	
	@Override
	@NotNull
	public String getDefaultControlIdNamespace() {
		return lms.getName();
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		return Flux.empty();
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	public UUID uuid5ForRawJson(@NotNull final OaiRecord result) {

		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":raw:" + result.header().identifier();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}
	
	// This should convert whatever type the FOLIO source returns to a RawSource
	@Override
	public RawSource resourceToRawSource(OaiRecord resource) {

		final JsonNode rawJson = conversionService.convertRequired(resource.metadata().record(), JsonNode.class);

		@SuppressWarnings("unchecked")
		final Map<String, ?> rawJsonString = conversionService.convertRequired(rawJson, Map.class);

		RawSource raw = RawSource.builder().id(uuid5ForRawJson(resource)).hostLmsId(lms.getId())
				.remoteId(resource.header().identifier()).json(rawJsonString).build();

		return raw;
	}

	@Override
	public ProcessStateService getProcessStateService() {
		return this.processStateService;
	}

	@Override
	public PublisherState mapToPublisherState(Map<String, Object> current_state) {
		PublisherState generator_state = new PublisherState(current_state);
		log.info("state=" + current_state + " lmsid=" + lms.getId() + " thread="
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

	// @Transactional
	@Override
	public Mono<PublisherState> saveState(@NonNull UUID id, @NonNull String processName, @NonNull PublisherState state) {
		log.debug("savState {} {} - {}", id, state, lms.getName());

		// return Mono.from(processStateService.updateState(id, processName, state.storred_state)).thenReturn(state);
		return processStateService.updateState(id, processName, state.storred_state)
			.thenReturn(state);
	}

	public R2dbcOperations getR2dbcOperations() {
		return r2dbcOperations;
	}
	
	protected Mono<ListRecordsResponse> fetchPage(Instant since, Optional<String> resumptionToken) {
		log.info("Creating subscribeable batch;  root={}/{} since={}, resumptionToken={}",  lms.getCode(), rootUri, since, resumptionToken);
	
		return Mono.from(this.get("/oai", Argument.of( Response.class ), params -> {
			
			params.queryParam("verb", "ListRecords");
			resumptionToken
				.filter(StringUtils::hasText)
				.ifPresentOrElse(val -> {
					params.queryParam("resumptionToken", val);
				}, () -> {
					
					// Exclude when resumption token present.
					if (since != null) {
						params.queryParam("from", since.truncatedTo(ChronoUnit.SECONDS).toString());
					}
	
					params
						.queryParam(PARAM_RECORD_SYNTAX, recordSyntax)
						.queryParam(PARAM_METADATA_PREFIX, metadataPrefix);
	
				});

			log.info("Final FOLIO OAI params:{}",params);
			
		}))
		.transform( this::handleErrors )
		.onErrorResume( OaiResumptionTokenError.class , re -> {
			// If we have a retryable state and a resumption token.... Then recursively return this,
			// But without a token.
			if (resumptionToken.isPresent()) {
				
				log.atInfo().log("Retrying without a resumption token and a 'since' of [{}]", since);
				return fetchPage(since, Optional.empty());
			}
			
			// Else bubble it up.
			return Mono.error(re);
		})
		.doOnSubscribe(_s -> log.info("Fetching batch from Folio OAI PMH {} with since={} resumptionToken={}", lms.getName(), since, resumptionToken));
	}
	
	@Override
	public boolean isSourceImportEnabled() {
		return isEnabled();
	}
	
	public Consumer<UriBuilder> mergeApiParameters(Optional<ListRecordsParams> parameters) {
		
		final ListRecordsParams lrParams = parameters.map( ListRecordsParams::toBuilder )
			.orElse( ListRecordsParams.builder() )
			.metadataPrefix(metadataPrefix)
			.build();
		
		return params -> {
			
			// Add the verb
			params.queryParam("verb", lrParams.getVerb());
			
			// The resumption token is exclusive when present.
			final String resumptionToken = lrParams.getResumptionToken();
			
			if (StringUtils.hasText(resumptionToken)) {
				params.queryParam("resumptionToken", resumptionToken);
				log.debug("Final FOLIO OAI params [{}]", params);
				return;
			}
			
			// No resumption token.
			Optional.ofNullable( lrParams.getFrom() )
				.ifPresent( from -> params.queryParam("from", from.truncatedTo(ChronoUnit.SECONDS).toString()));
			
			Optional.ofNullable( lrParams.getUntil() )
			.ifPresent( until -> params.queryParam("until", until.truncatedTo(ChronoUnit.SECONDS).toString()));

			// Always add the record syntax custom parameter.
			params
				.queryParam(PARAM_METADATA_PREFIX, lrParams.getMetadataPrefix())
				.queryParam(PARAM_RECORD_SYNTAX, recordSyntax);
			
			log.debug("Final FOLIO OAI params [{}]", params);
			
		};
	}
	
	private Mono<ListRecordsResponse> listRecords ( Optional<ListRecordsParams> params ) {
		
		final Consumer<UriBuilder> apiParams = mergeApiParameters(params);
  	return Mono.from(get("/oai", Argument.of( Response.class ), apiParams))
			.transform( this::handleErrors )
			.onErrorResume( OaiResumptionTokenError.class , re -> {
				
				// No token.
				if (params.map( ListRecordsParams::getResumptionToken )
						.filter( StringUtils::hasText )
						.isEmpty()) {
					return Mono.error(re);
				}

				
				log.info("Retrying without a resumption token, params [{}]", params.map(Objects::toString).orElse("<empty>"));
				
				// We had a token so this error is valid. Retry without.
				return listRecords( params.map( p -> p.toBuilder().resumptionToken(null).build() ));
			});
	}

	private Mono<JsonNode> reactiveObjectMap ( @NonNull Object obj ) {
		try {
			return Mono.justOrEmpty( objectMapper.writeValueToTree(obj) );
		} catch ( IOException e ) {
			return Mono.error( e );
		}
	}
	
	private ListRecordsParams buildNextParams(Optional<ListRecordsParams> currentParams, 
		JsonArray currentResults, 
		ListRecordsResponse fullResponse, 
		Instant fetchTime) {

		Instant highestRecordTimestampSeen = currentParams.isPresent() ? currentParams.get().getHighestRecordTimestampSeen() : Instant.ofEpochSecond(0);

		// Record the highest record timestamp seen, as a better way to resume next time.
		if ( fullResponse != null ) {
			if ( ( fullResponse.records() != null ) && ( !fullResponse.records().isEmpty() ) ) {
				OaiRecord or = fullResponse.records().get(fullResponse.records().size()-1);
				if ( ( or != null ) && ( or.header().datestamp().isAfter(highestRecordTimestampSeen) ) )
					highestRecordTimestampSeen = or.header().datestamp();
			}
		}

	  // Always preserve the "start" params even if we have a resumption. This allows for better handling
		// if the token expires during a harvest.
		ListRecordsParamsBuilder lrp = currentParams
				.map(ListRecordsParams::toBuilder)
				.orElseGet(ListRecordsParams::builder);
		
		final boolean emptyResultSet = currentResults.size() < 1;
		
		// Derive resumption token taking into account empty string and empty result sets. 
		final String resToken = emptyResultSet ? null : Optional.ofNullable( fullResponse.resumptionToken() )
				.filter(StringUtils::hasText)
				.orElse(null);
		
		if (resToken == null) {
			// II: I really don't like this - it's subject to clock skew and all sorts of crap - much better to keep the highest
			// timestamp seen from this source and use that as a from date.
			lrp = lrp.from(fetchTime); // Set from to "now" to perform delta.
		}
		
		// Add the token to the builder (possibly nulling out)
		return lrp.resumptionToken(resToken)
			.cpType("FOLIO")
			.hostCode(lms.getCode())
			.highestRecordTimestampSeen(highestRecordTimestampSeen)
			.build();
	}
	
	@Override
	public Mono<SourceRecordImportChunk> getChunk(Optional<JsonNode> checkpoint) {
		try {

			// Use the inbuilt marshalling to convert into the BibParams.
			final Optional<ListRecordsParams> optParams = checkpoint.isPresent() ? Optional.of( objectMapper.readValueFromTree(checkpoint.get(), ListRecordsParams.class) ) : Optional.empty();
			
	  	final Instant now = Instant.now();
	  	
	  	return Mono.just(optParams)
	  		.flatMap(this::listRecords)
	  		.flatMap( lrr -> {
	  			
	  			return Mono.just(lrr)
	  				.mapNotNull( ListRecordsResponse::records )
			  		.flatMap( this::reactiveObjectMap )
			  		.filter( entries -> {
							if (entries.isArray()) {
								return true;
							}
							
							log.debug("[.records] property received from Folio OAI could not be parsed as JSON array");
							return false;
						})
			  		.cast( JsonArray.class )
			  		
						.flatMap(jsonArr -> {
							
							final var nextParams = buildNextParams(optParams, jsonArr, lrr, now);
							final boolean isLastChunk = Optional.ofNullable(nextParams)
								.map(ListRecordsParams::getResumptionToken)
								.isEmpty();
							
							// Create chunk builder and set last chunk marker.
							final var builder = SourceRecordImportChunk.builder()
									.lastChunk( isLastChunk );
							
							// Attempt to set the checkpoint.
							try {
								builder.checkpoint( objectMapper.writeValueToTree(nextParams) );
							} catch (IOException e) {
								log.error("Error converting to params", e);
								builder.checkpoint(null);
							}							
							
							// Add each resource.
							jsonArr.values().forEach(rawJson -> {
								
								if (log.isTraceEnabled()) {
									log.trace(rawJson.getValue().toString());
								}
								
								try {
									builder.dataEntry( SourceRecord.builder()
					  				.hostLmsId( lms.getId() )
					  				.lastFetched( now )
					  				.remoteId( rawJson.get("header").get("identifier").coerceStringValue() )
					  				.sourceRecordData( rawJson )
					  				.build());
				  			} catch (Throwable t) {
				  				if (log.isDebugEnabled()) {
				    				log.error( "Error creating SourceRecord from JSON '{}' \n{}", rawJson.getValue(), t);
				  				} else {
				  					log.error( "Error creating SourceRecord from JSON", t.getMessage() );
				  				}
				  			}
							});

							return Mono.just( builder.build() ); 
						});
	  		});
		} catch (Exception e) {
			return Mono.error( e );
		}
	}

	@Override
	public OaiRecord convertSourceToInternalType(SourceRecord source) {
		
		JsonNode oaiSrc = source.getSourceRecordData();
		
		if (oaiSrc == null) {
			return null;
		}
		
		return conversionService.convertRequired(oaiSrc, OaiRecord.class);
	}
}
