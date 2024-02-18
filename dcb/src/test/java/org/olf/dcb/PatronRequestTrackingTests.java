package org.olf.dcb;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PLACED;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.isFinalised;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalStatus;

import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraHold;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.olf.dcb.tracking.TrackingService;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class PatronRequestTrackingTests {
	private static final String BORROWING_HOST_LMS_CODE = "borrowing-agency-tracking-tests";
	private static final String SUPPLYING_HOST_LMS_CODE = "supplying-agency-tracking-tests";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	TrackingService trackingService;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();

		defineHostLms(BORROWING_HOST_LMS_CODE, "https://borrowing-agency-tracking-tests.com", mockServerClient);
		defineHostLms(SUPPLYING_HOST_LMS_CODE, "https://supplying-agency-tracking-tests.com", mockServerClient);

		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		this.sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldDetectWhenRequestIsPlacedAtSupplyingAgency() {
		// Arrange
		final var patronRequest = createPatronRequest(
			request -> request
				.status(REQUEST_PLACED_AT_BORROWING_AGENCY));

		final var supplyingAgencyLocalRequestId = "11567";

		final var supplierRequest = createSupplierRequest(patronRequest,
			request -> request
				.localId(supplyingAgencyLocalRequestId)
				.localItemId("84356375")
				.statusCode(PLACED)
				// This may be somewhat artificial in order to be able to check for a change
				.localStatus(""));

		sierraPatronsAPIFixture.mockGetHoldById(supplyingAgencyLocalRequestId,
			SierraHold.builder()
				.statusCode("0")
				.statusName("on hold.")
				.build());

		// Act
		trackingService.run();

		// Assert
		await().atMost(5, SECONDS)
			.until(() -> supplierRequestsFixture.findById(supplierRequest.getId()),
				hasLocalStatus("PLACED"));
	}

	@Test
	void shouldCancelRequestWhenHoldDoesNotExistAndNotOnHoldShelfAtBorrowingAgency() {
		// Arrange
		final var borrowingAgencyLocalRequestId = "11463";
		final var borrowingAgencyLocalItemId = "1088431";

		final var patronRequest = createPatronRequest(
			request -> request
				.localRequestId(borrowingAgencyLocalRequestId)
				.localItemId(borrowingAgencyLocalItemId)
				// host LMS item status can not be on hold shelf
				.localItemStatus("")
				.localRequestStatus("PLACED")
				.status(REQUEST_PLACED_AT_BORROWING_AGENCY));

		createSupplierRequest(patronRequest,
			request -> request
				.localId("11987")
				.localItemId("1088432"));

		sierraPatronsAPIFixture.mockGetHoldByIdNotFound(borrowingAgencyLocalRequestId);
		sierraItemsAPIFixture.mockGetItemById(borrowingAgencyLocalItemId,
			exampleSierraItem(borrowingAgencyLocalItemId));

		// Act
		trackingService.run();

		// Assert

		// Workflow will propagate the request to an ultimate state of isFinalised, via isCanceled());
		waitUntilPatronRequestIsFinalised(patronRequest);
	}

	@Test
	void shouldFinaliseRequestWhenSupplierHostlmsHoldIsPLACED() {
		// Arrange
		final var borrowingAgencyLocalRequestId = "56436";
		final var borrowingAgencyLocalItemId = "1088437";

		final var patronRequest = createPatronRequest(
			request -> request
				.localRequestId(borrowingAgencyLocalRequestId)
				.localItemId(borrowingAgencyLocalItemId)
				.localItemStatus("")
				.localRequestStatus("PLACED")
				.status(REQUEST_PLACED_AT_BORROWING_AGENCY));

		createSupplierRequest(patronRequest,
			request -> request
				.localId("11987")
				.localItemId("1088432")
				// local status has to be PLACED
				.localStatus("PLACED"));

		sierraPatronsAPIFixture.mockGetHoldByIdNotFound(borrowingAgencyLocalRequestId);
		sierraItemsAPIFixture.mockGetItemById(borrowingAgencyLocalItemId,
			exampleSierraItem(borrowingAgencyLocalItemId));

		// Act
		trackingService.run();

		// Assert

		// Workflow will propagate the request to an ultimate state of isFinalised, via isCanceled());
		waitUntilPatronRequestIsFinalised(patronRequest);
	}


	@Test
	void shouldFinaliseRequestWhenSupplierItemAvailable() {

		// Arrange
		final var patronRequest = createPatronRequest(
			request -> request
				.localRequestId("24263")
				.localItemId("108843")
				.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
				.status(CANCELLED));

		final var supplyingAgencyLocalRequestId = "11987";
		final var supplyingAgencyLocalItemId = "1088435";

		createSupplierRequest(patronRequest,
			request -> request.localId(supplyingAgencyLocalRequestId)
				.localItemId(supplyingAgencyLocalItemId)
				.localItemStatus("TRANSIT"));

		sierraPatronsAPIFixture.mockGetHoldById(supplyingAgencyLocalRequestId,
			SierraHold.builder()
				.statusCode("0")
				.statusName("on hold.")
				.build());

		sierraItemsAPIFixture.mockGetItemById(supplyingAgencyLocalItemId,
			exampleSierraItem(supplyingAgencyLocalItemId));

		// Act
		trackingService.run();

		// Assert
		waitUntilPatronRequestIsFinalised(patronRequest);
	}

	private void defineHostLms(String hostLmsCode, String baseUrl, MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String KEY = "patron-request-tracking-tests-key";
		final String SECRET = "patron-request-tracking-tests-secret";

		SierraTestUtils.mockFor(mockServerClient, baseUrl)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		hostLmsFixture.createSierraHostLms(hostLmsCode, KEY, SECRET, baseUrl, "item");
	}

	private PatronRequest createPatronRequest(
		Consumer<PatronRequest.PatronRequestBuilder> additionalAttributes) {

		// Arrange
		final var patron = patronFixture.savePatron("homeLibraryCode");
		// final var patronIdentity = patronFixture.savePatronIdentity(patron);

		final var builder = PatronRequest.builder()
			.id(randomUUID())
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.patron(patron);

		additionalAttributes.accept(builder);

		return patronRequestsFixture.savePatronRequest(builder.build());
	}

	private SupplierRequest createSupplierRequest(
		PatronRequest patronRequest,
		Consumer<SupplierRequest.SupplierRequestBuilder> additionalAttributes) {

		final var builder = SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode(SUPPLYING_HOST_LMS_CODE);

		additionalAttributes.accept(builder);

		return supplierRequestsFixture.saveSupplierRequest(builder.build());
	}

	private void waitUntilPatronRequestIsFinalised(PatronRequest patronRequest) {
		await().atMost(5, SECONDS)
			.until(() -> getPatronRequest(patronRequest.getId()), isFinalised());
	}

	private PatronRequest getPatronRequest(UUID patronRequestId) {
		return patronRequestsFixture.findById(patronRequestId);
	}

	private static SierraItem exampleSierraItem(String id) {
		return SierraItem.builder()
			.id(id)
			.statusCode("-")
			.build();
	}
}
