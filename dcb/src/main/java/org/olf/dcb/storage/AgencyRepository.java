package org.olf.dcb.storage;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.reactivestreams.Publisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface AgencyRepository {

	@NonNull
	@SingleResult
	Publisher<? extends DataAgency> save(@Valid @NotNull @NonNull DataAgency agency);

	@NonNull
	@SingleResult
	Publisher<DataAgency> persist(@Valid @NotNull @NonNull DataAgency agency);

	@NonNull
	@SingleResult
	Publisher<? extends DataAgency> update(@Valid @NotNull @NonNull DataAgency agency);

	@NonNull
	@SingleResult
	Publisher<DataAgency> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<DataAgency>> queryAll(Pageable page);

	@NonNull
	Publisher<DataAgency> findOneByCode(String code);

	Publisher<DataAgency> queryAll();

	/**
	 * The whole library directory in ONE query.
	 *
	 * The controller previously resolved the Host LMS code per agency, which cost two
	 * extra round trips each through an unbounded flatMap on an ANONYMOUS endpoint: a
	 * 200-agency consortium meant 401 R2DBC acquisitions per unauthenticated GET, and
	 * a trivial request loop drained the connection pool out from under the state
	 * machine.
	 *
	 * A null is_borrowing_agency is "not participating", matching
	 * ResolvePatronPreflightCheck's reading of the flag — a patron from a
	 * non-borrowing agency cannot place a request, so the directory does not advertise
	 * one by default.
	 *
	 * INNER JOIN is deliberate: an agency with no Host LMS cannot be searched or
	 * requested through, so it does not belong in a discovery picker.
	 *
	 * The brand columns (N-1.3) join through LATERAL rather than a plain LEFT JOIN,
	 * for a reason that is structural rather than stylistic: library.agency_id carries
	 * no unique constraint, so two library rows against one agency would DUPLICATE that
	 * agency in the directory — and the directory is what renders the login picker. A
	 * LATERAL with LIMIT 1 makes one agency one row by construction. LEFT keeps an
	 * agency with no library row in the directory, unbranded, which is the common case
	 * on a deployment that has not filled the fields in.
	 *
	 * Still ONE query. The lateral is an index lookup on library.agency_id per agency
	 * row — see V8_73_002 for the index — against a table bounded by the consortium's
	 * membership, not by the corpus.
	 */
	@Query(value = """
		SELECT a.code      AS code,
		       h.code      AS host_lms_code,
		       a.name      AS name,
		       a.latitude  AS latitude,
		       a.longitude AS longitude,
		       l.brand_logo_url     AS brand_logo_url,
		       l.brand_logo_alt     AS brand_logo_alt,
		       l.default_theme_name AS default_theme_name
		FROM agency a
		JOIN host_lms h ON a.host_lms_id = h.id
		LEFT JOIN LATERAL (
		  SELECT lib.brand_logo_url, lib.brand_logo_alt, lib.default_theme_name
		  FROM library lib
		  WHERE lib.agency_id = a.id
		  ORDER BY lib.id
		  LIMIT 1
		) l ON TRUE
		WHERE (:includeAll = true OR a.is_borrowing_agency IS TRUE)
		ORDER BY a.name
		""", nativeQuery = true)
	Publisher<LibraryDirectoryEntry> findLibraryDirectory(boolean includeAll);

	/** Exactly the columns the directory query selects. */
	@Introspected
	record LibraryDirectoryEntry(
		String code,
		String hostLmsCode,
		String name,
		@Nullable Double latitude,
		@Nullable Double longitude,
		@Nullable String brandLogoUrl,
		@Nullable String brandLogoAlt,
		@Nullable String defaultThemeName) {
	}

	Publisher<Void> delete(UUID id);

	Publisher<Void> deleteByCode(@NotNull String code);

	// Find the ID Of the HostLms for this repository. Wanted findHostLmsById but that seems to cause problems.
	Publisher<UUID> findHostLmsIdById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findHostLmsById(@NonNull UUID id);

	@Query(value = "SELECT * from agency where host_lms_id in (:hostLmsIds) order by name", nativeQuery = true)
	Publisher<DataAgency> findByHostLmsIds(@NonNull Collection<UUID> hostLmsIds);

	@Query(value = "SELECT host_lms_id from agency where code in (:agencyCodes) and host_lms_id is not null order by name", nativeQuery = true)
	Publisher<UUID> findHostLmsIdByAgencyCodes(@NonNull Collection<String> agencyCodes);
	
	@Query(value = "delete from agency where host_lms_id = :hostLmsId", nativeQuery = true)
	Publisher<Void> deleteByHostLmsId(@NonNull UUID hostLmsId);

	@SingleResult
	@NonNull
	default Publisher<DataAgency> saveOrUpdate(@Valid @NotNull @NonNull DataAgency agency) {
		return Mono.from(this.existsById(agency.getId()))
			.flux().concatMap(update -> Mono.from(update ? this.update(agency) : this.save(agency)));
	}
}
