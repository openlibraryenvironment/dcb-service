package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

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
	@Column(name = "agency_fk")
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency agency;

    /**
     * The host system on which this location is managed
     */
    @NonNull
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private DataHostLms hostSystem;

    // List this location in the pickup location lists
	private Boolean isPickup;
}
