package services.k_int.data.querying;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;

public interface JpaQuerySpecificationBuilder<T,S> {
	public QuerySpecification<T> build(S from) throws Exception;
}
