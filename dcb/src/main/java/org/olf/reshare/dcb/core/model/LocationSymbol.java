package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;


/**
 * LocationSymbol - a code which uniquely identifies a location according to a specific naming authority.
 * ReShare DCB needs to be able to understand that different systems (Different providers, different consortia, different libraries) have different
 * codes for the same thing. The LocationSymbol concept allows a Location to be associated with a number of symbols in different contexts. For example,
 * given the symbol "EAST" (From the ReShare "Cardinal" demonstration consortium consisting of North, East, West and South). we would say that for the
 * the canonical symbol is "Cardinal:EAST". If East university were to join the "Polar" consortium it would be known by the symbol "0-1". This gives rise to
 * Location: name("East University")
 *     - LocationSymbol Cardinal:"EAST"
 *     - LocationSymbol Polar:"0-1"
 */
@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class LocationSymbol {

	@NotNull
	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;

	@NotNull
	@NonNull
	@Column(columnDefinition = "varchar(32)", unique = true)
	private String authority;

	@NotNull
	@NonNull
	@Column(columnDefinition = "varchar(64)", unique = true)
	private String code;

        // The UUID of the agency that owns this location record
        @Column(name="owning_location_fk")
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        private Location location;
}
