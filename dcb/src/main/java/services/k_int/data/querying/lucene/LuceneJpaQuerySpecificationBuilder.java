package services.k_int.data.querying.lucene;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.builders.QueryBuilder;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import services.k_int.data.querying.JpaQuerySpecificationBuilder;

public class LuceneJpaQuerySpecificationBuilder<T> implements QueryBuilder, JpaQuerySpecificationBuilder<T, QueryNode> {
	
	
	@SuppressWarnings("unchecked")
	private Predicate processQueryNode (
			@NonNull Root<T> root,
      @NonNull CriteriaQuery<?> query,
      @NonNull CriteriaBuilder criteriaBuilder,
      @NonNull QueryNode queryNode) {
		
		QuerySpecification<T> allspec = (QuerySpecification<T>) QuerySpecification.ALL;
		
		return  allspec.toPredicate(root, query, criteriaBuilder);
	}
	
	@Override
  public QuerySpecification<T> build(final QueryNode queryNode) throws QueryNodeException {
		
		QuerySpecification<T> qspec = (root, query, criteriaBuilder) ->
			this.processQueryNode(root, query, criteriaBuilder, queryNode);
		
		return qspec;
  }
}
