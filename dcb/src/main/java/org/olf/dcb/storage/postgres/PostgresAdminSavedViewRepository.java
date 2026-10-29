package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.model.AdminSavedView;
import org.olf.dcb.storage.AdminSavedViewRepository;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresAdminSavedViewRepository
	extends ReactiveStreamsCrudRepository<AdminSavedView, UUID>, AdminSavedViewRepository {
}
