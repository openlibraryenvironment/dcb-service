package org.olf.dcb.core.model;

import static io.micronaut.data.model.DataType.JSON;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.audit.Auditable;

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
import io.micronaut.security.annotation.UpdatedBy;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Accessors(chain=true)
@Builder
public class Location implements Auditable {

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

	@NotNull
	@NonNull
	@Size(max = 200)
	@Column(unique = true)
	private String code;

	@NotNull
	@NonNull
	@Size(max = 255)
	private String name;

	// Expect @Data to generate the getters and setters here - Use this field to
	// discriminate
	// Campus, Library, Locations (I.E. 3rd Floor Main Library), Service Points
	// (Collection Desk, 3rd floor main library)
	@NotNull
	@NonNull
	@Size(max = 32)
	private String type;

	// The UUID of the agency that owns this location record - We may not be able to infer this, so it's nullable
	// in order that a human can establish the correct relationship after initial discovery
	@ToString.Exclude
	@Column(name = "agency_fk")
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency agency;

	@ToString.Exclude
	@Column(name = "parent_location_fk")
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private Location parentLocation;

	/**
	* The host system on which this location is managed
	*/
	@NonNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms hostSystem;

	// List this location in the pickup location lists
	private Boolean isPickup;

	// It is possible to be a pickup location but the location can't be used in 3-legged requests
	private Boolean isEnabledForPickupAnywhere;

	// Is this location a shelving location
	private Boolean isShelving;

	// Does this location supply items (Some are reference only or have other flags)
	private Boolean isSupplyingLocation;

	private Double longitude;

	private Double latitude;

	// We now allow import to specify a reference which might help us retrospectively
	// identify bad data uploads and remove blocks of errors
	private String importReference;

	// It's common to find a short or coded name that should be printed on slips
	private String printLabel;

	private String deliveryStops;

	// Some systems such as Polaris allow a location "Uber Library" to have a code "UL" as well as an ID 1245. 
	// obviously some systems use strings (or UUIDs) and some use integers - so we store it as a string. Each hostLmsClient
	// will need to do some conversion to coerce the value here into what it needs.
	private String localId;

	// Locations can now be imported like mappings, so we need to keep track of the import timestamps
	@Nullable
	private Instant lastImported;

	@Nullable
	@UpdatedBy
	private String lastEditedBy;

	@Nullable
	private String reason;

	@Nullable
	private String changeCategory;

	@Nullable
	private String changeReferenceUrl;

	@Nullable
	private Boolean needsAttention;

  @ToString.Exclude
  @Singular("activeWorkflow")
  @TypeDef(type = JSON)
	@Nullable
	private Map<String,Workflow> activeWorkflows;

  @ToString.Exclude
  @Singular("archivedWorkflow")
  @TypeDef(type = JSON)
	@Nullable
	private Map<String,Workflow> archivedWorkflows;

	@Transient
	public String getIdAsString() {
		return getValueOrNull(this, Location::getId, UUID::toString);
	}
}
