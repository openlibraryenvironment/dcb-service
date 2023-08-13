package services.k_int.data.querying.lucene;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.lucene.queryparser.flexible.core.nodes.AndQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.BooleanQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.OrQueryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneBooleanQueryNodeBuilder<T> implements JpaQuerySpecificationBuilder<T,BooleanQueryNode> {

	private static final Logger log = LoggerFactory.getLogger(LuceneBooleanQueryNodeBuilder.class);
	
	private enum Junction {
		AND, OR
	}
	
	private Junction getJunction( @NonNull BooleanQueryNode boolQuery ) {
		Class<? extends BooleanQueryNode> typeClass = boolQuery.getClass();
		if (AndQueryNode.class.isAssignableFrom(typeClass)) {
			return Junction.AND;
		} else if (OrQueryNode.class.isAssignableFrom(typeClass)) {
			return Junction.OR;
		}
		
		log.warn("Unknown boolean query node: {}", typeClass);
		
		return null;
	}
	
	
	@Override
	public QuerySpecification<T> build( @NonNull final BooleanQueryNode boolNode) throws Exception {
			
		final Junction type = getJunction( boolNode );
		if (type == null) return null;
		
		return Stream.ofNullable( boolNode.getChildren() )
			.flatMap(List::stream)
			.flatMap(this::processQueryNode)
			.filter(Objects::nonNull)
			.reduce(switch(type) {
				case AND -> QuerySpecification::and;
				case OR -> QuerySpecification::or;
			})
			.orElse(null);
	}
}
