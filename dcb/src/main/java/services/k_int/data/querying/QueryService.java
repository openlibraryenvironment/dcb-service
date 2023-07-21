package services.k_int.data.querying;

import java.util.Objects;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import services.k_int.data.querying.lucene.JpaSpecificationQueryParser;

public class QueryService {

	private static final Logger log = LoggerFactory.getLogger(QueryService.class);

	private <T> QuerySpecification<T> parse(String q, Class<T> entitiyClass) throws QueryNodeException {
		log.debug("parse({})", q);
		JpaSpecificationQueryParser<T> qpHelper = new JpaSpecificationQueryParser<>();
		QuerySpecification<T> query = qpHelper.parse(q, "defaultField");
		log.debug("returning: {}", Objects.toString(query, null));
		return query;
	}

	public <T> QuerySpecification<T> evaluate(String q, Class<T> c) throws Exception {
		log.debug("evaluate({},{},...)", q, c);
		try {
			QuerySpecification<T> parsed_query = parse(q, c);
			return parsed_query;
//			dumpQuery(0, parsed_query);

			// @NonNull org.reactivestreams.Publisher<T> findAll(@Nullable
			// QuerySpecification<T> spec)
			// https://micronaut-projects.github.io/micronaut-data/snapshot/api/io/micronaut/data/repository/jpa/criteria/PredicateSpecification.html
			// Nullable jakarta.persistence.criteria.Predicate toPredicate(@NonNull
			// jakarta.persistence.criteria.Root<T> root,
			// jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder)

		} catch (QueryNodeException qne) {
			log.error("Problem parsing query");
			throw qne;
		}
	}

	private <T> void dumpQuery(int depth, QuerySpecification<T> q) {
    log.debug("[{}]", q.getClass().getName());
  }
	/*
	 * private PredicateSpecification
	 * recurseQueryTree(jakarta.persistence.criteria.Root root,
	 * jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder, Query q) {
	 * return null; }
	 */
}
