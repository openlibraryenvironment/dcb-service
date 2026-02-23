package org.olf.dcb.indexing.elasticsearch;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.indexing.SharedIndexConfiguration;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.AcknowledgedResponse;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.json.stream.JsonParser;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import services.k_int.micronaut.PublisherTransformationService;

@Setter
@Requires(bean = ElasticsearchAsyncClient.class)
@Requires(bean = SharedIndexConfiguration.class)
@Singleton
public class ElasticsearchSharedIndexService extends BulkSharedIndexService {

	private final Logger log = LoggerFactory.getLogger(ElasticsearchSharedIndexService.class);
	
	private final ElasticsearchAsyncClient client;
	private final ConversionService conversionService;
	
	private final String indexName; 
	private final int indexVersion; 
	
	public ElasticsearchSharedIndexService(SharedIndexConfiguration conf, ElasticsearchAsyncClient client, ConversionService conversionService, RecordClusteringService recordClusteringService, PublisherTransformationService pubs) {
		super(recordClusteringService, pubs, conf);
		this.client = client;
		this.conversionService = conversionService;
		this.indexName = conf.name();
		if (conf.version().isEmpty()) {
			this.indexVersion = SharedIndexConfiguration.LATEST_INDEX_VERSION;
		} else {
			this.indexVersion = conf.version().get();
		}
	}
	
	@PostConstruct
	void init() {
		log.info("Using Elasticearch Indexing service");
	}
	
	@Override
	public Mono<Void> deleteAll() {
		return Mono.from( deleteIndex() )
			.doOnNext(resp -> {				
				log.atInfo().log("Delete all, by deleting index: {}", indexName);
			})
			.onErrorMap( handleErrors("Error deleting all from shared index") )
			.then();
	}
	
	private Mono<Void> createNewIndex() {
		return createIndex()
			.doOnNext(resp -> {
				log.atInfo().log("Initialized index: {}", resp.index());
			})
			.onErrorMap( handleErrors("Error initializing shared index") )
			.then();
	}
	
	private Mono<Void> updateMappings() {
		return createUpdateIndexMappings()
			.doOnNext(resp -> {
				log.atInfo().log("Initialized index: {}", realIndexName());
			})
			.onErrorMap( handleErrors("Error updating mappings for shared index") )
			.then();
	}
	
	private Mono<PutMappingResponse> createUpdateIndexMappings() {
		try {
			// Get hold of the mapper from the transport client
			JsonpMapper mapper = client._transport().jsonpMapper();
			// Now create the index
			return Mono.fromFuture(
				client.indices()
					.putMapping(getTypeMappingsUpdate(mapper)));
			
		} catch (Throwable e) {
			return Mono.error(new DcbError("Error when creating index", e));
		}
	}
	
	
	private PutMappingRequest getTypeMappingsUpdate(JsonpMapper mapper) {
  	
  	// We can't modify the properties after they're built so we have no option but to manually copy them here :(
  	final TypeMapping m = getTypeMappings(mapper);
  	
  	return PutMappingRequest.of( b -> b
  		.index(realIndexName())
  		.dateDetection(m.dateDetection())
  		.dynamic(m.dynamic())
  		.dynamicDateFormats(m.dynamicDateFormats())
  		.dynamicTemplates(m.dynamicTemplates())
  		.fieldNames(m.fieldNames())
  		.numericDetection(m.numericDetection())
  		.properties(m.properties())
  		.routing(m.routing())
  		.source(m.source()));
	}
	
	@Override
	public Mono<Void> initialize() {
		
		// Check for index.
		// If not present then create.
		return checkIndex()	
			.map(BooleanResponse::value)
			.flatMap( exists -> (exists ? updateMappings() : createNewIndex()) )
			.then(Mono.defer(() -> restoreRefresh().then() ));
	}
	
	private Function<Throwable, Throwable> handleErrors ( final String message ) {
		return ( cause ) -> new DcbException( message, cause );
	}

	private String realIndexName() {
		return(indexName + "-" + indexVersion);
	}
	
	private Mono<BooleanResponse> checkIndex() {
		try {
			return Mono.fromFuture(
				client.indices()
					.exists( ind -> ind
						.index( realIndexName() )));
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error when creating index", e) );
		}
	}
	
