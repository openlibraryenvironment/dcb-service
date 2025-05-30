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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.core.HostLmsService;

import io.micrometer.core.annotation.Timed;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;

import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.core.model.Alarm;
import services.k_int.utils.UUIDUtils;


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
