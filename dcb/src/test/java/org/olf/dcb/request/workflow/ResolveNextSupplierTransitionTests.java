package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ResolveNextSupplierTransitionTests {
	private static final String BORROWING_HOST_LMS_CODE = "next-supplier-borrowing-tests";
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private ResolveNextSupplierTransition resolveNextSupplierTransition;

	private DataHostLms borrowingHostLms;
	private DataAgency borrowingAgency;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://resolve-next-borrowing-tests.com";
		final String KEY = "key";
		final String SECRET = "secret";

		hostLmsFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		borrowingHostLms = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE,
			KEY, SECRET, BASE_URL);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();

		borrowingAgency = agencyFixture.defineAgency("borrowing-agency", "Borrowing Agency",
			borrowingHostLms);
	}

	@Test
	void shouldProgressRequestWhenSupplierHasCancelled() {
		// Arrange
		final var borrowingLocalRequestId = "3635625";

		final var patronRequest = definePatronRequest(NOT_SUPPLIED_CURRENT_SUPPLIER,
			borrowingLocalRequestId);

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY)
		));

		sierraPatronsAPIFixture.verifyDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@ParameterizedTest
	@ValueSource(strings = {HOLD_MISSING, HOLD_CANCELLED})
	void shouldNotCancelBorrowingRequestWhenMissingOrCancelled(String localRequestStatus) {
		// Arrange
		final var borrowingLocalRequestId = "7836734";

		final var patron = patronFixture.definePatron("783174", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(borrowingLocalRequestId)
			.localRequestStatus(localRequestStatus)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		sierraPatronsAPIFixture.mockDeleteHold(borrowingLocalRequestId);

		// Act
		final var updatedPatronRequest = resolveNextSupplier(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY)
		));

		sierraPatronsAPIFixture.verifyNoDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	@Test
	void shouldNotApplyWhenItemHasBeenDispatchedForPickup() {
		// Arrange
		final var patronRequest = definePatronRequest(PICKUP_TRANSIT, "3635625");

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable after item has been dispatched",
			applicable, is(false));
	}

	@Test
	void shouldTolerateNullPatronRequestStatus() {
		// Arrange
		final var patronRequest = definePatronRequest(null, "3635625");

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable for request with no status",
			applicable, is(false));
	}

	@Test
	void shouldFailWhenPatronIsNotAssociatedWithHostLms() {
		// Arrange
		final var borrowingLocalRequestId = "6726357";

		final var patron = patronFixture.definePatron("264535", "home-library",
			null, null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(NOT_SUPPLIED_CURRENT_SUPPLIER)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(borrowingLocalRequestId)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		defineSupplierRequest(patronRequest, HOLD_CANCELLED);

		// Act
		final var error = assertThrows(RuntimeException.class,
			() -> resolveNextSupplier(patronRequest));

		// Assert
		assertThat(error, allOf(
			notNullValue(),
			hasMessage("Patron is not associated with a Host LMS")
		));

		sierraPatronsAPIFixture.verifyNoDeleteHoldRequestMade(borrowingLocalRequestId);
	}

	private PatronRequest resolveNextSupplier(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!resolveNextSupplierTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Resolve next supplier is not applicable for request"));
				}

				return resolveNextSupplierTransition.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}

	private Boolean isApplicable(PatronRequest patronRequest) {
		return singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> resolveNextSupplierTransition.isApplicableFor(ctx)));
	}

	private PatronRequest definePatronRequest(PatronRequest.Status status,
		String localRequestId) {

		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, borrowingAgency);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId(localRequestId)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private SupplierRequest defineSupplierRequest(PatronRequest patronRequest, String localStatus) {
		return supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("next-supplier-supplying-host-lms")
			.localItemId("48375735")
			.localStatus(localStatus)
			.build());
	}
}
