package org.olf.dcb.indexing.elasticsearch;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.olf.dcb.core.clustering.RecordClusteringService;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.indexing.SharedIndexConfiguration;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;
import org.olf.dcb.indexing.storage.SharedIndexQueueRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.AcknowledgedResponse;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
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

	private static final String RESOURCE_SHARED_INDEX_FULL_SETTINGS_PREFIX = "sharedIndex/fullSettings-";
	private static final String RESOURCE_SHARED_INDEX_POSTFIX = ".json";

	private final Logger log = LoggerFactory.getLogger(ElasticsearchSharedIndexService.class);
	
	private final ElasticsearchAsyncClient client;
	private final ConversionService conversionService;
	
	private final String indexName; 
	private final int indexVersion; 
	
	public ElasticsearchSharedIndexService(SharedIndexConfiguration conf, ElasticsearchAsyncClient client, ConversionService conversionService, RecordClusteringService recordClusteringService, SharedIndexQueueRepository sharedIndexQueueRepository, PublisherTransformationService pubs) {
		super(recordClusteringService, sharedIndexQueueRepository, pubs, conf);
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
	
	@Override
	public Mono<Void> initialize() {
		
		// Check for index.
		// If not present then create.
		return checkIndex()
			.map(BooleanResponse::value)
			.filter(Boolean.FALSE::equals)
			.flatMap( b_ -> createIndex() )
			.doOnNext(resp -> log.atInfo().log("Initialized index: {}", resp.index()))
			.onErrorMap( handleErrors("Error initializing shared index") )
			.then();
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
	
	private Mono<CreateIndexResponse> createIndex() {
		try {
			// Get hold of the stream for the settings
	    	InputStream fullSettingsInputStream = getClass().getClassLoader().getResourceAsStream(RESOURCE_SHARED_INDEX_FULL_SETTINGS_PREFIX + String.valueOf(indexVersion) + RESOURCE_SHARED_INDEX_POSTFIX);

	    	// Now create the index
			return Mono.fromFuture(
				client.indices()
					.create(ind -> ind
						.index( realIndexName() )
						.aliases(indexName, aliasBuilder -> aliasBuilder.isWriteIndex(true))
						.withJson(fullSettingsInputStream)
					)
			);
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error when creating index", e) );
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
	private volatile AtomicReference<Time> refreshInterval = new AtomicReference<Time>(Time.of(t->t.time("30s")));
	
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
	
	private Mono<Boolean> restoreRefresh() {
		
		return changeIndexSettings( s -> s.index(i -> i.refreshInterval(Time.of(t->t.time("30s")))));

		// return Mono.just(refreshInterval.get())
		//		.flatMap( val -> changeIndexSettings( s -> s
		// 	  .index(i -> i.refreshInterval(val))));
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
}
