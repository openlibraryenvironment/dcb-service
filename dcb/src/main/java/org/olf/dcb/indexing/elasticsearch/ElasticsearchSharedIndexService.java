package org.olf.dcb.indexing.elasticsearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.svc.RecordClusteringService;
import org.olf.dcb.indexing.SharedIndexConfiguration;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;
import org.olf.dcb.indexing.storage.SharedIndexQueueRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CharFilter;
import co.elastic.clients.elasticsearch._types.analysis.Normalizer;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.PropertyBuilders;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Setter
@Requires(bean = ElasticsearchAsyncClient.class)
@Requires(bean = SharedIndexConfiguration.class)
@Singleton
public class ElasticsearchSharedIndexService extends BulkSharedIndexService {
	
	private static final String DEFAULT_STOPWORDS = "_english_";
	private static final String TOKENFILTER_LOWERCASE = "lowercase";
	private static final String CHARFILTER_FOLD_WHITESPACE = "fold_multiple_whitespace";
	private static final String CHARFILTER_REMOVE_PUNCTUATION = "spaces_for_punctuation";
	private static final String STOPWORDS_FILTER_NAME = "dcb_stopwords_filter";

	private static final String LOWERCASE = TOKENFILTER_LOWERCASE;
	private static final String WITH_LOWERCASE = "_" + LOWERCASE;
	private static final String STRIPT_PUNCTUATION_KEYWORD_NORMALIZER = "default_normalizer";
	private static final String DEFAULT_KEYWORD_NORMALIZER = "preserve_punctuation_normalizer";

	private static final String TOKENFILTER_ASCIIFOLDING = "asciifolding";
	private static final String TOKENFILTER_TRIM = "trim";

	private final Logger log = LoggerFactory.getLogger(ElasticsearchSharedIndexService.class);
	
	private final ElasticsearchAsyncClient client;
	private final ConversionService conversionService;
	
	private final String indexName; 
	
	public ElasticsearchSharedIndexService(SharedIndexConfiguration conf, ElasticsearchAsyncClient client, ConversionService conversionService, RecordClusteringService recordClusteringService, SharedIndexQueueRepository sharedIndexQueueRepository) {
		super(recordClusteringService, sharedIndexQueueRepository);
		this.client = client;
		this.conversionService = conversionService;
		this.indexName = conf.name();
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
				.charFilter(getCharFilters())
				.filter(getFiltersMap())
				.normalizer(getNormalizersMap()));
	}
	
	private static Map<String, CharFilter> getCharFilters() {
		
		return Map.of(
			CHARFILTER_REMOVE_PUNCTUATION, CharFilter.of( cf ->
				cf.definition(cfd -> cfd.patternReplace(pr ->
					pr.pattern("[^\\w|\\s|\\-]")
						.replacement(" ")
						.flags("")))
					),
			CHARFILTER_FOLD_WHITESPACE, CharFilter.of( cf ->
					cf.definition(cfd -> cfd.patternReplace(pr ->
					pr.pattern("\\s{2,}")
						.replacement(" ")
						.flags("")))
					)
		);
	}
	
	private static Map<String, Analyzer> getAnalyzersMap() {
		return Map.of(
			"default", Analyzer.of( anb -> anb
			  .custom(cab -> cab
					.tokenizer("whitespace")
					.filter(List.of(TOKENFILTER_LOWERCASE, STOPWORDS_FILTER_NAME)))));
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
		final var defaults = Map.of(
			STRIPT_PUNCTUATION_KEYWORD_NORMALIZER, Normalizer.of(norm -> norm
				.custom( cust -> cust
					.filter(TOKENFILTER_ASCIIFOLDING)
					.charFilter(List.of(
						CHARFILTER_REMOVE_PUNCTUATION,
						CHARFILTER_FOLD_WHITESPACE))
					.filter(TOKENFILTER_TRIM))),
			
			DEFAULT_KEYWORD_NORMALIZER, Normalizer.of(norm -> norm
				.custom( cust -> cust
					.filter(TOKENFILTER_ASCIIFOLDING)
					.charFilter(List.of(
						CHARFILTER_FOLD_WHITESPACE))
					.filter(TOKENFILTER_TRIM))));
		
		// Create lowercase variants of the defined Normalizers
		Map<String, Normalizer> defaults_with_lowercase = new HashMap<>();
		defaults.forEach( (name, normalizer) -> {
			defaults_with_lowercase.put(name, normalizer);
			
			if (normalizer.isCustom()) {
				final List<String> charFilters = new ArrayList<> ( normalizer.custom().charFilter() );
				final List<String> filters = new ArrayList<> ( normalizer.custom().filter() );
				filters.add(TOKENFILTER_LOWERCASE);
				
				final var lowercaseVariant = Normalizer.of(norm -> norm
					.custom( cust -> cust
						.charFilter(charFilters)
						.filter(filters)));
				
				// Add the lowercase variant
				defaults_with_lowercase.put(name + WITH_LOWERCASE, lowercaseVariant);
			}
		});
//		Map<String, Normalizer> defaults_with_lowercase = new HashMap<>();
		return defaults_with_lowercase;
	}
	
	private Property buildKeyWordFieldTextProperty(final int maxKw, final String normalizer) {
		return Property.of(p -> p
			.text(t -> t
					
				.fields("keyword", f -> f
					.keyword(kw -> kw
						.ignoreAbove(maxKw)
						.normalizer(normalizer)))));
	}
	
	private Property defaultKeywordProperty() {
		return new Property(PropertyBuilders.keyword().build());
	}
	
	private ObjectBuilder<TypeMapping> getMappings(TypeMapping.Builder m) {
		return m
			.properties(
				"title", buildKeyWordFieldTextProperty(256, DEFAULT_KEYWORD_NORMALIZER + WITH_LOWERCASE))
			.properties(
				"primaryAuthor", buildKeyWordFieldTextProperty(256, STRIPT_PUNCTUATION_KEYWORD_NORMALIZER + WITH_LOWERCASE))
			.properties(
				"yearOfPublication", new Property(PropertyBuilders.long_().build()))
			.properties(
				"bibClusterId", buildKeyWordFieldTextProperty(256, DEFAULT_KEYWORD_NORMALIZER))
			.properties(
				"members", mem -> mem
					.object(mo -> mo
						.properties(
							"bibId", defaultKeywordProperty())
						.properties(
							"sourceSystem", buildKeyWordFieldTextProperty(256, DEFAULT_KEYWORD_NORMALIZER))))
					
			.properties(
				"isbn", buildKeyWordFieldTextProperty(256, DEFAULT_KEYWORD_NORMALIZER))
			.properties(
				"issn", buildKeyWordFieldTextProperty(256, DEFAULT_KEYWORD_NORMALIZER))
			.properties(
				"metadata", md -> md
					.object(mo -> mo
						.properties(
							"agents", a -> a
								.object(ao -> ao
									.properties("label", buildKeyWordFieldTextProperty(256, DEFAULT_KEYWORD_NORMALIZER))
									.properties("subtype", defaultKeywordProperty())))
								
						.properties(
							"subjects", s -> s
								.object(so -> so
									.properties("label", buildKeyWordFieldTextProperty(256, STRIPT_PUNCTUATION_KEYWORD_NORMALIZER + WITH_LOWERCASE))
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
						BulkRequest breq = bops.index(indexName).build();

						if ( ! breq.operations().isEmpty() ) {
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
	
}
