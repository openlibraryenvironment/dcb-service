package org.olf.dcb.core.interaction;

import static services.k_int.utils.MapUtils.getAsOptionalString;

import java.util.Map;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Getter
@AllArgsConstructor
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

	public static IntegerHostLmsPropertyDefinition integerPropertyDefinition(String name,
		String description, Boolean mandatory) {

		return new IntegerHostLmsPropertyDefinition(name, description, mandatory);
	}

	public static class IntegerHostLmsPropertyDefinition extends HostLmsPropertyDefinition {
		public IntegerHostLmsPropertyDefinition(String name, String description, Boolean mandatory) {
			super(name, description, mandatory, "Integer");
		}

		public Integer getOptionalValueFrom(Map<String, Object> clientConfig, int defaultValue) {
			return getAsOptionalString(clientConfig, getName())
				.map(Integer::parseInt)
				.orElse(defaultValue);
		}
	}
}
