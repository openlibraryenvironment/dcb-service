package org.olf.reshare.dcb.bib.storage;

import java.util.Optional;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.bib.model.BibRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;

public interface BibRepository {
	
	@NonNull
	public BibRecord save(@NonNull @NotNull @Valid BibRecord bibRecord);

  @NotNull
  public Optional<BibRecord> findById(@NonNull @NotEmpty String id);
  
  @NotNull
  public Publisher<BibRecord> getAll();
}
