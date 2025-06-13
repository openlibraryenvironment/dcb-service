package org.olf.dcb.indexing.opensearch;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.indexing.SharedIndexConfiguration;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;
import org.olf.dcb.indexing.storage.SharedIndexQueueRepository;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.AcknowledgedResponseBase;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexState;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.util.ObjectBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.ConversionService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.json.stream.JsonParser;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.PublisherTransformationService;

@Order(OpenSearchSharedIndexService.OS_INDEXER_PRIORITY)
@Setter
@Requires(bean = OpenSearchAsyncClient.class)
@Requires(bean = SharedIndexConfiguration.class)
@Singleton
public class OpenSearchSharedIndexService extends BulkSharedIndexService {

	private static final String RESOURCE_SHARED_INDEX_SETTING_PREFIX = "sharedIndex/settings-";
	private static final String RESOURCE_SHARED_INDEX_MAPPING_PREFIX = "sharedIndex/mappings-";
	private static final String RESOURCE_SHARED_INDEX_POSTFIX = ".json";
	
	static final int OS_INDEXER_PRIORITY = 1;

	private final Logger log = LoggerFactory.getLogger(OpenSearchSharedIndexService.class);
	
	private final OpenSearchAsyncClient client;
	private final ConversionService conversionService;
	
	private final String indexName;
	private final int indexVersion; 
	
	public OpenSearchSharedIndexService(SharedIndexConfiguration conf, OpenSearchAsyncClient client, ConversionService conversionService, RecordClusteringService recordClusteringService, SharedIndexQueueRepository sharedIndexQueueRepository, PublisherTransformationService pubs) {
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
		log.info("Using Opensearch Indexing service");
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

	private String realIndexName() {
//		return(indexName);
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

	@Override
	public Mono<Void> initialize() {
		
		// Check for index.
		// If not present then create.
		return checkIndex()
			.map(BooleanResponse::value)
			.filter(Boolean.FALSE::equals)
			.flatMap( b_ -> createIndex() )
			.doOnNext(resp -> {
				log.atInfo().log("Initialized index: {}", resp.index());
			})
			.onErrorMap( handleErrors("Error initializing shared index") )
			.then();
	}
	
	private Function<Throwable, Throwable> handleErrors ( final String message ) {
		return ( cause ) -> new DcbException( message, cause );
	}
	
	private Mono<CreateIndexResponse> createIndex() {
		try {
			// Unlike elasticsearch we cannot use withJson, so need to read the mappings and deserialize them ourselves
			// Get hold of the mapper from the transport client
	    	JsonpMapper mapper = client._transport().jsonpMapper();

	    	// Read the mappings and generate a TyypeMapping from them
	    	InputStream mappingsInputStream = getClass().getClassLoader().getResourceAsStream(RESOURCE_SHARED_INDEX_MAPPING_PREFIX + String.valueOf(indexVersion) + RESOURCE_SHARED_INDEX_POSTFIX);
	    	JsonParser mappingsParser = mapper.jsonProvider().createParser(mappingsInputStream);
	    	TypeMapping typeMapping = TypeMapping._DESERIALIZER.deserialize(mappingsParser, mapper);
	    	
	    	// Read the settings and generate an IndexSettings from them
	    	InputStream settingsInputStream = getClass().getClassLoader().getResourceAsStream(RESOURCE_SHARED_INDEX_SETTING_PREFIX + String.valueOf(indexVersion) + RESOURCE_SHARED_INDEX_POSTFIX);
	    	JsonParser settingsParser = mapper.jsonProvider().createParser(settingsInputStream);
	    	IndexSettings indexSettings = IndexSettings._DESERIALIZER.deserialize(settingsParser, mapper);

	    	// Now create the index
			return Mono.fromFuture(
				client.indices()
					.create(ind -> ind
						.index( realIndexName() )
						.aliases(indexName, aliasBuilder -> aliasBuilder.isWriteIndex(true))
						.settings( indexSettings )
						.mappings( typeMapping )
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
				.transform(getPublisherTransformer()::executeOnBlockingThreadPool)
				.onErrorMap(e -> new DcbError("Error communicating with OpenSearch", e))
			)
			.doOnNext( resp -> {
				if (log.isDebugEnabled()) {
					log.info("Sent {} documents to be indexed in {} seconds", resp.items().size(),  (resp.took() / 1000.00D));
					if (resp.errors()) {
						long errCount = resp.items().stream()
							.map(BulkResponseItem::error)
							.filter(Objects::nonNull)
							.count();
						log.atWarn().log("There were {} indexing errors", errCount);
					}
				}
			})
			.doOnError( err -> log.error("Error using OpenSearch bulk operation", err));
	}
	
	// Default to 30 seconds.
	private volatile AtomicReference<Time> refreshInterval = new AtomicReference<Time>(Time.of(t->t.time("30s")));
	
	private Mono<IndexSettings> getIndexSettings() {
		
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
          log.error("Error in (OS) getIndexSettings {}",e.getMessage());
          return Mono.error( new DcbError("Error (OS) in getIndexSettings "+e.getMessage(),e));
        });
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error fetching index settings", e) );
		}
	}
	
	private Mono<Boolean> changeIndexSettings(Function<IndexSettings.Builder, ObjectBuilder<IndexSettings>> settings) {
		
		try {
			return Mono.fromFuture(
					client.indices()
					.putSettings( s -> s.index(indexName)
						.settings(settings)))
				.map(AcknowledgedResponseBase::acknowledged)
        .onErrorResume( e -> {
          log.error("Error in (OS) changeIndexSettings {}",e.getMessage());
          return Mono.error( new DcbError("Error in (OS) changeIndexSettings "+e.getMessage(),e));
        });
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error fetching index settings", e) );
		}
	}
	
	private final static Time REFRESH_INTERVAL_DISABLED = Time.of(t -> t.time("-1"));
	
	private Mono<Boolean> restoreRefresh() {
		
		log.info("Attempting to update OpenSearch refresh interval to {}",refreshInterval.get());

		if ( refreshInterval.get().equals(REFRESH_INTERVAL_DISABLED) ) {
			refreshInterval = new AtomicReference<Time>(Time.of(t->t.time("30s")));
		}

    return changeIndexSettings( s -> s.index(i -> i.refreshInterval(Time.of(t->t.time("30s")))));

		// return Mono.just(refreshInterval.get())
		// 	.flatMap( val -> changeIndexSettings( s -> s
		// 	  .index(i -> i.refreshInterval(val))));
	}
	
	private boolean isTimeValueDisabled(Time time) {
		if (time.isOffset()) return time.offset() > -1;
		
		return "-1".equals( time.time() );
	}
	
	private Mono<Boolean> disableRefresh() {
		
		log.info("Attempting to disable OpenSearch refresh interval");
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
		restoreRefresh().subscribe( res -> log.debug("Re-enabled refresh in OpenSearch") );
	}
	
	@Override
	protected void rateThresholdOpenHook() {
		disableRefresh().subscribe( res -> log.debug("Disabled refresh in OpenSearch") );
	}

}