	private TypeMapping getTypeMappings(JsonpMapper mapper) {
		
  	// Read the mappings and generate a TyypeMapping from them
  	InputStream mappingsInputStream = getClass().getClassLoader().getResourceAsStream(
  			RESOURCE_SHARED_INDEX_MAPPING_PREFIX + String.valueOf(indexVersion) + RESOURCE_SHARED_INDEX_POSTFIX);
  	JsonParser mappingsParser = mapper.jsonProvider().createParser(mappingsInputStream);
  	TypeMapping typeMapping = TypeMapping._DESERIALIZER.deserialize(mappingsParser, mapper);
  	
  	return typeMapping;
	}
	
	private IndexSettings getIndexSettings(JsonpMapper mapper) {
  	
  	// Read the settings and generate an IndexSettings from them
  	InputStream settingsInputStream = getClass().getClassLoader().getResourceAsStream(
  			RESOURCE_SHARED_INDEX_SETTING_PREFIX + String.valueOf(indexVersion) + RESOURCE_SHARED_INDEX_POSTFIX);
  	JsonParser settingsParser = mapper.jsonProvider().createParser(settingsInputStream);
  	IndexSettings indexSettings = IndexSettings._DESERIALIZER.deserialize(settingsParser, mapper);
  	
  	return indexSettings;
	}
	
	private Mono<CreateIndexResponse> createIndex() {
		try {
			// Get hold of the stream for the settings
			JsonpMapper mapper = client._transport().jsonpMapper();
			// Now create the index
			return Mono.fromFuture(
					client.indices()
						.create(ind -> ind
							.index(realIndexName())
							.aliases(indexName, aliasBuilder -> aliasBuilder.isWriteIndex(true))

							.settings(getIndexSettings(mapper))
							.mappings(getTypeMappings(mapper))));

		} catch (Throwable e) {
			return Mono.error(new DcbError("Error when creating index", e));
		}
	}
	
	private Mono<DeleteIndexResponse> deleteIndex() {
		try {
			return Mono.fromFuture(
				client.indices().delete(del -> del
					.ignoreUnavailable(true)
					.index(realIndexName())))
			;
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error when deleting index", e) );
		}
	}
	
	@Override
	protected Publisher<List<org.olf.dcb.indexing.bulk.IndexOperation<UUID, ClusterRecord>>> doOnNext(List<org.olf.dcb.indexing.bulk.IndexOperation<UUID, ClusterRecord>> item) {
		return bulkOperations(item)
				.thenReturn(item);
	}
	
	private BulkRequest.Builder addBulkOperation (BulkRequest.Builder bulk, org.olf.dcb.indexing.bulk.IndexOperation<UUID, ClusterRecord> op) {
		return (BulkRequest.Builder)switch (op.type()) {
			case CREATE, UPDATE -> {
				ClusterRecordIndexDoc indexDoc = conversionService.convertRequired(op.doc(), ClusterRecordIndexDoc.class);
				
				yield bulk.operations( b -> b.index(index -> index
					.index(indexName)
					.id(indexDoc.getBibClusterId().toString())
					.document(indexDoc)));
			}
			
			case DELETE -> bulk.operations( b -> b.delete(index -> index
				.index(indexName)
				.id(op.id().toString())));
		};
	}
	
	private Mono<BulkResponse> bulkOperations(

		Collection<org.olf.dcb.indexing.bulk.IndexOperation<UUID, ClusterRecord>> cr) {
		
		return Flux.fromIterable(cr)
			.publishOn(Schedulers.boundedElastic())
			.reduce( new BulkRequest.Builder(), this::addBulkOperation )
			.flatMap( bops -> Mono.<BulkResponse>create(sink -> {
					try {
						
						BulkRequest breq = null;
						try {
						
							breq = bops.index(indexName).build();
						} catch (Exception e) {
							log.error("Invalid bulk request. Skipping", e);
						}

						if ( breq != null ) {
							log.info("attempt index bulk operation {} items",breq.operations().size());
							client.bulk( breq ).handle((br, ex) -> {
								if ( ex != null) {
									sink.error(ex);
									return ex;
								}
							
								sink.success(br);
								return br;
							});
						}
					} catch (Exception e) {
						sink.error(e);
					}
				})
				.onErrorMap(e -> new DcbError("Error communicating with Elasticearch", e))
			)
			.doOnNext( resp -> {
				if (log.isDebugEnabled()) {
					log.debug("Sent {} documents to be indexed in {} seconds", resp.items().size(),  (resp.took() / 1000.00D));
					if (resp.errors()) {
						long errCount = resp.items().stream()
							.map(BulkResponseItem::error)
							.filter(Objects::nonNull)
							.count();
						log.atWarn().log("There were {} indexing errors", errCount);
					}
				}
			})
			.doOnError( err -> log.error("Error using Elasticearch bulk operation", err));
	}

