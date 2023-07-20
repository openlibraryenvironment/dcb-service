package services.k_int.data.querying;

import org.olf.dcb.core.model.DataAgency;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
// import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;

import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.core.config.QueryConfigHandler;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Import where(nameEquals("Dennis"))  Person.findAll(where(nameEquals("Dennis")));
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where;

public class QueryService {

	private static final Logger log = LoggerFactory.getLogger(QueryService.class);

	private PredicateSpecification<DataAgency> codeLike(String code) {
		return (root, criteriaBuilder) -> criteriaBuilder.like(root.get("code"), "%" + code + "%");
	}

	private Query parse(String q) throws QueryNodeException {
		log.debug("parse({})", q);
		StandardQueryParser qpHelper = new StandardQueryParser();
		Query query = qpHelper.parse(q, "defaultField");
		log.debug("returning: {}", query.toString());
		return query;
	}

	public void evaluate(String q, Class c, ReactiveStreamsJpaSpecificationExecutor repo) {
		log.debug("evaluate({},{},...)", q, c);
		try {
			Query parsed_query = parse(q);
			dumpQuery(0, parsed_query);
			PredicateSpecification ps = convertQuery(parsed_query, c);

			// @NonNull org.reactivestreams.Publisher<T> findAll(@Nullable
			// QuerySpecification<T> spec)
			// https://micronaut-projects.github.io/micronaut-data/snapshot/api/io/micronaut/data/repository/jpa/criteria/PredicateSpecification.html
			// Nullable jakarta.persistence.criteria.Predicate toPredicate(@NonNull
			// jakarta.persistence.criteria.Root<T> root,
			// jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder)

		} catch (QueryNodeException qne) {
			log.error("Problem parsing query");
		}
	}

	private void dumpQuery(int depth, Query q) {
    log.debug("[{}] {}", depth, q.getClass().getName());
    if ( q instanceof org.apache.lucene.search.TermQuery ) {
      // https://lucene.apache.org/core/7_0_0/core/org/apache/lucene/search/TermQuery.html
      TermQuery tq = (org.apache.lucene.search.TermQuery) q;
      log.debug("TermQuery: {} {}",tq.getTerm().field(), tq.getTerm().text());
    }
  }

	private PredicateSpecification convertQuery(Query q, Class c) {
		// return (root, criteriaBuilder, q) -> recurseQueryTree(root, criteriaBuilder,
		// q);
		return null;
	}

	/*
	 * private PredicateSpecification
	 * recurseQueryTree(jakarta.persistence.criteria.Root root,
	 * jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder, Query q) {
	 * return null; }
	 */
}
