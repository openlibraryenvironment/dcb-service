package org.olf.dcb.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasCode;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasId;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasName;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasType;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@DcbTest
@MicronautTest(transactional = false, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(PER_CLASS)
class DCBStartupEventListenerTests {
	@Inject
	private HostLmsService hostLmsService;

	@Test
	void shouldFindHostLmsInConfigByCode() {
		// Act
		final var foundHost = hostLmsService.findByCode("config-host").block();

		// Assert
		assertThat(foundHost, hasId(
			nameUUIDFromNamespaceAndString(NAMESPACE_DCB, String.format("config:lms:%s", "config-host"))));
		assertThat(foundHost, hasCode("config-host"));
		// The name in the config is ignored and the code is used for the name too
		assertThat(foundHost, hasName("config-host"));
		assertThat(foundHost, hasType(SierraLmsClient.class));
		assertThat(foundHost, hasProperty("clientConfig",
			hasEntry("base-url", "https://some-sierra-system")));
	}
}
