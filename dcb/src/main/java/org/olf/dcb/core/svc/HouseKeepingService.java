package org.olf.dcb.core.svc;


import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.util.function.Tuples;
import reactor.util.function.Tuple2;

import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.core.HostLmsService;

import io.micrometer.core.annotation.Timed;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;

import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.core.model.Alarm;
import services.k_int.utils.UUIDUtils;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class HouseKeepingService {
	
	private final R2dbcOperations dbops;
  private final AlarmsService alarmsService;

	public HouseKeepingService(
		R2dbcOperations dbops,
		HostLmsService hostLmsService,
		HostLmsRepository hostLmsRepository,
		AlarmsService alarmsService) {

		this.dbops = dbops;
		this.hostLmsService = hostLmsService;
		this.hostLmsRepository = hostLmsRepository;
    this.alarmsService = alarmsService;
	}
	
	private static final String QUERY_POSTGRES_DEDUPE_MATCHPOINTS = "DELETE FROM match_point m WHERE EXISTS (\n"
			+ "	SELECT dupe.id as dupeId FROM (\n"
			+ "		SELECT id, bib_id, \"value\", row_number()\n"
			+ "			OVER(partition by bib_id, \"value\" order by value asc) AS row_num FROM match_point) dupe\n"
			+ "	WHERE dupe.row_num > 1 AND dupe.id = m.id\n"
			+ ");";

	private Mono<String> dedupe;
	private Mono<String> reprocess;
	private Mono<String> validateClusters;
	private final HostLmsRepository hostLmsRepository;
  private final HostLmsService hostLmsService;

  private static final int BATCH_SIZE = 10_000;

  private static final String QUERY_DUPLICATE_IDS = """
      SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY bib_id, "value" ORDER BY id) AS rn
        FROM match_point
      ) sub
      WHERE rn > 1
      LIMIT $1
      """;

	private static final String QUERY_SOURCE_RECORD_IDS = """
		SELECT id from source_record where processing_state != 'PROCESSING_REQUIRED'
    """;

  private static final String DELETE_BY_IDS = "DELETE FROM match_point WHERE id = ANY($1)";

  private static final String CLUSTER_VALIDATION_QUERY = """
    select id, selected_bib, date_updated 
    from cluster_record 
    where date_updated > $1 AND ( is_deleted is null or is_deleted = false )
    order by date_updated asc
    """;

  private static final String CLUSTER_BIB_IDENTIFIERS = """
    select c.id c_id, b.id b_id, mp.value id_val
    from cluster_record as c,
         bib_record as b,
         match_point as mp
    where b.contributes_to = c.id
      and mp.bib_id = b.id
      and c.id = $1
    order by b.date_created, mp.value
    """;

  private static final String BREAK_CLUSTER_ASSOCIATION = """
    update bib_record set contributes_to = null where id = $1
  """;
	
  // This has to be this way for now, until the new source uuid on bib_record is fully populated
  private static final String SET_REINDEX = """
    update source_record set processing_state = 'PROCESSING_REQUIRED' where id in (
    SELECT 
    FROM bib_record b
    JOIN source_record s
      ON s.remote_id LIKE '%' || b.source_record_id
    WHERE b.id = $1 )
  """;

  private static final String TOUCH_BIB_OWNING_CLUSTER = """
    update cluster_record set date_updated = now() where id in ( select br.contributes_to from bib_record br where br.d = $1 )
  """;

  // First mark any clusters that no longer have bibs as deleted (All bibs directed to other clusters - DELETED bibs is another case we need to service)
  private static final String PURGE_EMPTY_CLUSTERS = """
    update cluster_record cr set cr.is_deleted = true, cr.date_updated=now()  where cr.id in (
    select cr.id from cluster_record cr left outer join bib_record br on br.contributes_to = cr.id group by cr.id having count(br.id) = 0 );
  """;

  // Find any clusters where the bib was modified after the cluster and touch the cluster
  private static final String TOUCH_UPDATED_CLUSTERS = """
    update cluster_record cr set cr.date_updated = now() where cr.id in (
      select br.contributes_to from bib_record br, cluster_record cr where br.date_updated > cr.date_updated and br.contributes_to = cr.id )
  """;


  // select source_record_id srid from bib_record br where br.id = $1
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Mono<String> legacyDedupeMatchPoints() {
		if (dedupe == null) {
			synchronized (this) {
				if (dedupe == null) {
					dedupe = Mono.<String>create( report -> {
						Mono.from( dbops.withTransaction( status -> {
							return status.getConnection()
									.createStatement(QUERY_POSTGRES_DEDUPE_MATCHPOINTS)
									.execute();
							}))								
							.then()
							.doOnTerminate(() -> {
								dedupe = null;
								log.info("Finished MatchPoint Dedupe");
							})
							.doOnSubscribe(_s -> {
								report.success("In progress since [%s]".formatted(Instant.now()));
								log.info("Dedupe matchpoints");
							})
							.subscribe(_void -> {}, err -> {
								report.error(err);
								log.error("Error during dedupe of MatchPoints", err);
							});
						
					})
						.cache();
				}
			}
		} else {
			log.debug("Dedupe Matchpoints allready running. NOOP");
		}
		
		return dedupe;
	}

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Mono<String> dedupeMatchPoints() {
    if (dedupe == null) {
      synchronized (this) {
        if (dedupe == null) {
          dedupe = Mono.<String>create(report -> {
            log.info("Starting MatchPoint deduplication");
            report.success("Dedupe started at [%s]".formatted(Instant.now()));

            deleteDuplicatesBatch()
              .doOnTerminate(() -> {
                dedupe = null;
                log.info("Finished MatchPoint deduplication");
              })
              .subscribe();
          }).cache();
        }
      }
    } else {
      log.debug("Dedupe MatchPoints already running. NOOP");
    }

    return dedupe;
  }

  private Mono<Void> deleteDuplicatesBatch() {
    return Mono.from(
      dbops.withConnection(conn -> 
        Flux.from(conn.createStatement(QUERY_DUPLICATE_IDS)
          .bind("$1", BATCH_SIZE)
          .execute())
        .flatMap(result -> result.map((row, meta) -> row.get("id", Long.class)))
        .collectList()
        .flatMap(ids -> {
          if (ids.isEmpty()) {
            return Mono.empty(); // Done!
          }

          log.info("Deleting batch of {} duplicates", ids.size());

          return Mono.from(dbops.withTransaction(tx -> 
              Mono.from(tx.getConnection()
                  .createStatement(DELETE_BY_IDS)
                  .bind("$1", ids.toArray(new Long[0]))
                  .execute())
              .flatMap(r -> Mono.from(r.getRowsUpdated()))
              .then()
          )).then(deleteDuplicatesBatch()); // Recurse to process next batch
        })  
      )
    );
  }

  public Mono<String> reprocessAll() {
    log.info("reprocessAll");
    if (reprocess == null) {
      synchronized (this) {
        if (reprocess == null) {
          reprocess = Mono.<String>create(report -> {
            log.info("Starting source record reprocess");
            report.success("Dedupe started at [%s]".formatted(Instant.now()));

            reprocessQuery()
              .doOnTerminate(() -> {
                reprocess = null;
                log.info("Finished reprocess update");
              })
              .subscribe();
          }).cache();
        }
      }
    } else {
      log.debug("Reprocess running. NOOP");
    }

    return reprocess;
  }

  public Mono<String> validateClusters() {
    log.info("validateClusters");
    if (validateClusters == null) {
      synchronized (this) {
        if (validateClusters == null) {
          validateClusters = Mono.<String>create(report -> {
            log.info("Starting validateClusters");
            report.success("Dedupe started at [%s]".formatted(Instant.now()));
            innerValidateClusters()
              .doOnTerminate(() -> {
                validateClusters = null;
                log.info("Finished validate clusters");
              })
              .doOnNext(count -> log.info("### Validated {} clusters ###",count))
              .doOnError(error -> log.error("Error in innerValidateClusters:",error))
              .subscribe();
          }).cache();
        }
      }
    } else {
      log.debug("Reprocess running. NOOP");
    }
    return validateClusters;
  }

  private Mono<Long> innerValidateClusters() {
    Timestamp since = new Timestamp(0);

    log.info("innerValidateClusters");

    return Mono.from(
      dbops.withConnection(conn ->
        Flux.from(conn.createStatement(CLUSTER_VALIDATION_QUERY).bind("$1", since).execute())
          .doOnNext( n -> log.info("Progressing") )
          .doOnError(e -> log.error("Problem finding clusters to validate") )
          .flatMap(result -> result.map((row, meta) -> {
            UUID clusterId = row.get("id", UUID.class);
            UUID selectedBib = row.get("selected_bib", UUID.class);

            if ( ( clusterId != null ) && ( selectedBib != null ) )
              return Optional.of(Tuples.of(clusterId, selectedBib) );

            log.warn("Skipping missing clusterId {} or selectedBib {}",clusterId, selectedBib);
            return Optional.empty();
          }))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .cast(Tuple2.class) // Avoids generic inference failure
          .flatMap(tuple -> {
            @SuppressWarnings("unchecked")
            Tuple2<UUID, UUID> typedTuple = (Tuple2<UUID, UUID>) tuple;
            return validateCluster(typedTuple.getT1(), typedTuple.getT2());
          })
          .count()
          .doOnError(e -> log.error("Error in inner validate {}",e.getMessage(), e) )
          .doOnNext(c -> log.info("Processed {}",c))
          .then(
            // Find all the clusters that have zero bibs attached, and set their state to deleted, touch the date_updted
            Mono.from(
              conn
                .createStatement(PURGE_EMPTY_CLUSTERS)
                .execute())
            .flatMap(result -> Mono.from(result.getRowsUpdated())))
          .then(
            // Find all the clusters that have a member bib with a date_updated > the cluster, and touch the cluster
            Mono.from(
              conn
                .createStatement(TOUCH_UPDATED_CLUSTERS)
                .execute())
            .flatMap(result -> Mono.from(result.getRowsUpdated())))

      )
    );
  }

  private Mono<Void> validateCluster(UUID clusterId, UUID selectedBib) {

    // log.info("Process cluster {}", clusterId);

    return Mono.from(
      dbops.withConnection(conn ->
        Flux.from(conn
          .createStatement(CLUSTER_BIB_IDENTIFIERS)
            .bind("$1", clusterId)
            .execute())
          .flatMap(result -> result.map((row, meta) -> {
            UUID bibId = row.get("b_id", UUID.class);
            String idVal = row.get("id_val", String.class);
            return Map.entry(bibId, idVal);
          }))
          .collect(Collectors.groupingBy(
            Map.Entry::getKey,
            Collectors.mapping(Map.Entry::getValue, Collectors.toSet())
          ))
      )
    ).flatMapMany(bibIdToIdentifiersMap -> {
      // log.info("bibIdentifierMap {}", bibIdToIdentifiersMap);

      Set<String> idset = new HashSet<>();
      idset.addAll(bibIdToIdentifiersMap.getOrDefault(selectedBib, Set.of()));

      // Emit the bibs that need cleanup
      return Flux.fromIterable(bibIdToIdentifiersMap.entrySet())
        .filter(entry -> {
          UUID bibId = entry.getKey();
          if (bibId.equals(selectedBib)) return false;
          Set<String> identifiers = entry.getValue();
          Set<String> intersection = new HashSet<>(identifiers);
          intersection.retainAll(idset);

          // log.info("Intersection: {}",intersection.size());

          if ( intersection.size() < 2 ) {
            // Pass the filter - this should be removed
            log.info("Cleanup... ids={} clusterids={}",identifiers,idset);
            return true;
          }
          else {
            // this should be clustered.. Add it's IDs to the idset to grow the cluster scope
            idset.addAll(identifiers);
            // log.info("Retaining {} and adding ids to idset {}",bibId,idset);
            return false;
          }
        })
        .map(entry -> {
          UUID bibToCleanup = entry.getKey();
          log.info("Bib {} should be cleaned up", bibToCleanup);
          return bibToCleanup;
        });
    })
    .flatMap ( bibToCleanup -> {
      return cleanupBib(bibToCleanup);
    })
    .then();
  }

  private Mono<Void> cleanupBib(UUID bibId) {
    log.info("### Clean up bib {} ###", bibId);

    return Mono.from(dbops.withTransaction(status ->

			// Touch the cluster that owns this bib - we are removing something from it
      Mono.from(
        status.getConnection()
          .createStatement(TOUCH_BIB_OWNING_CLUSTER)
          .bind("$1", bibId)
          .execute())

      .then(
        Mono.from(
          status.getConnection()
            .createStatement(SET_REINDEX)
            .bind("$1", bibId)
            .execute())
        .flatMap(result -> Mono.from(result.getRowsUpdated())))

      .doOnNext(c -> log.info("Completed setting reindex for {} {} rows", bibId, c))
      .doOnError(e -> log.error("Problem setting reindex for bib {} - {}",bibId,e.getMessage(),e) )

      .then(
        Mono.from(
          status.getConnection()
            .createStatement(BREAK_CLUSTER_ASSOCIATION)
            .bind("$1", bibId)
            .execute())
          .flatMap(result -> Mono.from(result.getRowsUpdated()))) // Fix here

      .doOnNext(c -> log.info("Completed breaking cluster association for {} {} rows", bibId, c))
      .doOnError(e -> log.error("Problem breaking cluster association for bib {} - {}",bibId,e.getMessage(),e) )
    ))
    .onErrorResume(e -> {
      log.warn("Resume after error trying to clean up bib {}",bibId);
      return Mono.empty();
    })
    .then(); // Return Mono<Void>
  }

  private Mono<Void> reprocessQuery() {
    log.info("Running reprocessQuery");
		AtomicInteger page = new AtomicInteger(0);
    return Mono.from(
      dbops.withConnection(conn ->
        Flux.from(conn.createStatement(QUERY_SOURCE_RECORD_IDS).execute())
        .flatMap(result -> result.map((row, meta) -> row.get("id", UUID.class)))
				.buffer(10000)
				.concatMap(batch -> updateSourceRecordBatch(batch, page.incrementAndGet()))
      )
    );
  }

	public Mono<Void> updateSourceRecordBatch(List<UUID> batch, long pageno) {
    log.info("Update source batch {} - pageno {}",batch.size(),pageno);
		return Mono.from(dbops.withTransaction(tx -> 
			Mono.from(
				tx.getConnection()
        	.createStatement("update source_record set processing_state = 'PROCESSING_REQUIRED' where id = ANY($1)")
        	.bind("$1", batch.toArray(new UUID[0]))
        	.execute()
    	)
    	.flatMap(result -> Mono.from(result.getRowsUpdated()))
    	.then()
  	));
	}


  @Timed("tracking.run")
  @AppTask
  @Scheduled(initialDelay = "2m", fixedDelay = "${dcb.task.housekeeping.interval:24h}")
  public Mono<String> initiateAudit() {
		log.info("Starting audit process");
		audit().subscribe();
		log.info("Started audit process");

		return Mono.just("Started");
	}

	public static interface AuditTask {
    Mono<String> run();
	}

  public Mono<Void> audit() {

		List<AuditTask> checks = List.of( 
			() -> pingTests(),
			() -> systemsLibrarianContactableTests()
		);

		log.info("Audit....");
		return Flux.fromIterable(checks)
	    .concatMap(fn -> 
        fn.run()
					.onErrorResume(ex -> {
						log.error("Audit check [{}] failed with exception", fn, ex);
						return Mono.empty();
					})
					.doOnNext(result -> log.info("Audit [{}] completed", fn))
			)
			.collectList()
	    .doOnSuccess(results -> log.info("All audit checks completed."))
			.then();

		// Audit should run the following checks and raise alarms if any of the conditions are met
			// Any hostlms where a simple connection test fails
				// Code - HostLms.code.NoConnection ( Lasts until cleared )
			// Any Libraries that point to an agency that does not exist
				// Code - Library.{code}.InvalidAgency ( Lasts until cleared )
			// Any Agencies that are missing core patron type mappings for CIRC, NOCIRC, CIRCAV (And any extended list of required mappings)
				// Code - Agency.{code}.NoMapping.Patron.{type} ( Lasts until cleared )
			// Any Agencies that are missinig core item type mappings for ...
				// Code - Agency.{code}.NoMapping.Item.{type}
	}

	private Mono<String> pingTests() {
    return Flux.from(hostLmsRepository.queryAll())
      .flatMap( hostLms -> hostLmsService.getClientFor(hostLms) )
      .doOnNext( hostLmsClient -> log.info("Audit -- LMS connectivity test: {}",hostLmsClient.getHostLmsCode()))
      .flatMap( hostLmsClient -> hostLmsClient.ping() )
			.onErrorResume( e -> {
				log.error("Problem in ping test {}",e.getMessage());
				return Mono.empty();
			})
			.flatMap ( pingResponse -> {
				log.info("Ping Response {}",pingResponse );

				String alarmCode = "ILS."+pingResponse.getTarget()+".PING_FAILURE".toUpperCase();

				if ( pingResponse.getStatus().equals("OK") ) {
					return alarmsService.cancel(alarmCode)
						.thenReturn("OK");
				}
				else {
					return alarmsService.raise(
						Alarm.builder()
							.id(UUIDUtils.generateAlarmId(alarmCode))
							.code(alarmCode)
							.build()
						)
						.thenReturn( pingResponse );
				}
			})
			.collectList()
      .thenReturn("OK");
	}

  private Mono<String> systemsLibrarianContactableTests() {
    return Flux.from(hostLmsRepository.queryAll())
      .doOnNext( hostLmsData -> log.info("Audit -- Systems librarian contact checks: {}",hostLmsData.getCode()))
      .flatMap ( hostLmsData -> {

				String alarmCode = "ILS."+hostLmsData.getCode()+".NO_SYSTEMS_EMAIL".toUpperCase();

				if ( ( hostLmsData.getClientConfig() != null ) &&
						 ( hostLmsData.getClientConfig().containsKey("systemsLibrarianContactEmail") ) ) {
					// Pass
					return alarmsService.cancel(alarmCode)
						.thenReturn(Mono.just("OK"));
				}
				else {
          return alarmsService.raise(
            Alarm.builder()
              .id(UUIDUtils.generateAlarmId(alarmCode))
              .code(alarmCode)
              .build()
          )
          .thenReturn( Mono.just("FAIL") );
				}
			})
      .collectList()
      .thenReturn("OK");
  }

	
}
