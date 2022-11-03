package org.olf.reshare.dcb.bib.storage;

import java.util.HashMap;
import java.util.Map;

import org.olf.reshare.dcb.bib.model.BibRecord;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.ReflectiveAccess;

@Introspected
@ReflectiveAccess
public class BibDataRoot {
	private final Map<String, BibRecord> _records = new HashMap<>();
	
	@NonNull
  public Map<String, BibRecord> records() {
      return this._records;
  }
}