	// Default to 30 seconds.
	private volatile AtomicReference<Time> refreshInterval = new AtomicReference<Time>(REFRESH_INTERVAL_DEFAULT);
	
	private Mono<IndexSettings> getIndexSettings() {
		log.debug("attempt to get index settings {}",indexName);
		try {
			return Mono.fromFuture(
					client.indices()
					.getSettings( ind -> ind
						.index( indexName )))
				.map( GetIndicesSettingsResponse::result )
        // Direct get of indexName will break when using an alias because although we can get indexname/_settings, the actual key
        // returned will be the name of the real index so with -1 -2 -3 appended. Because of this we get the first value - due to the
        // parameter above we should only get back the single actual index representing the alias.
				.map( indexMap -> indexMap.entrySet().iterator().next().getValue() )
				.map( IndexState::settings )
				.onErrorResume( e -> {
					log.warn("Error in (ES) getIndexSettings for {} - {}", indexName, e.getMessage());
					return Mono.error( new DcbError("Error (ES) in getIndexSettings for "+indexName+":"+e.getMessage(),e));
				});
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error fetching index settings for "+indexName, e) );
		}
	}
	
	private Mono<Boolean> changeIndexSettings(Function<IndexSettings.Builder, ObjectBuilder<IndexSettings>> settings) {
		
		try {
			return Mono.fromFuture(
					client.indices()
					.putSettings( s -> s.index(indexName)
						.settings(settings)))
				.map(AcknowledgedResponse::acknowledged)
				.onErrorResume( e -> {
					log.warn("Error in (ES) changeIndexSettings {}",e.getMessage());
					return Mono.error( new DcbError("Error in (ES) getIndexSettings "+e.getMessage(),e));
				});
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error fetching index settings", e) );
		}
	}
	
	private final static Time REFRESH_INTERVAL_DISABLED = Time.of(t -> t.time("-1"));
	
	private final static Time REFRESH_INTERVAL_DEFAULT = Time.of(t -> t.time("30s"));
	
	private Mono<Boolean> restoreRefresh() {
		
		
		Time time = getRefreshInterval();
		
		log.info("Attempting to update OpenSearch refresh interval to {}",time);

    return changeIndexSettings( s -> s.index(i -> i.refreshInterval(time)));
	}
	
	private Time getRefreshInterval() {
		
		Time time = refreshInterval.get();
		if ( time.equals(REFRESH_INTERVAL_DISABLED) ) {
			
			refreshInterval.set(REFRESH_INTERVAL_DEFAULT);
			time = refreshInterval.get();
		}
		
		return time;
	}
	
	private boolean isTimeValueDisabled(Time time) {
		if (time.isOffset()) return time.offset() > -1;
		
		return "-1".equals( time.time() );
	}
	
	
	private Mono<Boolean> disableRefresh() {
		
		return getIndexSettings()
			.map( state -> {
				var existingInterval = state.index().refreshInterval();
				if (existingInterval != null && !isTimeValueDisabled(existingInterval)) {
					refreshInterval.set(existingInterval);
				}
				return refreshInterval.get();
			})
			.flatMap( previousInterval -> changeIndexSettings( s -> s
				.index(i -> i.refreshInterval(REFRESH_INTERVAL_DISABLED)) ));
	}
	
	@Override
	protected void rateThresholdClosedHook() {
		restoreRefresh().subscribe( res -> log.debug("Re-enabled refresh in Elasticsearch") );
	}
	
	@Override
	protected void rateThresholdOpenHook() {
		disableRefresh().subscribe( res -> log.debug("Disabled refresh in Elasticsearch") );
	}

	@Override
	public Mono<Void> deleteDocsIndexedBefore(Instant before) {
		try {
			return Mono.fromFuture(
				client.deleteByQuery(deleteBy -> deleteBy
					.index(realIndexName())
					.query(q -> q
						.bool(topLevel -> topLevel
							.should(should -> should
								.range( range -> range
									.field( DOCUMENT_SHARED_INDEX_DATEFIELD )
									.lt( JsonData.of( before.toString() ))
								)
							)
							.should(should -> should
								.bool( bool -> bool
									.mustNot(mn -> mn
										.exists(exists -> exists
											.field(DOCUMENT_SHARED_INDEX_DATEFIELD)))
								)
							)
						)
					)
				)
			)
				.doOnSuccess(data -> log.info("Deleted [{}] documents that were indexed before [{}]", data.deleted(), before))
				.then();
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error deleteing superfluous documents", e) );
		}
	
	}
}
