package org.olf.reshare.dcb.core.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Relation.Kind;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
public class BibRecord {

	public BibRecord() {
	}

	public BibRecord(UUID id, String title) {
		this.id = id;
		this.title = title;
	}

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;

	@Nullable
	@Column(columnDefinition = "TEXT")
	private String title;
	
	@NotNull
	@Relation(value = Kind.MANY_TO_MANY)
	private Set<BibIdentifier> identifiers = new HashSet<>();

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