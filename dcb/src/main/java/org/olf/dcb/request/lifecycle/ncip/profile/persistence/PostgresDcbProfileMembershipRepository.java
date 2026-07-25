package org.olf.dcb.request.lifecycle.ncip.profile.persistence;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresDcbProfileMembershipRepository
	extends DcbProfileMembershipRepository,
		ReactiveStreamsCrudRepository<DcbProfileMembership, UUID> {
}
