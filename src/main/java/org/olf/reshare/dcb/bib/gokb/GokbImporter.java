package org.olf.reshare.dcb.bib.gokb;

import java.io.IOException;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.ImportedRecordBuilder;
import org.olf.reshare.dcb.bib.BibRecordService;
import org.olf.reshare.dcb.bib.storage.BibRepository;
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
public class GokbImporter {

  private static Logger log = LoggerFactory.getLogger(GokbImporter.class);
	private final GokbApiClient gokbapi;
	BibRecordService bibRecordService;
	BibRepository bibRepo;
	ObjectMapper objectMapper;
	
	GokbImporter(
		GokbApiClient gokbapi,
		BibRecordService bibRecordService,
		BibRepository bibRepo,
		ObjectMapper objectMapper) {
		this.gokbapi = gokbapi;
		this.bibRecordService = bibRecordService;
		this.bibRepo = bibRepo;
		this.objectMapper = objectMapper;
	}
	
	
	@Scheduled(initialDelay = "1s")
	public void run() {
		
		final long start = System.currentTimeMillis();
		// Set up the stream
		Flux<ImportedRecord> records = Mono.from( gokbapi.scrollTipps() )
		
			.filter( resp -> "OK".equalsIgnoreCase( resp.result() ) && resp.size() > 0)
			
			.flatMapIterable( resp -> resp.records() )
			.map( tipp -> ImportedRecordBuilder.ImportedRecord( tipp.tippTitleName() ) )
		;
	
	  records.count().subscribe(count -> {
	  	log.info ("Imported {} records in {} milliseconds", count, System.currentTimeMillis() - start);
	  });
	  
	  records.subscribe( bibRecordService::addBibRecord );
	  
  	// Now lets dump out the records.
  	log.info ("Try serializing as JSON");
	  Flux.from( bibRepo.getAll() )
	  	.mapNotNull (bib -> {
				try {
					return objectMapper.writeValueAsString( bib );
				} catch (IOException e) {
					log.error( "Error writing {}", bib);
					return null;
				}
			})
	  	.subscribe( log::info )
	  ;
	}
}
