package org.olf.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Size;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


/**
 * ProcessState - store state information for processes in the DCB space.
 * DCB is a process-oriented service. We have many processes, for example fetching the latest bib records from
 * a large number of back-end systems. Rather than scatter the state of those processes in the domain classes that
 * describe the endpoint, we collect all the "state" information in instances of this class.
 *
 */
@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity(value = "process_state")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class ProcessState {

	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	private UUID id;

	@NonNull
	@Column(columnDefinition = "UUID")
	private UUID context;

	@NonNull
	@Column(columnDefinition = "varchar(200)")
	@Size(max = 200)
	private String processName;

	@NonNull
	@Singular("processState")
	@TypeDef(type = DataType.JSON)
	Map<String, Object> processState;
}
