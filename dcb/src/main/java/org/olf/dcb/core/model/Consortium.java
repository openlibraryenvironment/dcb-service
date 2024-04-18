package org.olf.dcb.core.model;

import java.util.*;

import io.micronaut.core.annotation.*;
import io.micronaut.data.annotation.*;

import io.micronaut.data.annotation.Id;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.*;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.ToString;

@Data
@Accessors(chain=true)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
public class Consortium {
	@ToString.Include
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@NonNull
	@Size(max = 200)
	private String name;

	@Relation(value = Relation.Kind.ONE_TO_ONE)
	@Nullable
	private LibraryGroup libraryGroup;
}
