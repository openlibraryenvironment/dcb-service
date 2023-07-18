package org.olf.dcb.core;

import org.olf.dcb.core.model.DataAgency;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.core.config.QueryConfigHandler;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;

public class QueryService {

        private PredicateSpecification<DataAgency> codeLike(String code) {
                return (root, criteriaBuilder) -> criteriaBuilder.like(root.get("code"), "%"+code+"%");
        }

        private Query parse(String q) throws QueryNodeException {
                StandardQueryParser qpHelper = new StandardQueryParser();
                Query query = qpHelper.parse(q, "defaultField");
                return query;
        }

        public void evaluate(String q, Class c, JpaSpecificationExecutor repo) {
                try {
                        Query parsed_query = parse(q);
                }
                catch ( QueryNodeException qne ) {
                }
        }

}
