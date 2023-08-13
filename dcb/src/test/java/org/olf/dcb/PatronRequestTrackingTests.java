package org.olf.dcb;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.StatusCodeRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.tracking.TrackingService;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PatronRequestTrackingTests {
	@Inject
	ResourceLoader loader;
	@Inject
	TrackingService trackingService;
	@Inject
	PatronRequestRepository patronRequestRepository;
	@Inject
	SupplierRequestRepository supplierRequestRepository;
	@Inject
	StatusCodeRepository statusCodeRepository;
	@Inject
	private HostLmsFixture hostLmsFixture;
	private static final String HOST_LMS_CODE = "patron-request-tracking-tests";
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-request-tracking-tests.com";
		final String KEY = "patron-request-tracking-tests-key";
		final String SECRET = "patron-request-tracking-tests-secret";
		SierraTestUtils.mockFor(mock, BASE_URL).setValidCredentials(KEY, SECRET, TOKEN, 60);
		this.sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mock, loader);
		this.sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldCancelRequestWhenHoldDoesNotExistAndNotOnHoldShelf() {
		// Arrange
		final var savedPatronRequestId = randomUUID();
		// hostlms item status can not be on hold shelf
		final var patronRequest = Mono.from(patronRequestRepository.save(PatronRequest.builder()
				.id(savedPatronRequestId).localRequestId("11890").localItemId("1088431")
				.localItemStatus("").patronHostlmsCode(HOST_LMS_CODE)
				.status(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY).build())).block();
		Mono.from(supplierRequestRepository.save(SupplierRequest.builder()
			.id(randomUUID()).localId("11987").localItemId("1088432").patronRequest(patronRequest)
			.hostLmsCode(HOST_LMS_CODE).build())).block();
		Mono.from(statusCodeRepository.saveOrUpdate(StatusCode.builder()
				.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
					"StatusCode:"+"PatronRequest"+":"+"REQUEST_PLACED_AT_BORROWING_AGENCY"))
				.model("PatronRequest").code("REQUEST_PLACED_AT_BORROWING_AGENCY").tracked(Boolean.TRUE).build())).block();
		sierraPatronsAPIFixture.getHoldById404("11890");
		sierraItemsAPIFixture.getItemById("1088431");
		// Act
		trackingService.run();
		// Assert
		await().atMost(5, SECONDS)
			.until(() -> Mono.from(patronRequestRepository.findById(savedPatronRequestId)).block(),
				isCanceled());
	}

	@Test
	void shouldFinaliseRequestWhenSupplierHostlmsHoldIsPLACED() {
		// Arrange
		final var savedPatronRequestId = randomUUID();
		final var patronRequest = Mono.from(patronRequestRepository.save(PatronRequest.builder()
			.id(savedPatronRequestId).localRequestId("11890").localItemId("1088431")
			.localItemStatus("").patronHostlmsCode(HOST_LMS_CODE)
			.status(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY).build())).block();
		Mono.from(supplierRequestRepository.save(SupplierRequest.builder()
			// local status has to be PLACED
			.id(randomUUID()).localId("11987").localItemId("1088432").localStatus("PLACED").patronRequest(patronRequest)
			.hostLmsCode(HOST_LMS_CODE).build())).block();
		Mono.from(statusCodeRepository.saveOrUpdate(StatusCode.builder()
			.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
				"StatusCode:"+"PatronRequest"+":"+"REQUEST_PLACED_AT_BORROWING_AGENCY"))
			.model("PatronRequest").code("REQUEST_PLACED_AT_BORROWING_AGENCY").tracked(Boolean.TRUE).build())).block();
		sierraPatronsAPIFixture.getHoldById404("11890");
		sierraItemsAPIFixture.getItemById("1088431");
		// Act
		trackingService.run();
		// Assert
		await().atMost(5, SECONDS)
			.until(() -> Mono.from(patronRequestRepository.findById(savedPatronRequestId)).block(),
				isCanceled());
	}

	@Test
	void shouldFinaliseRequestWhenSupplierItemAvailable() {
		// Arrange
		final var savedPatronRequestId = randomUUID();
		final var patronRequest = Mono.from(patronRequestRepository.save(PatronRequest.builder()
			.id(savedPatronRequestId).localRequestId("11890")
			.localItemId("108843").patronHostlmsCode(HOST_LMS_CODE)
			.status(CANCELLED).build())).block();
		// the supplier item id has to match with the mock for state change to AVAILABLE
		Mono.from(supplierRequestRepository.save(SupplierRequest.builder()
				.id(randomUUID()).localId("11987").localItemId("1088431").patronRequest(patronRequest)
				.hostLmsCode(HOST_LMS_CODE)
                                .localItemStatus("TRANSIT")
                                .build())).block();

		Mono.from(statusCodeRepository.saveOrUpdate(StatusCode.builder()
			.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "StatusCode:"+"PatronRequest"+":"+"CANCELLED"))
			.model("PatronRequest").code("CANCELLED").tracked(Boolean.TRUE).build())).block();

		Mono.from(statusCodeRepository.saveOrUpdate(StatusCode.builder()
			.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "StatusCode:"+"VirtualItem"+":"+"TRANSIT"))
			.model("SupplierItem").code("TRANSIT").tracked(Boolean.TRUE).build())).block();

		Mono.from(statusCodeRepository.saveOrUpdate(StatusCode.builder()
			.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "StatusCode:"+"VirtualItem"+":"+"AVAILABLE"))
			.model("VirtualItem").code("AVAILABLE").tracked(Boolean.FALSE).build())).block();
		sierraPatronsAPIFixture.getHoldById("11987");
		sierraItemsAPIFixture.getItemById("1088431");
		// Act
		trackingService.run();
		// Assert
		await().atMost(5, SECONDS)
			.until(() -> Mono.from(patronRequestRepository.findById(savedPatronRequestId)).block(),
				isFinalised());
	}

	private static Matcher<Object> isCanceled() {
		return hasProperty("status", is(CANCELLED));
	}
	private static Matcher<Object> isFinalised() {
		return hasProperty("status", is(FINALISED));
	}
}
