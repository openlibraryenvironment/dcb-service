package services.k_int.data.querying.lucene;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.builders.QueryBuilder;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneJpaQuerySpecificationBuilder<T> implements QueryBuilder, JpaQuerySpecificationBuilder<T, QueryNode> {
	
	@Override
  public QuerySpecification<T> build(final QueryNode queryNode) throws QueryNodeException {
		// The base should be ANDed.
		return this.processQueryNode(queryNode)
				.reduce(QuerySpecification::and)
				.orElse(null);
  }
}
