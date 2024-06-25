package org.olf.dcb.request.workflow;

import jakarta.inject.Inject;
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
import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;
import org.olf.dcb.test.*;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraCodeTuple;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.test.mockserver.MockServerMicronautTest;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.*;
import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.*;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class CancelledPatronRequestTransitionTests {

	private static final String SUPPLYING_HOST_LMS_CODE = "cancelled-patron-request-tests";

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
	private CancelledPatronRequestTransition cancelledPatronRequestTransition;

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
	void shouldNotProgressPatronRequestToCancelledWhenNotApplicable() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(SUBMITTED_TO_DCB)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);

		// Act
		final var applicable = singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> cancelledPatronRequestTransition.isApplicableFor(ctx)));

		// Assert
		assertThat("Should not be applicable for a submitted DCB status",
			applicable, is(false));

	}

	@Test
	void shouldProgressPatronRequestToCancelledWhenNotYetLoanedAndMissingLocalHoldWithFalseVerification() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.statusCode(SupplierRequestStatusCode.PLACED)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);

		// Act
		final var updatedPatronRequest = cancelPatronRequestAtSupplyingAgency(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CANCELLED)
		));

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(updatedSupplierRequest, allOf(
			hasStatusCode(SupplierRequestStatusCode.PLACED)
		));
	}

	@Test
	void shouldProgressPatronRequestToCancelledWhenNotYetLoanedAndMissingLocalHoldWithTrueVerification() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
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
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.mockDeleteHold(localSupplyingHoldId);
		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId, SierraPatronHold.builder()
			.id(localSupplyingHoldId)
			.status(SierraCodeTuple.builder().name("Missing").code("m").build())
			.build());

		// Act
		final var updatedPatronRequest = cancelPatronRequestAtSupplyingAgency(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(CANCELLED)
		));

		final var updatedSupplierRequest = supplierRequestsFixture.findFor(patronRequest);

		assertThat(updatedSupplierRequest, allOf(
			hasStatusCode(SupplierRequestStatusCode.CANCELLED)
		));

	}

	@Test
	void shouldTriggerAnErrorResponseWhenCancellingALocalHoldProducedAnError() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.localRequestStatus(HOLD_MISSING)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		final var localSupplyingHoldId = "7357356";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId("647375678")
				.localItemBarcode("26123553")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.mockDeleteHoldError(localSupplyingHoldId);

		// Act
		assertThrows(ThrowableProblem.class,
			() -> cancelPatronRequestAtSupplyingAgency(patronRequest));

		// Assert
		assertThat(patronRequest, allOf(
			notNullValue(),
			hasStatus(REQUEST_PLACED_AT_BORROWING_AGENCY)
		));
	}

	private PatronRequest cancelPatronRequestAtSupplyingAgency(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!cancelledPatronRequestTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("cancelledPatronRequestTransition is not applicable for request"));
				}

				return cancelledPatronRequestTransition.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}
}
