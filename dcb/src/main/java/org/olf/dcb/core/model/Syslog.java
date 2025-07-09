package org.olf.dcb.core.model;

import java.util.UUID;
import java.util.Map;

import io.micronaut.security.annotation.UpdatedBy;
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
import lombok.Singular;
import org.olf.dcb.core.audit.Auditable;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import java.time.Instant;

import static io.micronaut.data.model.DataType.JSON;

@Data
@Serdeable
@Builder
@ToString
@MappedEntity
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
public class Syslog {

  @Id
  @TypeDef(type = DataType.LONG)
	Long id;

	@DateCreated
	Instant ts;

	String category;
	String message;
	Map<String,Object> details;
}

