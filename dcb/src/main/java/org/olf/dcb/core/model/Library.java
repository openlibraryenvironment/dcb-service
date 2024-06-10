package org.olf.dcb.core.model;

import java.util.*;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Creator;

import io.micronaut.data.annotation.*;
import jakarta.validation.constraints.*;

import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
public class Library {
	@ToString.Include
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@NonNull
	@Size(max = 32)
	private String agencyCode;

	@NonNull
	@Size(max = 200)
	private String fullName;

	@NonNull
	@Size(max = 100)
	private String shortName;

	@Nullable
	private Boolean training;

	@NonNull
	@Size(max = 32)
	private String abbreviatedName;

	@Nullable
	@Size(max = 200)
	private String backupDowntimeSchedule;

	@Nullable
	@Size(max = 200)
	private String supportHours;

	@Nullable
	@Size(max = 32)
	private String type;

	@Nullable
	@Size(max = 200)
	private String discoverySystem;

	@Nullable
	@Size(max = 200)
	private String patronWebsite;

	@Nullable
	@Size(max = 200)
	private String hostLmsConfiguration;

	@Nullable
	private Float latitude;

	@Nullable
	private Float longitude;

	@Nullable
	@Size(max = 200)
	private String address;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms secondHostLms;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency agency;

	@Nullable
	@Size(max = 200)
	private String principalLabel;

	@Nullable
	@Size(max = 200)
	private String secretLabel;
}
