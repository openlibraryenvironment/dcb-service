package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
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
@MappedEntity(value = "agency")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class DataAgency implements Agency {

	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;

	@NonNull
	@Column(columnDefinition = "varchar(32)")
	private String code;

	@NonNull
	@Column(columnDefinition = "varchar(200)")
	@Size(max = 200)
	private String name;

	@NonNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataHostLms hostLms;
}
