package org.olf.dcb.indexing.opensearch;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;
import org.olf.dcb.indexing.storage.SharedIndexQueueRepository;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.analysis.Analyzer;
import org.opensearch.client.opensearch._types.analysis.Normalizer;
import org.opensearch.client.opensearch._types.analysis.NormalizerBuilders;
import org.opensearch.client.opensearch._types.analysis.TokenFilter;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.PropertyBuilders;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.util.ObjectBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Order(OpenSearchSharedIndexService.OS_INDEXER_PRIORITY)
@Setter
@Requires(bean = OpenSearchAsyncClient.class)
@Requires(property = "dcb.index.name")
@Singleton
public class OpenSearchSharedIndexService extends BulkSharedIndexService {
	
	private static final String STOPWORDS_FILTER_NAME = "dcb_stopwords_filter";

	private static final String DEFAULT_STOPWORDS = "_english_";

	static final int OS_INDEXER_PRIORITY = 1;

	private static final String LOWERCASE_NORMALIZER = "lowercase_normalizer";
	
	private final Logger log = LoggerFactory.getLogger(OpenSearchSharedIndexService.class);
	
	private final OpenSearchAsyncClient client;
	private final ConversionService conversionService;
	
	@io.micronaut.context.annotation.Property(name="dcb.index.name")
	private String indexName; 
	
	public OpenSearchSharedIndexService(OpenSearchAsyncClient client, ConversionService conversionService, RecordClusteringService recordClusteringService, SharedIndexQueueRepository sharedIndexQueueRepository) {
		super(recordClusteringService, sharedIndexQueueRepository);
		this.client = client;
		this.conversionService = conversionService;
	}
	
	@PostConstruct
	void init() {
		log.info("Using Opensearch Indexing service");
	}

	private Mono<Void> deleteAllAsync() {
		return Mono.from( deleteIndex() )
			.doOnNext(resp -> {				
				log.atInfo().log("Delete all, by deleting index: {}", indexName);
			})
			.onErrorMap( handleErrors("Error deleting all from shared index") )
			.then();
	}
	
	@Override
	@ExecuteOn(TaskExecutors.BLOCKING)
	public void deleteAll() {
		deleteAllAsync().block();
	}
	

	private Mono<BooleanResponse> checkIndex() {
		try {
			return Mono.fromFuture(
				client.indices()
					.exists( ind -> ind
						.index( indexName )));
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error when creating index", e) );
		}
	}

	public Mono<CreateIndexResponse> initializeAsync() {
		
		// Check for index.
		// If not present then create.
		return checkIndex()
			.map(BooleanResponse::value)
			.filter(Boolean.FALSE::equals)
			.then( createIndex() )
			.doOnNext(resp -> {
				log.atInfo().log("Initialized index: {}", resp.index());
			})
			.onErrorMap( handleErrors("Error initializing shared index") );
	}
	
	@Override
	@ExecuteOn(TaskExecutors.BLOCKING)
	public void initialize() {
		initializeAsync().block();
	}
	
	private Function<Throwable, Throwable> handleErrors ( final String message ) {
		return ( cause ) -> new DcbException( message, cause );
	}
	
	private Mono<CreateIndexResponse> createIndex() {
		try {
			return Mono.fromFuture(
				client.indices()
					.create(ind -> ind
						.index( indexName )
						.settings( this::buildIndexSettings )
						.mappings( this::getMappings )));
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error when creating index", e) );
		}
	}
	
	
	private Mono<DeleteIndexResponse> deleteIndex() {
		try {
			return Mono.fromFuture(
				client.indices().delete(del -> del
					.ignoreUnavailable(true)
					.index(indexName)))
			;
			
		} catch (Throwable e) {
			return Mono.error( new DcbError("Error when deleting index", e) );
		}
	}
	
	private ObjectBuilder<IndexSettings> buildIndexSettings(IndexSettings.Builder settings) {
		return settings
			.analysis(analysis -> analysis
				.analyzer(getAnalyzersMap())
				.filter(getFiltersMap())
				.normalizer(getNormalizersMap()));
	}
	
	private static Map<String, Analyzer> getAnalyzersMap() {
		return Map.of(
			"default", Analyzer.of( anb -> anb
			  .custom(cab -> cab
					.tokenizer("whitespace")
					.filter(List.of(STOPWORDS_FILTER_NAME)))));
	}
	
	private static Map<String, TokenFilter> getFiltersMap() {
		return Map.of(
			STOPWORDS_FILTER_NAME, TokenFilter.of(tf -> tf
				.definition(def -> def
					.stop(sb -> sb
						.ignoreCase(true)
						.stopwords(DEFAULT_STOPWORDS)))));
	}
	

	private static Map<String, Normalizer> getNormalizersMap() {
		return Map.of(
			LOWERCASE_NORMALIZER, NormalizerBuilders
				.lowercase()
				.build()
					._toNormalizer());
	}
	
	private Property buildKeyWordFieldTextProperty(final int maxKw, final boolean normalizeToLowercase) {
		return Property.of(p -> p
				.text(t -> t
					.fields("keyword", f -> f
						.keyword(kw -> {
							if (normalizeToLowercase) {
								kw.normalizer(LOWERCASE_NORMALIZER);
							}
							
							return kw.ignoreAbove(maxKw);
					  }))));
	}
	
	private Property defaultKeywordProperty() {
		return new Property(PropertyBuilders.keyword().build());
	}
	
	private ObjectBuilder<TypeMapping> getMappings(TypeMapping.Builder m) {
		return m
			.properties(
				"title", buildKeyWordFieldTextProperty(256, true))
			.properties(
				"primaryAuthor", buildKeyWordFieldTextProperty(256, true))
			.properties(
				"yearOfPublication", new Property(PropertyBuilders.long_().build()))
			.properties(
				"bibClusterId", buildKeyWordFieldTextProperty(256, false))
			.properties(
				"members", mem -> mem
					.object(mo -> mo
						.properties(
							"bibId", defaultKeywordProperty())
						.properties(
							"sourceSystem", buildKeyWordFieldTextProperty(256, false))))
					
			.properties(
				"isbn", buildKeyWordFieldTextProperty(256, false))
			.properties(
				"issn", buildKeyWordFieldTextProperty(256, false))
			.properties(
				"metadata", md -> md
					.object(mo -> mo
						.properties(
							"agents", a -> a
								.object(ao -> ao
									.properties("label", buildKeyWordFieldTextProperty(256, false))
									.properties("subtype", defaultKeywordProperty())))
								
						.properties(
							"subjects", s -> s
								.object(so -> so
									.properties("label", buildKeyWordFieldTextProperty(256, true))
									.properties("subtype", defaultKeywordProperty())))
						
						.properties(
							"identifiers", id -> id
								.object(ido -> ido
									.properties("value", defaultKeywordProperty())
									.properties("namespace", defaultKeywordProperty())))));
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
						client.bulk( bops.index(indexName).build() ).handle((br, ex) -> {
							if ( ex != null) {
								sink.error(ex);
								return ex;
							}
							
							sink.success(br);
							return br;
						});
					} catch (Exception e) {
						sink.error(e);
					}
				})
				.onErrorMap(e -> new DcbError("Error communicating with OpenSearch", e))
			)
			.doOnNext( resp -> {
				if (log.isDebugEnabled()) {
					log.debug("Indexed {} documents in {}", resp.items().size(), resp.took());
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
	
}
