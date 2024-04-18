package org.olf.dcb.core.model;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Creator;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.Id;
import jakarta.validation.constraints.Size;

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
public class LibraryGroup {

	@ToString.Include
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@ToString.Include
	@NonNull
	@Size(max = 32)
	private String code;

	@NonNull
	@Size(max = 200)
	private String name;

	@NonNull
	@Size(max = 32)
	private String type;

}
