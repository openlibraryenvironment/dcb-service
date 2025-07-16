package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import static services.k_int.utils.StringUtils.parseList;


/**
 * The identifier for a patron in a specific host system.
 */
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@Accessors(chain = true)
@AllArgsConstructor
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@ToString(onlyExplicitlyIncluded = true)
public class PatronIdentity {

	@ToString.Include
	@NotNull
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Patron patron;

	@ToString.Include
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms hostLms;

	@ToString.Include
	@NotNull
	@NonNull
	private String localId;

	@ToString.Include
	@NotNull
	@NonNull
	private Boolean homeIdentity;

	@ToString.Include
	@Nullable
	private String localBarcode;

	// PII: The system may allow administrators to set local policies which control what values
	// can appear in this field. This field is not forced to be populated.
	@Nullable
	private String localNames;

	@ToString.Include
	@Nullable
	private String localPtype;

	@ToString.Include
	@Nullable
	private String canonicalPtype;

	@Nullable
	private String localHomeLibraryCode;

	@Nullable
	private Instant lastValidated;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency resolvedAgency;

	/**
	 * Parses the localBarcode field which can contain multiple barcodes stored as a string representation
	 * of a list (e.g., "[b1, b2, b3]") and returns them as a List<String>.
	 *
	 * @return List of patron barcodes, or empty list if no barcodes are available
	 */
	@io.micronaut.data.annotation.Transient
	@com.fasterxml.jackson.annotation.JsonIgnore
	public List<String> getParsedBarcodes() {
		if (localBarcode == null || localBarcode.trim().isEmpty()) {
			return Collections.emptyList();
		}

		List<String> barcodes = parseList(localBarcode);
		return barcodes != null ? barcodes : Collections.emptyList();
	}

	/**
	 * Checks if the patron has any valid barcodes.
	 *
	 * @return true if patron has at least one barcode, false otherwise
	 */
	@io.micronaut.data.annotation.Transient
	@com.fasterxml.jackson.annotation.JsonIgnore
	public boolean hasBarcodes() {
		List<String> barcodes = getParsedBarcodes();
		return !barcodes.isEmpty();
	}

	/**
	 * Gets the first barcode from the list of parsed barcodes.
	 *
	 * @return the first barcode, or null if no barcodes are available
	 */
	@io.micronaut.data.annotation.Transient
	@com.fasterxml.jackson.annotation.JsonIgnore
	public String getFirstBarcode() {
		List<String> barcodes = getParsedBarcodes();
		return barcodes.isEmpty() ? null : barcodes.get(0);
	}
}
