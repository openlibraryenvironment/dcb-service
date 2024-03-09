package org.olf.dcb.core.model;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import java.time.Instant;

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class Location {

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
}
