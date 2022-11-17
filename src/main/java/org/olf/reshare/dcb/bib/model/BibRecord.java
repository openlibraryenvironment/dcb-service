package org.olf.reshare.dcb.bib.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class BibRecord {

	@NotNull
	@NonNull
	@Id
  private UUID id;
	
	@NotNull
	@NonNull
	@Column(columnDefinition = "TEXT")
	private String title;

	public UUID getId() {
		return id;
	}

	public BibRecord setId(UUID id) {
		this.id = id;
		return this;
	}

	public String getTitle() {
		return title;
	}

	public BibRecord setTitle(String title) {
		this.title = title;
		return this;
	}
}