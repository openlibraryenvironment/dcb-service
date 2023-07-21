package services.k_int.data.querying;

import java.util.Objects;
import java.util.stream.Stream;

import org.apache.lucene.queryparser.flexible.core.nodes.BooleanQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.ModifierQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.standard.nodes.BooleanModifierNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.lucene.LuceneModifierQueryNodeBuilder;
import services.k_int.data.querying.lucene.LuceneBooleanQueryNodeBuilder;
import services.k_int.data.querying.lucene.LuceneFieldQueryNodeBuilder;

public interface JpaQuerySpecificationBuilder<T,S> {
	
	static final Logger log = LoggerFactory.getLogger(JpaQuerySpecificationBuilder.class);
	
	// Default implementation.
	static final JpaQuerySpecificationBuilder<?,QueryNode> UNIMPLEMENTED =	new JpaQuerySpecificationBuilder<> () {
		@Override
		public QuerySpecification<Object> build(QueryNode from) throws Exception {
			log.info("QueryNodeType {} is not currently supported", from.getClass());
			return null;
		}
	};

	@SuppressWarnings("unchecked")
	private static <R, Q extends QueryNode> JpaQuerySpecificationBuilder<R, Q> getBuilderForNode( Q queryNode ) {
		
		Objects.requireNonNull(queryNode);
		
		JpaQuerySpecificationBuilder<R, ? extends QueryNode> builder = null;
		if ( FieldQueryNode.class.isAssignableFrom( queryNode.getClass())) {
			builder = new LuceneFieldQueryNodeBuilder<R>();
			
		} else if ( BooleanQueryNode.class.isAssignableFrom( queryNode.getClass())) {
			builder = new LuceneBooleanQueryNodeBuilder<R>();
			
		} else if ( ModifierQueryNode.class.isAssignableFrom( queryNode.getClass())) {
			builder = new LuceneModifierQueryNodeBuilder<R>();
		}
		
		// Default implementation.
		return (JpaQuerySpecificationBuilder<R, Q>) Objects.requireNonNullElse(builder, UNIMPLEMENTED);
	}
	
	public default Stream<QuerySpecification<T>> processQueryNode(@NonNull QueryNode queryNode) {

		final JpaQuerySpecificationBuilder<T, QueryNode> theBuilder = getBuilderForNode(queryNode);
		
		// Get specific builder...
		return Stream.ofNullable(theBuilder)
			.map(builder -> {
				try {
					return builder.build(queryNode);
				} catch (Exception e) {
					log.error("Error parsing query node", e);
				}
				return null;
			})
			.filter(Objects::nonNull);
			
	}
	
	public QuerySpecification<T> build(S from) throws Exception;
}
