package org.olf.dcb.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasCode;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasId;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasName;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasType;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.GrantFixture;
import org.olf.dcb.test.StatusCodeFixture;

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

	@Inject
	private GrantFixture grantFixture;
	@Inject
	private StatusCodeFixture statusCodeFixture;

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

	@Test
	void shouldFindGrantCreatedAtStartup() {
		final var grants = grantFixture.findAll();

		assertThat("Single grant created at startup", grants, hasSize(1));

		assertThat(grants, containsInAnyOrder(allOf(
			hasProperty("id", is(notNullValue())),
			hasProperty("grantResourceOwner", is("%")),
			hasProperty("grantResourceType", is("%")),
			hasProperty("grantResourceId", is("%")),
			hasProperty("grantedPerm", is("%")),
			hasProperty("granteeType", is("role")),
			hasProperty("grantee", is("ADMIN")),
			hasProperty("grantOption", is(true))
		)));
	}

	@Test
	void shouldFindStatusesCreatedAtStartup() {
		final var statusCodes = statusCodeFixture.findAll();
		
		assertThat(statusCodes, containsInAnyOrder(
			hasStatusCode("SupplierRequest", "IDLE", false),
			hasStatusCode("SupplierRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", true),
			hasStatusCode("SupplierRequest", "PLACED", true),
			hasStatusCode("SupplierRequest", "MISSING", false),
			hasStatusCode("PatronRequest", "IDLE", false),
			hasStatusCode("PatronRequest", "PLACED", true),
			hasStatusCode("VirtualItem", "IDLE", false),
			hasStatusCode("VirtualItem", "RET-TRANSIT", false),
			hasStatusCode("VirtualItem", "TRANSIT", true),
			hasStatusCode("VirtualItem", "AVAILABLE", true),
			hasStatusCode("VirtualItem", "LOANED", true),
			hasStatusCode("VirtualItem", "PICKUP_TRANSIT", true),
			hasStatusCode("VirtualItem", "HOLDSHELF", true),
			hasStatusCode("VirtualItem", "MISSING", false),
			hasStatusCode("SupplierItem", "TRANSIT", true),
			hasStatusCode("SupplierItem", "RECEIEVED", true)
		));
	}

	private static Matcher<Object> hasStatusCode(String expectedModel,
		String expectedCode, boolean expectedTracked) {

		return allOf(
			hasProperty("id", is(notNullValue())),
			hasProperty("model", is(expectedModel)),
			hasProperty("code", is(expectedCode)),
			hasProperty("tracked", is(expectedTracked))
		);
	}
}
