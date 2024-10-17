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
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasClientClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasCode;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasId;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasIngestSourceClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasName;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasNoClientClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasNoIngestSourceClass;
import static org.olf.dcb.test.matchers.HostLmsMatchers.hasNonNullId;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.GrantFixture;
import org.olf.dcb.test.StatusCodeFixture;

import jakarta.inject.Inject;

@DcbTest
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
		assertThat(foundHost, hasClientClass(SierraLmsClient.class.getCanonicalName()));
		assertThat(foundHost, hasIngestSourceClass(SierraLmsClient.class.getCanonicalName()));
		assertThat(foundHost, hasProperty("clientConfig",
			hasEntry("base-url", "https://some-sierra-system")));
	}

	@Test
	void shouldTolerateHostLmsWithoutClientType() {
		// Act
		final var foundHost = hostLmsService.findByCode("no-client-config-host").block();

		// Assert
		assertThat(foundHost, hasNonNullId());
		assertThat(foundHost, hasNoClientClass());
		assertThat(foundHost, hasNoIngestSourceClass());
	}

	// SO: Fixed the binding as was initially intended. clientType will bind both ingest source and
	// lms client if the ingest source is not already set, and if the client is of a valid type.
//	@Test
//	void shouldTolerateHostLmsWithoutIngestSourceType() {
//		// Act
//		final var foundHost = hostLmsService.findByCode("no-ingest-source-config-host").block();
//
//		// Assert
//		assertThat(foundHost, hasNonNullId());
//		assertThat(foundHost, hasClientClass(SierraLmsClient.class.getCanonicalName()));
//		
//		
//		assertThat(foundHost, hasNoIngestSourceClass());
//	}

	@Test
	void shouldNotFindAnyGrantsCreatedAtStartup() {
		final var grants = grantFixture.findAll();

		assertThat("Single grant created at startup", grants, hasSize(0));

//		assertThat(grants, containsInAnyOrder(allOf(
//			hasProperty("id", is(notNullValue())),
//			hasProperty("grantResourceOwner", is("%")),
//			hasProperty("grantResourceType", is("%")),
//			hasProperty("grantResourceId", is("%")),
//			hasProperty("grantedPerm", is("%")),
//			hasProperty("granteeType", is("role")),
//			hasProperty("grantee", is("ADMIN")),
//			hasProperty("grantOption", is(true))
//		)));
	}

	@Test
	void shouldFindStatusesCreatedAtStartup() {
		final var statusCodes = statusCodeFixture.findAll();
		
		assertThat(statusCodes, containsInAnyOrder(
			hasStatusCode("SupplierRequest", "IDLE", false),
			// hasStatusCode("SupplierRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", true),
			hasStatusCode("SupplierRequest", "PLACED", true),
			hasStatusCode("SupplierRequest", "MISSING", false),
			hasStatusCode("SupplierRequest", "CONFIRMED", true),
			hasStatusCode("PatronRequest", "IDLE", false),
			hasStatusCode("PatronRequest", "PLACED", true),
			// hasStatusCode("PatronRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", true),
			hasStatusCode("PatronRequest", "CANCELLED", true),
			hasStatusCode("PatronRequest", "MISSING", false),
			hasStatusCode("VirtualItem", "IDLE", false),
			hasStatusCode("VirtualItem", "RET-TRANSIT", false),
			hasStatusCode("VirtualItem", "TRANSIT", true),
			hasStatusCode("VirtualItem", "REQUESTED", true),
			hasStatusCode("VirtualItem", "AVAILABLE", true),
			hasStatusCode("VirtualItem", "LOANED", true),
			hasStatusCode("VirtualItem", "PICKUP_TRANSIT", true),
			hasStatusCode("VirtualItem", "HOLDSHELF", true),
			hasStatusCode("VirtualItem", "MISSING", true),
			hasStatusCode("VirtualItem", "CREATED", true),
			hasStatusCode("VirtualItem", "OPEN", true),
			hasStatusCode("SupplierItem", "AVAILABLE", true),
			hasStatusCode("SupplierItem", "LOANED", true),
			hasStatusCode("SupplierItem", "TRANSIT", true),
			hasStatusCode("SupplierItem", "RECEIVED", true),
			hasStatusCode("DCBRequest", "SUBMITTED_TO_DCB", false),
			hasStatusCode("DCBRequest", "PATRON_VERIFIED", false),
			hasStatusCode("DCBRequest", "RESOLVED", false),
			hasStatusCode("DCBRequest", "NOT_SUPPLIED_CURRENT_SUPPLIER", true),
			hasStatusCode("DCBRequest", "NO_ITEMS_SELECTABLE_AT_ANY_AGENCY", false),
			hasStatusCode("DCBRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", true),
			hasStatusCode("DCBRequest", "REQUEST_PLACED_AT_BORROWING_AGENCY", true),
			hasStatusCode("DCBRequest", "CONFIRMED", true),
			hasStatusCode("DCBRequest", "PICKUP_TRANSIT", true),
			hasStatusCode("DCBRequest", "RECEIVED_AT_PICKUP", true),
			hasStatusCode("DCBRequest", "READY_FOR_PICKUP", true),
			hasStatusCode("DCBRequest", "LOANED", true),
			hasStatusCode("DCBRequest", "RETURN_TRANSIT", true),
			hasStatusCode("DCBRequest", "CANCELLED", true),
			hasStatusCode("DCBRequest", "COMPLETED", true),
			hasStatusCode("DCBRequest", "FINALISED", false),
			hasStatusCode("DCBRequest", "ERROR", false)
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
