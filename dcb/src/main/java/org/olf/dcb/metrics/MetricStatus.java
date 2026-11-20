package org.olf.dcb.metrics;

import io.micronaut.core.annotation.NonNull;

public enum MetricStatus {
	
	BIB_INSERT("BibInsert"),
	BIB_UPDATE("BibUpdate"),
	BIB_DELETED_SUPPRESSED("BibDeletedSupressed"),
	BIB_DELETED_SOURCE("BibDeletedSource"),
	BIB_DELETED_NULL_TITLE("BibDeletedNullTitle"),
	
	
//	INGEST_RECORD("IngestRecord");
	;
	private final String eventName;

	MetricStatus(String eventName) {
      this.eventName = eventName;
  }

  @Override
  public String toString() {
      return getStatusName();
  }

  @NonNull
  public String getStatusName() {
      return eventName;
  }

}
