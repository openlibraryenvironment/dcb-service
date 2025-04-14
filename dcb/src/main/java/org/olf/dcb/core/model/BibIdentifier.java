package org.olf.dcb.core.model;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@MappedEntity
public class BibIdentifier {

	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@NotNull
	@NonNull
  @EqualsAndHashCode.Include
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private BibRecord owner;

	@NotNull
	@NonNull
	@Size(max = 255)
  @EqualsAndHashCode.Include
	private String value;

	@NotNull
	@NonNull
	@Size(max = 255)
  @EqualsAndHashCode.Include
	private String namespace;

	// 0 = certain 100-random
	// This will be used to indicate for example that the FIRST ISBN in a record is 
	// normally that of the INSTANCE itself, later ISBNs are usually WORK associations
	private Integer confidence;
}
