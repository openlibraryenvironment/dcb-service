package org.olf.dcb.core.model;

import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PLACED;

import java.time.Instant;
import java.util.UUID;

import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;

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
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

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
@Accessors(chain = true)
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

	@ToString.Include
	@Nullable
	@Size(max = 32)
	private String localItemStatus;

	@ToString.Include
	@Nullable
	@Size(max = 32)
	private String rawLocalItemStatus;

	@Nullable
	Instant localItemLastCheckTimestamp;

  // How many times have we seen this localRequestStatus when tracking? Used for backoff polling
  @Nullable
  private Long localItemStatusRepeat;

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

	@ToString.Include
	@Nullable
	@Size(max = 32)
	private String localStatus;

	@ToString.Include
	@Nullable
	@Size(max = 200)
	private String rawLocalStatus;

	@Nullable
	Instant localRequestLastCheckTimestamp;

  // How many times have we seen this localRequestStatus when tracking? Used for backoff polling
  @Nullable
  private Long localRequestStatusRepeat;

	@ToString.Include
	@Nullable
	private String localAgency;

	@ToString.Include
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency resolvedAgency;

	@Nullable
	@Builder.Default
	private Boolean isActive = true;

	@Nullable
	private String protocol;

	/**
	 * A place request on a bib record MAY change the item selected, if the fields are set, store the actual values in here
	 */
	public SupplierRequest placed(
		String localId, String localStatus,
		String rawLocalStatus, String localItemId,
		String localItemBarcode)
	{
		setLocalId(localId);
		setLocalStatus(localStatus);
		setRawLocalStatus(rawLocalStatus);
		if ( localItemId != null )
			setLocalItemId(localItemId);
		if ( localItemBarcode != null )
			setLocalItemBarcode(localItemBarcode);
		setStatusCode(PLACED);
		return this;
	}
}
