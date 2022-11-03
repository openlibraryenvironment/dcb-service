package org.olf.reshare.dcb.bib;

import java.util.UUID;

import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.model.BibRecord;
import org.olf.reshare.dcb.bib.model.BibRecordBuilder;
import org.olf.reshare.dcb.bib.storage.BibRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;

@Singleton
public class DefaultBibRecordService implements BibRecordService {	

  private static Logger log = LoggerFactory.getLogger(DefaultBibRecordService.class);
	
	private BibRepository bibRepo;
	
	DefaultBibRecordService( BibRepository bibRepo ) {
		this.bibRepo = bibRepo;
	}
	
  public void addBibRecord ( ImportedRecord record ) {
  	final BibRecord bib = BibRecordBuilder.builder()
  		.id( UUID.randomUUID())
  		.title( record.title() )
  		.build();
  	
  	log.debug( "Adding bib record for title" + bib.title() );
  	bibRepo.save( bib );
  }
}
