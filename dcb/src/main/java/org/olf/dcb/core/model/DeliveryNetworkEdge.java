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
public class DeliveryNetworkEdge {

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

  @ToString.Exclude
  @Column(name = "from_location_fk")
  @Relation(value = Relation.Kind.MANY_TO_ONE)
	private Location from;

  @ToString.Exclude
  @Column(name = "to_location_fk")
  @Relation(value = Relation.Kind.MANY_TO_ONE)
	private Location to;

	// Sometimes links need to be deactivated (E.G. Snow makes route unusable)
	private Boolean active;

	// So named to differentiate from "monetary cost" - planning cost is a relative integer used to reflect the cost of this edge
	private Long planningCost;

	// Hubs support multiple routes - e.g. NO/SO hub serves the NOrth route and the SOuth route
	private String routeCode;

	// Name this specific segment of the delivery route - locations can appear on multiple routes - Hubs can support multiple routes
	// Order the segments by this code to work out the order of delivery stops
	private String segmentCode;
}
