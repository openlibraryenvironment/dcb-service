package org.olf.dcb.api;

import java.time.LocalDateTime;
import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;

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
		@Getter
		@Nullable String label,
		@Getter
		@Nullable String subtype
	) {
	}

	@Serdeable
	public record Identifier(
		@Getter
		@Nullable String value,
		@Getter
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
