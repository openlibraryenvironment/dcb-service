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
public class BibIdentifier {

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
  private UUID id;
	
	@NotNull
	@NonNull
	private String value;
	
	@NotNull
	@NonNull
	private String namespace;
}