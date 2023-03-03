package org.olf.reshare.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.data.annotation.MappedProperty;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity(value="host_lms")
@NoArgsConstructor(onConstructor_=@Creator())
@AllArgsConstructor
@Builder
public class DataHostLms implements HostLms {
	
	@NonNull
	@NotNull
	@Id
	@Column(columnDefinition = "UUID")
	public UUID id;
	
	@NonNull
	@NotNull
	@Nullable
	@Column(columnDefinition = "TEXT")
	public String name;
	
	@NonNull
	@NotNull
	@Nullable
	@MappedProperty(value="lms_client_class")
	public Class<? extends HostLmsClient> type;
	
	@NonNull
	@NotNull
	@Singular("clientConfig")
	@TypeDef(type = DataType.JSON)
	Map<String, Object> clientConfig;
}
