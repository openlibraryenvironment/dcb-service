package org.olf.reshare.dcb.bib;

import org.olf.reshare.dcb.ImportedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;

@Singleton
public class DefaultBibRecordService implements BibRecordService {

  private static Logger log = LoggerFactory.getLogger(DefaultBibRecordService.class);
	
  @Override
  public void addBibRecord ( ImportedRecord record ) {
    log.info("Adding record {}", record.identifier() + record.title());
  }
}

