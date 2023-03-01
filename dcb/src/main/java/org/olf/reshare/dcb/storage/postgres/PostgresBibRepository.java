package org.olf.reshare.dcb.storage.postgres;

import static javax.transaction.Transactional.TxType.NOT_SUPPORTED;

import java.util.UUID;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.storage.BibRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresBibRepository extends ReactiveStreamsPageableRepository<BibRecord, UUID>, BibRepository {
	
	@Override
	@Transactional(NOT_SUPPORTED)
	default void cleanUp() {
		// TODO Auto-generated method stub
		BibRepository.super.cleanUp();
	}
	
	@Override
	@Transactional(NOT_SUPPORTED)
	default void commit() {
		// TODO Auto-generated method stub
		BibRepository.super.commit();
	}
}
