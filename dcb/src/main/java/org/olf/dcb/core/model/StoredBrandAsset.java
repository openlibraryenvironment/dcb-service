package org.olf.dcb.core.model;

import java.time.Instant;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One uploaded brand image as it is stored (R-17b).
 *
 * <p>Distinct from {@code org.olf.dcb.core.branding.BrandAsset}, which is the in-memory
 * value a validator produces and a controller returns. This is the row.
 *
 * <p>{@code assetKey} is the primary key and is the SHA-256 of the content plus an
 * extension, so a row is written once and never updated: identical bytes are the same key
 * and different bytes are a different row. That is what lets the served URL be immutable
 * and cached for a year, and it is why there is no version column here.
 *
 * <p>There is no size field either. {@code bytes.length} is the size, and a stored copy of
 * it would be written on every upload and read by nothing.
 *
 * <p>{@code bytes} is deliberately excluded from {@code toString} by the class-level
 * {@code onlyExplicitlyIncluded} — a two-megabyte image in a log line helps nobody.
 */
@Data
@Serdeable
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity("brand_asset")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class StoredBrandAsset {

	@ToString.Include
	@NonNull
	@Id
	@Size(max = 80)
	private String assetKey;

	@NonNull
	@Size(max = 64)
	private String contentType;

	@NonNull
	private byte[] bytes;

	@NonNull
	private Instant dateCreated;
}
