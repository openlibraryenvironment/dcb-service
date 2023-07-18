package org.olf.dcb.core;

import org.olf.dcb.core.model.DataAgency;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

public class QueryService {

        private PredicateSpecification<DataAgency> codeLike(String code) {
                return (root, criteriaBuilder) -> criteriaBuilder.like(root.get("code"), "%"+code+"%");
        }

}
