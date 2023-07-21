package org.olf.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.Size;

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
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.ToString;

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity(value = "agency")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class DataAgency implements Agency {

	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@NonNull
	@Size(max = 32)
	private String code;

	@NonNull
	@Size(max = 200)
	private String name;

        @ToString.Exclude
	@NonNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms hostLms;
}
