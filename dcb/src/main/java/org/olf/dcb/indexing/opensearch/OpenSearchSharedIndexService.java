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
import org.opensearch.client.util.ObjectBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.ConversionService;
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
	
	static final int OS_INDEXER_PRIORITY = 1;
	
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

	@Override
	public Mono<Void> initialize() {
		return Mono.from( createIndex() )
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
	
	@Override
	public Mono<Void> deleteAll() {

		return Mono.from( deleteIndex() )
			.doOnNext(resp -> {				
				log.atInfo().log("Delete all, by deleting index: {}", indexName);
			})
			.onErrorMap( handleErrors("Error deleting all from shared index") )
			.then();
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
				.filter(getFiltersMap()));
	}
	
	private static Map<String, Analyzer> getAnalyzersMap() {
		return Map.of(
			"default", Analyzer.of( anb -> anb
			  .custom(cab -> cab
					.tokenizer("whitespace")
					.filter(List.of("dcb_stopwords_filter")))));
	}
	
	private static Map<String, TokenFilter> getFiltersMap() {
		return Map.of(
			"dcb_stopwords_filter", TokenFilter.of(tf -> tf
				.definition(def -> def
					.stop(sb -> sb
						.ignoreCase(true)))));
	}
	
	private Property buildKeyWordFieldTextProperty(final int maxKw) {
		return Property.of(p -> p
			.text(t -> t
				.fields("keyword", f -> f
					.keyword(kw -> kw
						.ignoreAbove(maxKw)))));
	}
	
	private Property defaultKeywordProperty() {
		return new Property(PropertyBuilders.keyword().build());
	}
	
	private ObjectBuilder<TypeMapping> getMappings(TypeMapping.Builder m) {
		return m
			.properties(
				"title", buildKeyWordFieldTextProperty(256))
			.properties(
				"primaryAuthor", buildKeyWordFieldTextProperty(256))
			.properties(
				"yearOfPublication", new Property(PropertyBuilders.long_().build()))
			.properties(
				"bibClusterId", buildKeyWordFieldTextProperty(256))
			.properties(
				"members", mem -> mem
					.object(mo -> mo
						.properties(
							"bibId", defaultKeywordProperty())
						.properties(
							"sourceSystem", buildKeyWordFieldTextProperty(256))))
					
			.properties(
				"isbn", buildKeyWordFieldTextProperty(256))
			.properties(
				"issn", buildKeyWordFieldTextProperty(256))
			.properties(
				"metadata", md -> md
					.object(mo -> mo
						.properties(
							"agents", a -> a
								.object(ao -> ao
									.properties("label", buildKeyWordFieldTextProperty(256))
									.properties("subtype", defaultKeywordProperty())))
								
						.properties(
							"subjects", s -> s
								.object(so -> so
									.properties("label", buildKeyWordFieldTextProperty(256))
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
