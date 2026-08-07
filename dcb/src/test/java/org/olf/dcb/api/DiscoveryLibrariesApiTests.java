package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.DiscoveryLibrariesController.LibraryGeo;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

/**
 * A library that is not enabled for borrowing cannot have a request placed
 * through it — ResolvePatronPreflightCheck rejects it — so discovery must not
 * offer it in the institution picker by default.
 */
@DcbTest
@TestInstance(PER_CLASS)
class DiscoveryLibrariesApiTests {
	private static final String HOST_LMS_CODE = "discovery-libraries-host-lms";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE);
	}

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
	}

	@Test
	void shouldOnlyReturnLibrariesEnabledForBorrowingByDefault() {
		// Arrange
		defineAgency("borrowing-agency", true);
		defineAgency("non-borrowing-agency", false);
		defineAgency("unknown-borrowing-agency", null);

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.stream().map(LibraryGeo::code).toList(),
			contains("borrowing-agency"));
	}

	@Test
	void shouldReturnEveryLibraryWhenIncludeAllIsRequested() {
		// Arrange
		defineAgency("borrowing-agency", true);
		defineAgency("non-borrowing-agency", false);
		defineAgency("unknown-borrowing-agency", null);

		// Act
		final var libraries = listLibraries("/discovery/libraries?includeAll=true");

		// Assert
		assertThat(libraries.stream().map(LibraryGeo::code).toList(),
			containsInAnyOrder("borrowing-agency", "non-borrowing-agency", "unknown-borrowing-agency"));
	}

	private void defineAgency(String code, Boolean isBorrowingAgency) {
		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(code)
			.isSupplyingAgency(true)
			.isBorrowingAgency(isBorrowingAgency)
			.hostLms(hostLmsFixture.findByCode(HOST_LMS_CODE))
			.build());
	}

	private List<LibraryGeo> listLibraries(String uri) {
		return client.toBlocking().retrieve(HttpRequest.GET(uri),
			Argument.listOf(LibraryGeo.class));
	}
}
