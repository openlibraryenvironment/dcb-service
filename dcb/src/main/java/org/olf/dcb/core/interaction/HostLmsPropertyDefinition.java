package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class HostLmsPropertyDefinition {
	private String name;
	private String description;
	private Boolean mandatory;
	private String typeCode;

	public static HostLmsPropertyDefinition urlPropertyDefinition(String name,
		String description, Boolean mandatory) {

		return new HostLmsPropertyDefinition(name, description, mandatory, "URL");
	}

	public static HostLmsPropertyDefinition stringPropertyDefinition(String name,
		String description, Boolean mandatory) {

		return new HostLmsPropertyDefinition(name, description, mandatory, "String");
	}

	public static HostLmsPropertyDefinition booleanPropertyDefinition(String name,
		String description, Boolean mandatory) {

		return new HostLmsPropertyDefinition(name, description, mandatory, "Boolean");
	}

	public static HostLmsPropertyDefinition integerPropertyDefinition(String name,
		String description, Boolean mandatory) {

		return new HostLmsPropertyDefinition(name, description, mandatory, "Integer");
	}
}
