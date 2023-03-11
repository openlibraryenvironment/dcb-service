package org.olf.reshare.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Transient;
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
@MappedEntity(value = "host_lms")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class DataHostLms implements HostLms {

	@NonNull
	@Id
	@Column(columnDefinition = "UUID")
	public UUID id;

	@NonNull
	@Column(columnDefinition = "varchar(32)")
	public String code;

	@NonNull
	@Nullable
	@Column(columnDefinition = "TEXT")
	public String name;

	@NonNull
	@Nullable
	@MappedProperty(value = "lms_client_class")
	public String lmsClientClass;

	@NonNull
	@Singular("clientConfig")
	@TypeDef(type = DataType.JSON)
	Map<String, Object> clientConfig;

	@Transient
	public Class<? extends HostLmsClient> getType() {
		//TODO: Replace this with a proper converter implementation then remove this getter.
		
		Class<? extends HostLmsClient> resolved_class = null;
		try {
			resolved_class = (Class<? extends HostLmsClient>) Class.forName(lmsClientClass);
		} catch (ClassNotFoundException cnfe) {
		}
		return resolved_class;
	}

}
