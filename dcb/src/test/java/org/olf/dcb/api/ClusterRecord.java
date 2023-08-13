package org.olf.dcb.api;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;
import org.olf.dcb.ingest.model.Identifier;

import java.time.LocalDateTime;
import java.util.List;

@Serdeable
public record ClusterRecord(
	@Nullable String clusterId,
	@Nullable LocalDateTime dateCreated,
	@Nullable LocalDateTime dateUpdated,
	@Nullable String title,
	@Nullable String selectedBibId,
	@Nullable SelectedBib selectedBib,
	@Nullable List<Bib> bibs
) {

	@Serdeable
	public record SelectedBib(
		@Nullable String bibId,
		@Nullable String title,
		@Nullable String sourceRecordId,
		@Nullable String sourceSystemId,
		@Nullable String sourceSystemCode,
		@Nullable String recordStatus,
		@Nullable String typeOfRecord,
		@Nullable String derivedType,
		@Nullable CanonicalMetadata canonicalMetadata
	) {
	}

	@Serdeable
	public record Bib(
		@Nullable String bibId,
		@Nullable String title,
		@Nullable String sourceRecordId,
		@Nullable String sourceSystem,
		@Nullable String metadataScore
	) {
	}

	@Serdeable
	public record CanonicalMetadata(
		@Nullable String title,
		@Nullable Author author,
		@Nullable List<Subject> subjects,
		@Nullable String derivedType,
		@Nullable List<Identifier> identifiers,
		@Nullable List<Author> otherAuthors,
		@Nullable String recordStatus
	) {
	}


	@Serdeable
	public record Subject(
		@Nullable String label,
		@Nullable String subtype
	) {
	}

	@Serdeable
	public record Identifier(
		@Nullable String value,
		@Nullable String namespace
	) {
	}

	@Serdeable
	public record Author(
		@Nullable String name,
		@Nullable List<Identifier> identifiers
	) {
	}
}
