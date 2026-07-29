package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;

class NcipHostLmsConfigurationTests {
	private final NcipHostLmsConfiguration configuration =
		new NcipHostLmsConfiguration();

	@Test
	void acceptsCamelCaseNcipSystemId() {
		assertThat(configuration.ncipSystemIdFor(hostLms(Map.of(
			"ncipSystemId", "ORS-SYSTEM:HOGWARTS"))),
			is("ORS-SYSTEM:HOGWARTS"));
	}

	@Test
	void acceptsKebabCaseNcipSystemId() {
		assertThat(configuration.ncipSystemIdFor(hostLms(Map.of(
			"ncip-system-id", "ORS-SYSTEM:HOGWARTS"))),
			is("ORS-SYSTEM:HOGWARTS"));
	}

	@Test
	void acceptsMicronautNormalizedNcipSystemId() {
		assertThat(configuration.ncipSystemIdFor(hostLms(Map.of(
			"ncipsystemid", "ORS-SYSTEM:HOGWARTS"))),
			is("ORS-SYSTEM:HOGWARTS"));
	}

	@Test
	void defaultsNcipAgencyIdToNcipSystemId() {
		assertThat(configuration.ncipAgencyIdFor(hostLms(Map.of(
			"ncip-system-id", "ors:hogwarts"))),
			is("ors:hogwarts"));
	}

	@Test
	void acceptsKebabCaseNcipAgencyId() {
		assertThat(configuration.ncipAgencyIdFor(hostLms(Map.of(
			"ncip-system-id", "ors:hogwarts-system",
			"ncip-agency-id", "ors:hogwarts"))),
			is("ors:hogwarts"));
	}

	@Test
	void rejectsMissingNcipSystemId() {
		final var error = assertThrows(IllegalArgumentException.class,
			() -> configuration.ncipSystemIdFor(hostLms(Map.of())));

		assertThat(error.getMessage(),
			is("Missing required configuration property: \"ncip-system-id\""));
	}

	private static DataHostLms hostLms(Map<String, Object> clientConfig) {
		final var hostLms = new DataHostLms();
		hostLms.setCode("ORS-HOGWARTS");
		hostLms.setClientConfig(clientConfig);
		return hostLms;
	}
}
