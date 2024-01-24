package org.olf.dcb;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.isFinalised;

import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
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
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class PatronRequestTrackingTests {
	private static final String HOST_LMS_CODE = "patron-request-tracking-tests";

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

		defineHostLms(HOST_LMS_CODE, "https://patron-request-tracking-tests.com", mockServerClient);
		
		this.sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		this.sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldCancelRequestWhenHoldDoesNotExistAndNotOnHoldShelf() {
		// Arrange
		final var patronRequest = createPatronRequest(
			request -> request
				.localRequestId("11890")
				.localItemId("1088431")
				.localItemStatus("")
				.localRequestStatus("PLACED")
				.status(REQUEST_PLACED_AT_BORROWING_AGENCY));

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
				.id(randomUUID())
				.localId("11987")
				.localItemId("1088432")
				.patronRequest(patronRequest)
				.hostLmsCode(HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.getHoldById404("11890");
		sierraItemsAPIFixture.getItemById("1088431");

		// Act
		trackingService.run();

		// Assert

		// Workflow will propagate the request to an ultimate state of isFinalised, via isCanceled());
		waitUntilPatronRequestIsFinalised(patronRequest);
	}

	@Test
	void shouldFinaliseRequestWhenSupplierHostlmsHoldIsPLACED() {
		// Arrange
		final var patronRequest = createPatronRequest(
			request -> request
				.localRequestId("11890")
				.localItemId("1088431")
				.localItemStatus("")
				.localRequestStatus("PLACED")
				.status(REQUEST_PLACED_AT_BORROWING_AGENCY));

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
				// local status has to be PLACED
				.id(randomUUID())
				.localId("11987")
				.localItemId("1088432")
				.localStatus("PLACED")
				.patronRequest(patronRequest)
				.hostLmsCode(HOST_LMS_CODE)
				.build());

		sierraPatronsAPIFixture.getHoldById404("11890");
		sierraItemsAPIFixture.getItemById("1088431");

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
				.localRequestId("11890")
				.localItemId("108843")
				.patronHostlmsCode(HOST_LMS_CODE)
				.status(CANCELLED));

		// the supplier item id has to match with the mock for state change to AVAILABLE
		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
				.id(randomUUID())
				.localId("11987")
				.localItemId("1088431")
				.patronRequest(patronRequest)
				.hostLmsCode(HOST_LMS_CODE)
				.localItemStatus("TRANSIT")
				.build());

		sierraPatronsAPIFixture.getHoldById("11987");
		sierraItemsAPIFixture.getItemById("1088431");

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

		// host LMS item status can not be on hold shelf
		final var patronRequestBuilder = PatronRequest.builder()
			.id(randomUUID())
			.patronHostlmsCode(HOST_LMS_CODE)
			.patron(patron);

		additionalAttributes.accept(patronRequestBuilder);

		return patronRequestsFixture.savePatronRequest(patronRequestBuilder.build());
	}

	private void waitUntilPatronRequestIsFinalised(PatronRequest patronRequest) {
		await().atMost(5, SECONDS)
			.until(() -> getPatronRequest(patronRequest.getId()), isFinalised());
	}

	private PatronRequest getPatronRequest(UUID patronRequestId) {
		return patronRequestsFixture.findById(patronRequestId);
	}
}
