package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemBarcode;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalItemId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
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
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private HandleSupplierRequestConfirmed handleSupplierRequestConfirmed;

	private DataHostLms SUPPLYING_HOST_LMS;

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

		SUPPLYING_HOST_LMS = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, "title");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldProgressPatronRequestToConfirmedWhenLocalSupplierRequestIsConfirmed() {
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

		// save the virtual identity (supplier patron)
		final var localPatronId = "562967";
		final var virtualIdentity = patronFixture.saveIdentity(patron, SUPPLYING_HOST_LMS,
			localPatronId, true, "-", localPatronId, null);

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
				.virtualIdentity(virtualIdentity)
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

		final var updatedLocalSupplyingBarcode = "6837533";

		sierraItemsAPIFixture.mockGetItemById(updatedLocalSupplyingItemId,
			SierraItem.builder()
				.id(updatedLocalSupplyingItemId)
				.barcode(updatedLocalSupplyingBarcode)
				.statusCode("-")
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
			hasLocalItemId(updatedLocalSupplyingItemId),
			hasLocalItemBarcode(updatedLocalSupplyingBarcode)
		));
	}

	@Test
	void shouldProgressPatronRequestToConfirmedWhenLocalSupplierRequestIsInTransit() {
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

		// save the virtual identity (supplier patron)
		final var localPatronId = "562967";
		final var virtualIdentity = patronFixture.saveIdentity(patron, SUPPLYING_HOST_LMS,
			localPatronId, true, "-", localPatronId, null);

		final var localSupplyingHoldId = "3525635";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_TRANSIT)
				.localId(localSupplyingHoldId)
				.localItemId("73653263")
				.localItemBarcode("1745736")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualIdentity)
				.build());

		// Update the hold to be an item level hold
		final var updatedLocalSupplyingItemId = "6736735";

		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId,
			SierraPatronHold.builder()
				.id(localSupplyingHoldId)
				.recordType("i")
				.record(updatedLocalSupplyingItemId)
				.status(SierraCodeTuple.builder()
					.code("0")
					.build())
				.build());

		final var updatedLocalSupplyingBarcode = "355366";

		sierraItemsAPIFixture.mockGetItemById(updatedLocalSupplyingItemId,
			SierraItem.builder()
				.id(updatedLocalSupplyingItemId)
				.barcode(updatedLocalSupplyingBarcode)
				.statusCode("-")
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
			hasLocalStatus(HOLD_TRANSIT),
			hasLocalItemId(updatedLocalSupplyingItemId),
			hasLocalItemBarcode(updatedLocalSupplyingBarcode)
		));
	}

	@Test
	void shouldNotProgressRequestWhenNotPlacedAtSupplyingAgency() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(REQUEST_PLACED_AT_BORROWING_AGENCY)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var applicable = singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> handleSupplierRequestConfirmed.isApplicableFor(ctx)));

		// Assert
		assertThat("Should not be applicable for request status other than placed at supplying agency",
			applicable, is(false));
	}

	@Test
	void shouldNotProgressRequestWhenLocalRequestStatusIsPlaced() {
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

		final var localSupplyingHoldId = "73625225";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_PLACED)
				.localId(localSupplyingHoldId)
				.localItemId("2652563")
				.localItemBarcode("2917564")
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.build());

		// Act
		final var applicable = singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> handleSupplierRequestConfirmed.isApplicableFor(ctx)));

		// Assert
		assertThat("Should not be applicable for local supplier request status of placed",
			applicable, is(false));
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

		// save the virtual identity (supplier patron)
		final var localPatronId = "562967";
		final var virtualIdentity = patronFixture.saveIdentity(patron, SUPPLYING_HOST_LMS,
			localPatronId, true, "-", localPatronId, null);

		final var localSupplyingHoldId = "4625522";
		final var originalLocalItemId = "647375678";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId(originalLocalItemId)
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualIdentity)
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
	void shouldNotUpdateTheLocalItemBarcodeWhenNoneProvided() {
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

		// save the virtual identity (supplier patron)
		final var localPatronId = "562967";
		final var virtualIdentity = patronFixture.saveIdentity(patron, SUPPLYING_HOST_LMS,
			localPatronId, true, "-", localPatronId, null);


		final var localSupplyingHoldId = "2632353";
		final var originalLocalItemId = "5365332";
		final var originalLocalItemBarcode = "2645245";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId(originalLocalItemId)
				.localItemBarcode(originalLocalItemBarcode)
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualIdentity)
				.build());

		// Update the hold to be an item level hold
		final var updatedLocalSupplyingItemId = "3436532";

		sierraPatronsAPIFixture.mockGetHoldById(localSupplyingHoldId,
			SierraPatronHold.builder()
				.id(localSupplyingHoldId)
				.recordType("i")
				.record(updatedLocalSupplyingItemId)
				.status(SierraCodeTuple.builder()
					.code("0")
					.build())
				.build());

		sierraItemsAPIFixture.mockGetItemById(updatedLocalSupplyingItemId,
			SierraItem.builder()
				.id(updatedLocalSupplyingItemId)
				.statusCode("-")
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
			hasLocalItemId(updatedLocalSupplyingItemId),
			hasLocalItemBarcode(originalLocalItemBarcode)
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

		// save the virtual identity (supplier patron)
		final var localPatronId = "562967";
		final var virtualIdentity = patronFixture.saveIdentity(patron, SUPPLYING_HOST_LMS,
			localPatronId, true, "-", localPatronId, null);

		final var localSupplyingHoldId = "5724732";
		final var originalLocalItemId = "73456242";

		supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.localStatus(HOLD_CONFIRMED)
				.localId(localSupplyingHoldId)
				.localItemId(originalLocalItemId)
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.virtualIdentity(virtualIdentity)
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

	@Test
	void shouldTolerateNullPatronRequestStatus() {
		// Arrange
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(null)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		// Act
		final var applicable = singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> handleSupplierRequestConfirmed.isApplicableFor(ctx)));

		// Assert
		assertThat("Should not be applicable for request with no status",
			applicable, is(false));
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
