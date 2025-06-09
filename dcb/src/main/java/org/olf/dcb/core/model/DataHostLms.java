package org.olf.dcb.core.model;

import static io.micronaut.data.model.DataType.JSON;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Data
@Serdeable
@ExcludeFromGeneratedCoverageReport
@MappedEntity(value = "host_lms")
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@Slf4j
public class DataHostLms implements HostLms {

	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	public UUID id;

	// If we specify NonNull then MN-data can't create an ID-only instance for associations
	@Size(max = 32)
	public String code;

	@Nullable
	@Size(max = 200)
	public String name;

	@Nullable
	@Size(max = 128)
	public String suppressionRulesetName;
	
	@Nullable
	@Size(max = 128)
	public String itemSuppressionRulesetName;

	@ToString.Exclude
	@Nullable
	@MappedProperty(value = "lms_client_class")
	public String lmsClientClass;

	@ToString.Exclude
	@Nullable
	@Size(max = 200)
	public String ingestSourceClass;

	@ToString.Exclude
	@Singular("clientConfig")
	@TypeDef(type = JSON)
	Map<String, Object> clientConfig;

	@Override
	@Transient
	@JsonIgnore
	public Class<?> getClientType() {
		//TODO: Replace this with a proper converter implementation then remove this getter.
		return getTypeFromName(lmsClientClass);
	}

	@Override
	@Nullable
	@Transient
	@JsonIgnore
	public Class<?> getIngestSourceType() {
		//TODO: Replace this with a proper converter implementation then remove this getter.
		return getTypeFromName(ingestSourceClass);
	}

	@Nullable
	private Class<?> getTypeFromName(@Nullable String name) {
		if (name == null) {
			return null;
		}

		try {
			return Class.forName(name);
		}
		catch (ClassNotFoundException exception) {
			log.error("class {} cannot be found", name, exception);

			// Does not throw exception because method is used in a property
			// Properties that throw exceptions fail micronaut validation
			return null;
		}
	}
}
