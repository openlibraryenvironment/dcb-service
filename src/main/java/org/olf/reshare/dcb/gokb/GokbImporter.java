package org.olf.reshare.dcb.gokb;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.transaction.Transactional;

import org.olf.reshare.dcb.ImportedRecordBuilder;
import org.olf.reshare.dcb.bib.BibRecordService;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.retry.event.RetryEventListener;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.gokb.GokbApiClient;
import services.k_int.interaction.gokb.GokbTipp;
import services.k_int.micronaut.scheduling.processor.AppTask;

@Singleton
@Requires(property = (GokbImporter.CONFIG_ROOT + ".enabled"), value = "true")
public class GokbImporter implements Runnable, RetryEventListener {

	public static final String CONFIG_ROOT = "gokb.import";
	
	private Disposable mutex = null;
	private Instant lastRun = null;
	
	private static Logger log = LoggerFactory.getLogger(GokbImporter.class);
	private final GokbApiClient gokbapi;
	private final BibRecordService bibRecordService;

	GokbImporter(GokbApiClient gokbapi, BibRecordService bibRecordService) {
		this.gokbapi = gokbapi;
		this.bibRecordService = bibRecordService;
	}

	
	private Publisher<GokbTipp> scrollAllResults(final String scrollId, final Instant lastRun) {
		log.info("Fetching scroll page from Gokb");
		return Mono.from(gokbapi.scrollTipps(scrollId, lastRun))
				.filter(resp -> "OK".equalsIgnoreCase(resp.result()) && resp.size() > 0)
				.flatMapMany(resp -> {
					
					log.info("Fetched a chunk of {} records", resp.size());
					// We already have results. If there are more results then we create a
					// new Stream from these results plus the result of recursing this
					// method with the scroll id the gokb api will supply the next page of
					// results. Doing this here effectively chunks a single stream into
					// the 5000 element pages that gokb supplies, but removes the onus 
					// of paging from the subscriber.
					
					final Flux<GokbTipp> currentPage = Flux.fromIterable(resp.records());
					final boolean more = resp.hasMoreRecords();
					
					
					if (!more) {
						log.info("No more results to fecth");
					}
					
					// FOrce a delay to prevent duplicate result pages from GOKb.
					return more ? Flux.concat(currentPage, scrollAllResults(resp.scrollId(), lastRun)) : currentPage;
				});
	}
	
	private Runnable cleanUp( final Instant i ) {
		return () -> {
			lastRun = i;
			mutex = null;
		};
	}

	@Override
	@Scheduled(initialDelay = "2s",fixedDelay = "90s")
	@AppTask
	@Transactional
	public void run() {

		if (mutex != null) {
			log.info("GOKb import already runnning. Skip this invokation.");
			return;
		}
		
		log.info("Scheduled GOKb import");
		
		final long start = System.currentTimeMillis();
		log.info("Now {}", Instant.ofEpochMilli(start).truncatedTo(ChronoUnit.SECONDS)) ;
		// Client stream
		mutex = Flux.from(scrollAllResults(null, lastRun))
				.name("gokp-tipps")
				.filter(tipp -> tipp.tippTitleName() != null)
				.map(tipp -> ImportedRecordBuilder.ImportedRecord(tipp.tippTitleName()))
				.doOnNext(bibRecordService::addBibRecord)
				.doOnTerminate(bibRecordService::commit) // Wnen done commit the data
				.doOnComplete(cleanUp( Instant.ofEpochMilli(start) ))
				.doOnCancel(cleanUp( lastRun )) // Don't change the last run
				.onErrorResume(t -> {
					log.error("Error fetching from GOKb {}", t.getMessage());
					cleanUp( lastRun ).run();
					
					return Mono.empty();
				})
				.count()
				.subscribe(count -> {
					bibRecordService.cleanup();
					
					if (count < 1) {
						log.info("No records to import");
						return;
					}
					
					final Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - start);
					log.info("Finsihed adding {} records. Total time {} hours, {} minute and {} seconds", count,
							elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart());
				});
	}


	@Override
	public void onApplicationEvent(RetryEvent retry) {

		var state = retry.getRetryState();
		log.info("Retry attempt {} for {}", state.currentAttempt(), retry.getSource().getDeclaringType().toString());
	}
}
