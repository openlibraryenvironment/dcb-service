package org.olf.dcb.core.svc;


import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.scheduling.annotation.Scheduled;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.core.HostLmsService;

import io.micrometer.core.annotation.Timed;
import services.k_int.federation.reactor.ReactorFederatedLockService;
import services.k_int.micronaut.scheduling.processor.AppTask;

import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.Syslog;
import services.k_int.utils.UUIDUtils;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class HouseKeepingService {
	
	private final R2dbcOperations dbops;
  private final AlarmsService alarmsService;
  private final SyslogService syslogService;

	public HouseKeepingService(
		R2dbcOperations dbops,
		HostLmsService hostLmsService,
		HostLmsRepository hostLmsRepository,
		AlarmsService alarmsService,
		SyslogService syslogService) {

		this.dbops = dbops;
		this.hostLmsService = hostLmsService;
		this.hostLmsRepository = hostLmsRepository;
    this.alarmsService = alarmsService;
    this.syslogService = syslogService;
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
  private Map<String,Object> reprocessStatusReport = new HashMap<String,Object>();

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
    SELECT id
    FROM source_record
    WHERE ( processing_state = 'SUCCESS' AND last_processed < $1 )
    OR ( processing_state = 'FAILURE' )
    FOR SHARE SKIP LOCKED
    LIMIT 25000
    """;

	private static final String COUNT_QUERY_SOURCE_RECORD_IDS = """
    SELECT count(id) srcount
    FROM source_record
    WHERE ( processing_state = 'SUCCESS' AND last_processed < $1 )
    OR ( processing_state = 'FAILURE' )
    """;

  private static final String DELETE_BY_IDS = "DELETE FROM match_point WHERE id = ANY($1)";

  private static final String SINGLE_CLUSTER_VALIDATION_QUERY = """
    select id, selected_bib, date_updated 
    from cluster_record 
    where id  = $1
    """;

  private static final String CLUSTER_VALIDATION_QUERY = """
    select id, selected_bib, date_updated 
    from cluster_record 
    where date_updated > $1 AND ( is_deleted is null or is_deleted = false )
    order by date_updated asc
    limit 5000
    """;

  private static final String CLUSTER_BIBS = """
    SELECT b.id AS b_id
    FROM bib_record b
    WHERE b.contributes_to = $1
  """;
  private static final String CLUSTER_BIB_IDENTIFIERS = """
    SELECT b.contributes_to AS c_id, b.id AS b_id, mp.value AS id_val
    FROM bib_record b
    JOIN match_point mp ON mp.bib_id = b.id
    WHERE b.contributes_to = $1
    """;
  //   select c.id c_id, b.id b_id, mp.value id_val
  //   from cluster_record as c,
  //        bib_record as b,
  //        match_point as mp
  //   where b.contributes_to = c.id
  //     and mp.bib_id = b.id
  //     and c.id = $1
  //   order by b.date_created, mp.value

  private static final String BREAK_CLUSTER_ASSOCIATION = """
    update bib_record set contributes_to = null where id = $1
  """;
	
  // ToDo: This is killing performance - we need to get source_uuid on bib_record populated asap
  // This has to be this way for now, until the new source uuid on bib_record is fully populated
  private static final String SET_REINDEX = """
    update source_record set processing_state = 'PROCESSING_REQUIRED' where id in (
    SELECT s.id
    FROM bib_record b
    JOIN source_record s
      ON s.remote_id LIKE '%' || b.source_record_id
    WHERE b.id = $1 )
  """;

  private static final String SET_REINDEX_WITH_SOURCE_RECORD_UUID = """
    update source_record set processing_state = 'PROCESSING_REQUIRED' 
    where id in ( select b.source_record_uuid from bib_record b where b.id = $1 )
  """;

  private static final String TOUCH_BIB_OWNING_CLUSTER = """
    update cluster_record set date_updated = now() where id in ( select br.contributes_to from bib_record br where br.d = $1 )
  """;

  // First mark any clusters that no longer have bibs as deleted (All bibs directed to other clusters - DELETED bibs is another case we need to service)
  private static final String PURGE_EMPTY_CLUSTERS = """
    update cluster_record set is_deleted = true, date_updated=now()  where id in (
    select cr.id from cluster_record cr left outer join bib_record br on br.contributes_to = cr.id group by cr.id having count(br.id) = 0 );
  """;

  // Find any clusters where the bib was modified after the cluster and touch the cluster
  private static final String TOUCH_UPDATED_CLUSTERS = """
    update cluster_record set date_updated = now() where id in (
      select br.contributes_to from bib_record br, cluster_record cr where br.date_updated > cr.date_updated and br.contributes_to = cr.id )
  """;

  // Used for an alarm which is set if there are bib records where source_record_uuid is null
  private static final String ALARM_BIB_SOURCE_IDS = """
    SELECT total_rows, populated_rows, total_rows - populated_rows AS null_rows
    FROM (
      SELECT COUNT(*) AS total_rows, COUNT(source_record_uuid) AS populated_rows
      FROM bib_record
    ) AS counts
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

  public Mono<String> reprocess(String criteria) {
    // Instant startts = Instant.now().minus(5, ChronoUnit.DAYS);
		return reprocess(Instant.now(), criteria != null ? criteria : "ALL" );
	}

  public Mono<String> reprocess(Instant startts, String criteria) {
    log.info("reprocessAll");
    if (reprocess == null) {
      synchronized (this) {
        if (reprocess == null) {

          reprocessStatusReport.put("status","Running");
          reprocessStatusReport.put("startTime",Instant.now().toString());

          reprocess = Mono.<String>create(report -> {
            log.info("Starting source record reprocess");
            report.success("Reprocessing started at [%s]".formatted(Instant.now()));


            estimateReprocessRunTime(startts, criteria)
							.then(syslogService.log(
								Syslog.builder()
									.category("reindex")
									.message("Started")
									.detail("instance", syslogService.getSystemInstanceId())
									.build()
							))
              .then(reprocessQuery(startts, criteria))
              .doOnTerminate(() -> {
                reprocess = null;
                reprocessStatusReport.clear();
                reprocessStatusReport.put("status","Not Active");
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

  public Mono<String> reprocessClusterBibs(UUID clusterId) {
    log.info("reprocessClusterBibs "+clusterId);
		AtomicInteger page = new AtomicInteger(0);
    return Mono.from(
      dbops.withConnection(conn ->
        Flux.from(conn.createStatement(CLUSTER_BIBS).bind("$1", clusterId).execute())
          .flatMap(result -> result.map((row, meta) -> { return row.get("b_id", UUID.class); }))
          .collectList()
          .map( batch -> updateSourceRecordBatch(batch, page.get()))
          .thenReturn("Completed")
      )

    );
  }

  public Mono<String> validateSingleCluster(UUID clusterId) {
    AtomicInteger counter = new AtomicInteger(0);
    AtomicInteger changedClusterCounter = new AtomicInteger(0);

    return Mono.from(
      dbops.withConnection(conn ->
        Mono.from(conn.createStatement(SINGLE_CLUSTER_VALIDATION_QUERY) .bind("$1", clusterId) .execute())
          .flatMap(result -> Mono.from( result.map((row, meta) -> {
            UUID selectedBib = row.get("selected_bib", UUID.class);
            Instant updated = row.get("date_updated", Instant.class);
  
            if (clusterId != null && selectedBib != null) {
              return Tuples.of(clusterId, selectedBib, updated);
            } else {
              log.warn("Skipping invalid row with clusterId {} or selectedBib {}", clusterId, selectedBib);
              return null; // temporarily allow null, will filter below
            }
          })))
         .filter(Objects::nonNull)
         .flatMap(tuple -> processSingleCluster(tuple, counter, changedClusterCounter, System.currentTimeMillis()))
         .thenReturn("OK")
       )
    );
  }


  public Mono<String> validateClusters() {
    log.info("validateClusters");
    if (validateClusters == null) {
      synchronized (this) {
        if (validateClusters == null) {
          validateClusters = Mono.<String>create(report -> {
            log.info("Starting validateClusters");
            report.success("Validate Clusters started at [%s]".formatted(Instant.now()));

						syslogService.log(
							Syslog.builder()
								.category("validateClusters")
								.message("Started")
								.detail("instance", syslogService.getSystemInstanceId())
								.build()
						)
              .then(innerValidateClusters())
   						.doOnNext(count-> {
                log.info("### Validated {} clusters ###",count);
				        syslogService.log(
				          Syslog.builder()
       				     .category("validateClusters")
				           .message("Completed")
       				     .detail("instance", syslogService.getSystemInstanceId())
				           .detail("count", count)
				           .build()
				        ).subscribe();
              })
              .doOnTerminate(() -> {
                validateClusters = null;
                log.info("Finished validate clusters");
              })
				      .onErrorResume( e -> {
                log.error("Problem in validate",e);
				        return syslogService.log(
       				   Syslog.builder()
				           .category("validateClusters")
       				     .message("ERROR "+e.getMessage())
				           .detail("instance", syslogService.getSystemInstanceId())
       				     .detail("error", e.toString())
				           .build()
       				  )
                .then(Mono.error(e instanceof RuntimeException ? e : new RuntimeException(e)));
							})
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
    log.info("innerValidateClusters");

    AtomicInteger changedClusterCount = new AtomicInteger(0);
    AtomicInteger recordCounter = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    return processClusterBatches(Instant.ofEpochSecond(0), recordCounter, changedClusterCount, startTime)
        .doOnNext(c -> log.info("Total clusters validated: {} changed:{}", c, changedClusterCount.get()))
        .flatMap(count -> purgeEmptyClusters()
            .then(touchUpdatedClusters())
            .thenReturn(count)
        );
  }

	private Mono<Long> processClusterBatches(Instant since, AtomicInteger counter, AtomicInteger changedClusterCounter, long startTime) {
		log.info("Executing processClusterBatches with since={}, counter={}, startTime={}", since, counter, startTime);

		return getClusterBatch(since)
			.flatMapMany(Flux::fromIterable)
			.flatMap(tuple -> Mono.defer(() -> processSingleCluster(tuple, counter, changedClusterCounter, startTime)).publishOn(Schedulers.boundedElastic()), 10)
			.collectList()
			.flatMap(processed -> {
				if (processed.isEmpty()) {
					log.info("No more clusters to process. Returning count: {}", counter.get());
					return Mono.just((long) counter.get());
				} else {
					Instant last = processed.get(processed.size() - 1).getT3();
					log.info("Batch processed, continuing with next batch since={}", last);
					return processClusterBatches(last, counter, changedClusterCounter, startTime);
				}
			});
	}

	private Mono<List<Tuple3<UUID, UUID, Instant>>> getClusterBatch(Instant since) {
		log.info("Executing getClusterBatch with since={} for Validate clusters", since);

		// Use defer to avoid eager evaluation and make type inference happy
		return Flux.defer(() ->
			dbops.withConnection(conn ->
				Flux.from(conn.createStatement(CLUSTER_VALIDATION_QUERY)
					.bind("$1", since)
					.execute())
					.flatMap(result -> result.map((row, meta) -> {
						UUID clusterId = row.get("id", UUID.class);
						UUID selectedBib = row.get("selected_bib", UUID.class);
						Instant updated = row.get("date_updated", Instant.class);
	
						if (clusterId != null && selectedBib != null) {
							return Optional.of(Tuples.of(clusterId, selectedBib, updated));
						} else {
							log.warn("Skipping invalid row with clusterId {} or selectedBib {}", clusterId, selectedBib);
							return Optional.empty();
						}
					}))
					.filter(Optional::isPresent)
					.map(Optional::get)
          .map(obj -> (Tuple3<UUID, UUID, Instant>) obj)
			)
		).collectList(); // Moved outside the withConnection to satisfy Mono<List<...>>
	}

	private Mono<Tuple3<UUID, UUID, Instant>> processSingleCluster(
		Tuple3<UUID, UUID, Instant> tuple, 
		AtomicInteger counter, 
    AtomicInteger changedClusterCounter,
		long startTime) {

		UUID clusterId = tuple.getT1();
		UUID selectedBib = tuple.getT2();
		log.info("Executing processSingleCluster with clusterId={}, selectedBib={} ", clusterId, selectedBib);

		return validateCluster(clusterId, selectedBib, changedClusterCounter)
			.doOnSuccess(ignored -> {
				int rc = counter.incrementAndGet();
				long elapsed = System.currentTimeMillis() - startTime;
				if (rc % 1000 == 0) {
					log.info("Validated {} clusters in {} ms (avg = {} ms) changed:{}", rc, elapsed, (elapsed / rc), changedClusterCounter.get());
				}
				if (rc % 100000 == 0) {
	        syslogService.log(
				    Syslog.builder()
       				 .category("validateClusters")
				       .message(String.format("status update: Validated %d clusters in %d ms (avg = %d ms) changed:%d",rc, elapsed, (elapsed / rc), changedClusterCounter.get()))
       				 .detail("instance", syslogService.getSystemInstanceId())
				       .build()
				  ).subscribe();
				}
			})
			.thenReturn(tuple);
	}	

  private Mono<Void> purgeEmptyClusters() {
    return Mono.from(dbops.withConnection(conn ->
        Mono.from(conn.createStatement(PURGE_EMPTY_CLUSTERS).execute())
            .flatMap(result -> Mono.from(result.getRowsUpdated()))
    )).then();
  }

  private Mono<Void> touchUpdatedClusters() {
    return Mono.from(dbops.withConnection(conn ->
        Mono.from(conn.createStatement(TOUCH_UPDATED_CLUSTERS).execute())
            .flatMap(result -> Mono.from(result.getRowsUpdated()))
    )).then();
  }

  private Mono<Void> validateCluster(UUID clusterId, UUID selectedBib, AtomicInteger changedClusterCounter) {

    log.info("Process cluster {}", clusterId);

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
      log.info("bibIdentifierMap {}", bibIdToIdentifiersMap);

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
      changedClusterCounter.getAndIncrement();
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

      .doOnError(e -> log.error("Problem with TOUCH_BIB_OWNING_CLUSTER",e))
      .doOnNext(n -> log.info("SET_REINDEX") )

      .then(
        Mono.from(
          status.getConnection()
            .createStatement(SET_REINDEX_WITH_SOURCE_RECORD_UUID)
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

  private Mono<Void> estimateReprocessRunTime(Instant startts, String criteria) {

    String reprocess_count_query = switch(criteria) {
      case "ALL" -> COUNT_QUERY_SOURCE_RECORD_IDS;
      default -> COUNT_QUERY_SOURCE_RECORD_IDS;
    };

		return Mono.from(dbops.withTransaction(tx -> 
      dbops.withConnection(conn ->
				tx.getConnection()
        	.createStatement(reprocess_count_query)
          .bind(0, startts)
        	.execute()
    	)))
    	.flatMap(result ->  Mono.from(result.map((row, meta) -> row.get("srcount", String.class))))
      .flatMap(reccount -> {
        return Mono.empty();
      })
      .then();
  }

  private Mono<Void> reprocessQuery(Instant startts, String criteria) {
    log.info("Running reprocessQuery startts={},criteria={}",startts,criteria);
		AtomicInteger page = new AtomicInteger(0);
		AtomicInteger recordCount = new AtomicInteger(0);
    // Grab the start time
    // Lets not process anything less than three days old

		return Flux.defer(() -> Mono.just(0)) // dummy trigger
			.expandDeep(ignore -> 
        dbops.withConnection(conn ->
          getNextBatch(conn, startts, criteria)
          .flatMap(result -> result.map((row, meta) -> row.get("id", UUID.class)))
          .collectList()
          .flatMap(batch -> {

            reprocessStatusReport.put("page",page.toString());
            reprocessStatusReport.put("fetchedRecordCount",""+recordCount.addAndGet(batch.size()));

            if (batch.isEmpty()) return Mono.empty(); // done
            page.incrementAndGet();

            // We don't want this long running update to block other work - so publish it on bounded elastic scheduler
            return Mono.defer(() -> updateSourceRecordBatch(batch, page.get()))
              .subscribeOn(Schedulers.boundedElastic())
              .thenReturn(0); // dummy value to trigger next iteration
          })  // Set concurrency of update to 3
          .doOnError(e -> log.error("Problem processing batch",e) )
        )
      )
			.then(
				syslogService.log(
					Syslog.builder()
						.category("reindex")
						.message("Completed")
						.detail("instance", syslogService.getSystemInstanceId())
						.detail("pageCount", page.get())
						.detail("recordCount", recordCount.get())
						.build()
				)
			)
			.onErrorResume( e -> {
				return syslogService.log(
	        Syslog.builder()
		        .category("reindex")
			      .message("ERROR "+e.getMessage())
				    .detail("instance", syslogService.getSystemInstanceId())
					  .detail("pageCount", page.get())
						.detail("recordCount", recordCount.get())
						.detail("error", e.toString())
	          .build()
				)
				.then(Mono.error(e instanceof RuntimeException ? e : new RuntimeException(e)));
			})
      .doFinally(signalType -> log.info("reprocessQuery terminated with signal: {}", signalType))
			.then();
  }

	public Mono<Void> updateSourceRecordBatch(List<UUID> batch, long pageno) {
    log.info("Update source batch {} - pageno {}",batch.size(),pageno);
		return Mono.from(dbops.withTransaction(tx -> 
			Mono.from(
				tx.getConnection()
        	.createStatement("update source_record set processing_state = 'PROCESSING_REQUIRED', date_updated=now() where id = ANY($1)")
        	.bind("$1", batch.toArray(new UUID[0]))
        	.execute()
    	)
    	.flatMap(result -> Mono.from(result.getRowsUpdated()))
      .doOnNext( r -> log.info("completed pageno {} - count={}",pageno,r) )
      .doOnError(e -> log.error("Problem updating batch",e) )
    	.then()
  	));
	}

  private Flux<? extends Result> getNextBatch(Connection conn, Instant startts, String criteria) {
    log.info("Get next Batch - startts={},criteria={}",startts,criteria);

    String reprocess_query = switch(criteria) {
      case "ALL" -> QUERY_SOURCE_RECORD_IDS;
      default -> QUERY_SOURCE_RECORD_IDS;
    };

    return Flux.from(conn.createStatement(reprocess_query)
      .bind(0, startts)
      .execute());
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
			() -> systemsLibrarianContactableTests(),
      () -> bibsWithoutSourceRecordUUID()
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

	private Mono<String> bibsWithoutSourceRecordUUID() {
	  return Mono.from(dbops.withConnection(conn ->
  	  Mono.from(conn.createStatement(ALARM_BIB_SOURCE_IDS).execute())
    	  .flatMap(result -> Mono.from(result.map((row, meta) -> {
      	  Long nulls = row.get("null_rows", Long.class);
        	Long total = row.get("total_rows", Long.class);
	        Long populated = row.get("populated_rows", Long.class);
  	      return Tuples.of(nulls, total, populated);
    	  })))
	      .flatMap(tuple -> {
  	      Long nulls = tuple.getT1();
    	    Long total = tuple.getT2();
      	  Long populated = tuple.getT3();
        	String code = "SYSTEM.BIBS_WITHOUT_SOURCE_ID";

	        if (nulls != null && nulls > 0) {
  	        return alarmsService.raise(
    	          Alarm.builder()
      	          .id(UUIDUtils.generateAlarmId(code))
        	        .code(code)
          	      .alarmDetail("Count", Map.of(
            	      "total", total,
              	    "with source record uuid", populated,
                	  "without source record uuid", nulls))
	                .build())
  	          .thenReturn("Bib records without source record UUID: " + nulls);
    	    } else {
      	    return alarmsService.cancel(code).thenReturn("OK");
        	}
	      })
  ));
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

  public Map getReprocessStatus() {
    return reprocessStatusReport;
  }
	
}
