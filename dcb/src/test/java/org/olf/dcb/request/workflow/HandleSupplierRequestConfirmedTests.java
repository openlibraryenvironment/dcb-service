package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class HandleSupplierRequestConfirmedTests {
	private static final String SUPPLYING_HOST_LMS_CODE = "handle-supplier-confirmed-tests";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private HandleSupplierRequestConfirmed handleSupplierRequestConfirmed;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://borrowing-agency-service-tests.com";
		final String KEY = "borrowing-agency-service-key";
		final String SECRET = "borrowing-agency-service-secret";

		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldProgressPatronRequestToConfirmed() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		// Update the hold to be an item level hold
		final var updatedLocalSupplyingItemId = "2424755";

		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId,
			SierraPatronHold.builder()
				.id(localSupplyingHoldId)
				.recordType("i")
				.record(updatedLocalSupplyingItemId)
				.status(SierraCodeTuple.builder()
					.code("0")
					.build())
				.build());

		// Act
		final var updatedPatronRequest = confirmRequestPlacedAtSupplyingAgency(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CONFIRMED)
		));

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(updatedSupplierRequest, allOf(
			hasLocalStatus(HOLD_CONFIRMED),
			hasLocalItemId(updatedLocalSupplyingItemId)
		));
	}

	@Test
	void shouldNotUpdateTheLocalItemIdWhenNoneProvided() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";
		final var originalLocalItemId = "647375678";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId(originalLocalItemId)
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		// Update the hold to be an item level hold
		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId,
			SierraPatronHold.builder()
				.id(localSupplyingHoldId)
				.recordType("i")
				.record(null)
				.status(SierraCodeTuple.builder()
					.code("0")
					.build())
				.build());

		// Act
		final var updatedPatronRequest = confirmRequestPlacedAtSupplyingAgency(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CONFIRMED)
		));

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(updatedSupplierRequest, allOf(
			hasLocalStatus(HOLD_CONFIRMED),
			hasLocalItemId(originalLocalItemId)
		));
	}

	@Test
	void shouldFailWhenLocalRequestCannotBeFetched() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";
		final var originalLocalItemId = "647375678";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId(originalLocalItemId)
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		// Update the hold to be an item level hold
		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId,
			response()
				.withStatusCode(500)
				.withBody("Something went wrong"));

		// Act
		assertThrows(ThrowableProblem.class,
			() -> confirmRequestPlacedAtSupplyingAgency(patronRequest));

		// Assert

		// Patron request status should not have changed
		assertThat(patronRequest, allOf(
			notNullValue(),
			hasStatus(REQUEST_PLACED_AT_SUPPLYING_AGENCY)
		));

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		// Supplier request should not have changed
		assertThat(updatedSupplierRequest, allOf(
			hasLocalStatus(HOLD_CONFIRMED),
			hasLocalItemId(originalLocalItemId)
		));
	}

	private PatronRequest confirmRequestPlacedAtSupplyingAgency(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!handleSupplierRequestConfirmed.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Handle supplier request confirmed is not applicable for request"));
				}

				return handleSupplierRequestConfirmed.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}
}
