package org.olf.reshare.dcb.bib.gokb;

import java.time.Duration;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.ImportedRecordBuilder;
import org.olf.reshare.dcb.bib.BibRecordService;
import org.olf.reshare.dcb.bib.storage.BibRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
@Requires(property = (GokbApiClient.CONFIG_ROOT + ".enabled"), value = "true")
public class GokbImporter implements Runnable {

	private static Logger log = LoggerFactory.getLogger( GokbImporter.class );
	private final GokbApiClient gokbapi;
	BibRecordService bibRecordService;
	BibRepository bibRepo;
	ObjectMapper objectMapper;

	GokbImporter (GokbApiClient gokbapi, BibRecordService bibRecordService, BibRepository bibRepo,
			ObjectMapper objectMapper) {
		this.gokbapi = gokbapi;
		this.bibRecordService = bibRecordService;
		this.bibRepo = bibRepo;
		this.objectMapper = objectMapper;
	}

	private Publisher<GokbTipp> scrollAllResults ( final String scrollId ) {
		final long start = System.currentTimeMillis();
		log.info( "Fetching scroll page from Gokb" );
		return Mono.from( gokbapi.scrollTipps( scrollId ) )
				.filter( resp -> "OK".equalsIgnoreCase( resp.result() ) && resp.size() > 0 )
				.flatMapMany( resp -> {

					// We already have results. If there are more results then we create a
					// new Stream from these results plus the result of recursing this
					// method with the scroll id the gokb api will supply the next page of
					// results. Doing this here effectively chunks a single stream into
					// the 5000 element pages that gokb supplies, but removes the owness
					// of paging from the subscriber.

					final Flux<GokbTipp> currentPage = Flux.fromIterable( resp.records() );
					currentPage.count().subscribe( count -> {

						bibRecordService.cleanup();
						log.info( "Processed a chunk of {} records in {} milliseconds", count,
								System.currentTimeMillis() - start );
					} );
					return resp.hasMoreRecords()
							? Flux.concat( currentPage, scrollAllResults( resp.scrollId() ) )
							: currentPage;
				} );
	}

	@Override
	@Scheduled(initialDelay = "10s")
	public void run () {
		
		final long start = System.currentTimeMillis();
		
		// Client stream
		Flux<ImportedRecord> tipps = Flux.from( scrollAllResults( null ) )
				.name( "gokp-tipps" )
				.metrics()
				.filter( tipp -> tipp.tippTitleName() != null )
				.map( tipp -> ImportedRecordBuilder.ImportedRecord( tipp.tippTitleName() ))
				
				.doOnTerminate( bibRecordService::commit ); // Wnen done commit the data to disk.
		
		// Add a count output
		tipps.count().subscribe( count -> {
			bibRecordService.cleanup();
			final Duration elapsed = Duration.ofMillis( System.currentTimeMillis() - start );
			log.info( "Finsihed adding {} records. Total time {} hours, {} minute and {} seconds", count,
					elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart() );
		});
		
		tipps.subscribe( bibRecordService::addBibRecord );
	}
}
