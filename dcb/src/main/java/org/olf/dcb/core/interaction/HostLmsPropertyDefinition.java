package org.olf.dcb.core.interaction;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Value;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Value
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class HostLmsPropertyDefinition {
	String name;
	String description;
	Boolean mandatory;
	String typeCode;

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
