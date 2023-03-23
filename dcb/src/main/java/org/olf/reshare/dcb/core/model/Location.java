package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;


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
	@Column(columnDefinition = "UUID")
	private UUID id;

	@NotNull
	@NonNull
	@Column(columnDefinition = "TEXT", unique = true)
	private String code;

	@NotNull
	@NonNull
	@Column(columnDefinition = "TEXT")
	private String name;


        // Expect @Data to generate the getters and setters here - Use this field to discriminate
        // Campus, Library, Locations (I.E. 3rd Floor Main Library), Service Points (Collection Desk, 3rd floor main library)
        @NotNull
	@NonNull
	@Column(columnDefinition = "varchar(32)")
	private String type;

        // The UUID of the agency that owns this location record
        @Column(name="agency_fk")
        private UUID agency;

        // List this location in the pickup location lists
        @Column
        private Boolean isPickup;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
