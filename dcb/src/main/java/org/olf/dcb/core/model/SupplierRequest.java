package org.olf.dcb.core.model;

import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PLACED;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;

import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.RequiredArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import java.time.Instant;


/**
 * Important reminder: This class needs to be representative of many different target systems.
 * Strings are chosen in many places to give us some flexibility to adapt to different targets - when adding properties here
 * remember to bear in mind that the datatype of the system you have in mind might not map to other systems.
 */
@Serdeable
@ExcludeFromGeneratedCoverageReport
@Data
@MappedEntity
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class SupplierRequest {

	@ToString.Include
	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
        @DateCreated
        private Instant dateCreated;

        @Nullable
        @DateUpdated
        private Instant dateUpdated;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "patron_request_id")
	private PatronRequest patronRequest;

	@NotNull
	@NonNull
	@Size(max = 200)
	private String localItemId;

	@Size(max = 200)
	private String localBibId;

	@Nullable
	@Size(max = 200)
	private String localItemBarcode;

	@Nullable
	@Size(max = 200)
	private String localItemLocationCode;

	@Nullable
	@Size(max = 32)
	private String localItemStatus;

	@Nullable
	@Size(max = 32)
	private String localItemType;

        // Canonical item type is how we have understood this itemType from this system in a way that
        // is understandable across the system
	@Nullable
	@Size(max = 32)
	private String canonicalItemType;

	@NotNull
	@NonNull
	@Size(max = 200)
	private String hostLmsCode;

	@ToString.Include
	@Nullable
	@Size(max = 200)
	@Enumerated(EnumType.STRING)
	private SupplierRequestStatusCode statusCode;

	@Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        private PatronIdentity virtualIdentity;

	// Once we have placed a hold at the lending system, track that hold by storring it's ID here
	// this will only be unique within the context of a hostLmsCode
	@Nullable
	@Size(max = 200)
	private String localId;

	@Nullable
	@Size(max = 32)
	private String localStatus;

	@Nullable
        private String localAgency;

        @Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        private DataAgency resolvedAgency;

	@Nullable
	private Boolean isActive;

	public SupplierRequest placed(String localId, String localStatus) {
		setLocalId(localId);
		setLocalStatus(localStatus);
		setStatusCode(PLACED);

		return this;
	}
}
