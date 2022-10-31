package org.olf.reshare.dcb.security;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.repository.CrudRepository;


import javax.transaction.Transactional;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Optional;

import static io.micronaut.data.model.query.builder.sql.Dialect.POSTGRES;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
//import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;

@R2dbcRepository(dialect = POSTGRES)
public interface RefreshTokenRepository extends CrudRepository<RefreshTokenEntity, Long> { 

    @Transactional
    RefreshTokenEntity save(@NonNull @NotBlank String username,
                            @NonNull @NotBlank String refreshToken,
                            @NonNull @NotNull Boolean revoked); 

    Optional<RefreshTokenEntity> findByRefreshToken(@NonNull @NotBlank String refreshToken); 

    long updateByUsername(@NonNull @NotBlank String username,
                          boolean revoked); 
}