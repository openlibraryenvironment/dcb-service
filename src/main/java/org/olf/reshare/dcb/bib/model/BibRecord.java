package org.olf.reshare.dcb.bib.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@ReflectiveAccess
public class BibRecord {

	@NotNull @NonNull UUID id;
	@NotNull @NonNull String title;
	
	public UUID getId () {
		return id;
	}
	
	public BibRecord setId ( UUID id ) {
		this.id = id;
		return this;
	}
	
	public String getTitle () {
		return title;
	}
	
	public BibRecord setTitle ( String title ) {
		this.title = title;
		return this;
	}
}