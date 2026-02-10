package org.olf.dcb.storage.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import jakarta.transaction.Transactional;

import org.olf.dcb.core.api.serde.DailyPatronRequestStat;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import reactor.core.publisher.Flux;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresPatronRequestRepository extends 
        ReactiveStreamsPageableRepository<PatronRequest, UUID>, 
        ReactiveStreamsJpaSpecificationExecutor<PatronRequest>,
        PatronRequestRepository {
	
	// TODO: This currently ignores the values passed in.... We need to fix that,
	// but the expansion seems to count each individual item in the expansion as a
	// variable instead of all counting to one. Might be fixed with MN update but defer till later.
	@Query(value = 
			"SELECT DISTINCT pr.* FROM patron_request pr"
			+ " INNER JOIN supplier_request sr ON pr.id = sr.patron_request_id "
			+ "WHERE pr.status_code not in ('ERROR', 'FINALISED', 'COMPLETED')"
			+ "  AND  sr.local_status <> 'MISSING' -- Includes Nulls"
			+ "  AND ("
			+ "    sr.local_item_status IS NULL "
			+ "	   OR sr.local_item_status IN ( select code from status_code where tracked = true )"
			+ "	   OR sr.status_code in ( select code from status_code where tracked = true ))", readOnly = true, nativeQuery = true)
	@Join("supplierRequests")
	@Override
	Publisher<PatronRequest> findAllTrackableRequests( @Expandable Iterable<Status> terminalStates, @Expandable Iterable<String> supplierStatuses, @Expandable Iterable<String> supplierItemStatuses);

	// Borrowers (Really anything that isn't a supplier)
	@Query(value = "SELECT " +
		"  DATE(pr.date_created) as date_created, " +
		"  COUNT(*) as count, " +
		"  pr.patron_hostlms_code, " +
		"  pr.pickup_location_code, " +
		"  pr.status_code, " +
		"  pr.bib_cluster_id " +
		"FROM patron_request pr " +
		"WHERE (:startDate IS NULL OR pr.date_created >= :startDate) " +
		"  AND (:endDate IS NULL OR pr.date_created <= :endDate) " +
		"  AND (:borrowerCodes IS NULL OR pr.patron_hostlms_code IN (:borrowerCodes)) " +
		"  AND (:pickupCodes IS NULL OR pr.pickup_location_code IN (:pickupCodes)) " +
		"  AND (:statusCodes IS NULL OR pr.status_code IN (:statusCodes)) " +
		"  AND (:bibClusterId IS NULL OR pr.bib_cluster_id = :bibClusterId) " +
		"GROUP BY " +
		"  DATE(pr.date_created), " +
		"  pr.patron_hostlms_code, " +
		"  pr.pickup_location_code, " +
		"  pr.status_code, " +
		"  pr.bib_cluster_id " +
		"ORDER BY date_created ASC",
		readOnly = true,
		nativeQuery = true)
	Flux<DailyPatronRequestStat> findDailyBorrowerStats(
		@Nullable Instant startDate,
		@Nullable Instant endDate,
		@Nullable List<String> borrowerCodes,
		@Nullable List<String> pickupCodes,
		@Nullable List<String> statusCodes,
		@Nullable UUID bibClusterId
	);

	// Suppliers
	@Query(value = "SELECT " +
		"  DATE(pr.date_created) as date_created, " +
		"  COUNT(DISTINCT pr.id) as count, " +
		"  pr.patron_hostlms_code, " +
		"  pr.pickup_location_code, " +
		"  pr.status_code, " +
		"  pr.bib_cluster_id " +
		"FROM patron_request pr " +
		"INNER JOIN supplier_request sr ON pr.id = sr.patron_request_id " +
		"WHERE (:startDate IS NULL OR pr.date_created >= :startDate) " +
		"  AND (:endDate IS NULL OR pr.date_created <= :endDate) " +
		"  AND sr.local_agency IN (:supplierCodes) " +
		"  AND (:borrowerCodes IS NULL OR pr.patron_hostlms_code IN (:borrowerCodes)) " +
		"  AND (:pickupCodes IS NULL OR pr.pickup_location_code IN (:pickupCodes)) " +
		"  AND (:statusCodes IS NULL OR pr.status_code IN (:statusCodes)) " +
		"  AND (:bibClusterId IS NULL OR pr.bib_cluster_id = :bibClusterId) " +
		"GROUP BY " +
		"  DATE(pr.date_created), " +
		"  pr.patron_hostlms_code, " +
		"  pr.pickup_location_code, " +
		"  pr.status_code, " +
		"  pr.bib_cluster_id " +
		"ORDER BY date_created ASC",
		readOnly = true,
		nativeQuery = true)
	Flux<DailyPatronRequestStat> findDailySupplierStats(
		@Nullable Instant startDate,
		@Nullable Instant endDate,
		List<String> supplierCodes,
		@Nullable List<String> borrowerCodes,
		@Nullable List<String> pickupCodes,
		@Nullable List<String> statusCodes,
		@Nullable UUID bibClusterId
	);

}
