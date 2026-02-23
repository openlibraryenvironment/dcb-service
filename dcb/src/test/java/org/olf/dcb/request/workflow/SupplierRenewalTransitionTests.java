package org.olf.dcb.request.workflow;


import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.TRIGGER_SUPPLIER_RENEWAL;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.briefDescriptionContains;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasLocalRenewalCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasRenewalCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.isNotOutOfSequence;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.isOutOfSequence;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.ConsortiumFixture;
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
class SupplierRenewalTransitionTests {

	@Inject private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject private PatronFixture patronFixture;
	@Inject private PatronRequestsFixture patronRequestsFixture;
	@Inject private SupplierRequestsFixture supplierRequestsFixture;
	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private ConsortiumFixture consortiumFixture;
	@Inject private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject private SupplierRenewalTransition supplierRenewalTransition;
	@Inject private PatronRequestWorkflowService patronRequestWorkflowService;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;
	private DataHostLms borrowingHostLms;
	private DataHostLms supplyingHostLms;

	private static final String BORROWING_HOST_LMS_CODE = "next-supplier-borrowing-tests";
	private static final String SUPPLYING_HOST_LMS_CODE = "next-supplier-tests";

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var token = "test-token";
		final var key = "key";
		final var secret = "secret";
		final var supplyingHostLmsBaseUrl = "https://supplying-host-lms.com";
		final var borrowingHostLmsBaseUrl = "https://borrowing-host-lms.com";

		hostLmsFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, supplyingHostLmsBaseUrl)
			.setValidCredentials(key, secret, token, 60);

		SierraTestUtils.mockFor(mockServerClient, borrowingHostLmsBaseUrl)
			.setValidCredentials(key, secret, token, 60);

		borrowingHostLms = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE,
			key, secret, borrowingHostLmsBaseUrl);

		supplyingHostLms = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE,
			key, secret, supplyingHostLmsBaseUrl);

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient);
		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		consortiumFixture.deleteAll();
	}

	@Test
	void shouldTriggerSupplierRenewalWhenConditionsAreMet() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", 1, 0);
		final var existingPatron = patronRequest.getPatron();

		defineSupplierRequest(patronRequest, "4324324", existingPatron);

		final var checkoutId = sierraItemsAPIFixture.checkoutsForItem("4324324");
		sierraPatronsAPIFixture.mockRenewalSuccess(checkoutId);

		// Act
		final var updatedPatronRequest = supplierRenewal(patronRequest);

		// Assert
		sierraPatronsAPIFixture.verifyRenewalRequestMade(checkoutId);

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(LOANED),
			hasLocalRenewalCount(1),
			hasRenewalCount(1),
			isNotOutOfSequence()
		));

		assertRenewalSuccessAudit(updatedPatronRequest);
	}

	@Test
	void shouldUpdateRenewalCountWhenSupplierRenewalRequestFails() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", 1, 0);
		final var existingPatron = patronRequest.getPatron();

		defineSupplierRequest(patronRequest, "4324324", existingPatron);

		sierraItemsAPIFixture.checkoutsForItemWithNoRecordsFound("4324324");

		// Act
		final var updatedPatronRequest = supplierRenewal(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(LOANED),
			hasLocalRenewalCount(1),
			hasRenewalCount(1),
			isOutOfSequence()
		));

		assertRenewalFailureAudit(updatedPatronRequest);
	}

	@Test
	void shouldOnlyUpdateRenewalCountWhenConsortialSettingIsDisabled() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, false);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", 1, 0);

		// Act
		final var updatedPatronRequest = supplierRenewal(patronRequest);

		// Assert
		sierraPatronsAPIFixture.verifyNoCheckoutRelatedRequestsMade();

		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(LOANED),
			hasLocalRenewalCount(1),
			hasRenewalCount(1),
			isOutOfSequence()
		));

		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);

		assertThat("There should be one matching audit entry",
			audits, hasItem(allOf(
				briefDescriptionContains("Supplier renewal : Skipping supplier renewal as setting disabled"),
				hasFromStatus(LOANED),
				hasToStatus(LOANED)
			))
		);
	}

	@Test
	void shouldNotContinuouslyTriggerSupplierRenewalWhenConsortialSettingIsDisabled() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, false);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", 1, 0);

		// Act

		// Will fail with a timeout exception if the transition is repeatedly progressed
		// No assertions after this due to potential solution will change checks
		singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(patronRequestWorkflowService::progressUsing)
				.timeout(Duration.ofSeconds(30)));
	}

	@Test
	void shouldNotTriggerSupplierRenewalWhenLocalRenewalCountIsNull() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", null, 0);

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> supplierRenewal(patronRequest));

		// Assert
		sierraPatronsAPIFixture.verifyNoCheckoutRelatedRequestsMade();

		assertThat(exception.getMessage(), containsString("Supplier renewal is not applicable for request"));

		assertNoAuditRecords(patronRequest);
	}

	@Test
	void shouldNotTriggerSupplierRenewalWhenRequestIsNotLoaned() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(CANCELLED, "LOANED", 1, 0);

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> supplierRenewal(patronRequest));

		// Assert
		sierraPatronsAPIFixture.verifyNoCheckoutRelatedRequestsMade();

		assertThat(exception.getMessage(), containsString("Supplier renewal is not applicable for request"));

		assertNoAuditRecords(patronRequest);
	}

	@Test
	void shouldNotTriggerSupplierRenewalWhenItemHasBeenReturned() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(CANCELLED, "TRANSIT", 1, 0);

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> supplierRenewal(patronRequest));

		// Assert
		sierraPatronsAPIFixture.verifyNoCheckoutRelatedRequestsMade();

		assertThat(exception.getMessage(), containsString("Supplier renewal is not applicable for request"));

		assertNoAuditRecords(patronRequest);
	}

	@Test
	void shouldNotTriggerSupplierRenewalWhenNoRenewalDetected() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(CANCELLED, "LOANED", 0, 0);

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> supplierRenewal(patronRequest));

		// Assert
		sierraPatronsAPIFixture.verifyNoCheckoutRelatedRequestsMade();

		assertThat(exception.getMessage(), containsString("Supplier renewal is not applicable for request"));

		assertNoAuditRecords(patronRequest);
	}

	private void assertNoAuditRecords(PatronRequest updatedPatronRequest) {
		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);
		assertThat(audits.size(), is(0));
	}

	private void assertRenewalSuccessAudit(PatronRequest updatedPatronRequest) {
		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);

		assertThat("There should be one matching audit entry",
			audits, hasItem(allOf(
				briefDescriptionContains("Supplier renewal : Placed"),
				hasFromStatus(LOANED),
				hasToStatus(LOANED)
			))
		);
	}

	private void assertRenewalFailureAudit(PatronRequest updatedPatronRequest) {
		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);

		assertThat("There should be one matching audit entry",
			audits, hasItem(allOf(
				briefDescriptionContains("Supplier renewal : Failed"),
				hasFromStatus(LOANED),
				hasToStatus(LOANED)
			))
		);
	}

	private PatronRequest supplierRenewal(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.flatMap(ctx -> {
				if (!supplierRenewalTransition.isApplicableFor(ctx)) {
					return Mono.error(new RuntimeException("Supplier renewal is not applicable for request"));
				}

				return supplierRenewalTransition.attempt(ctx);
			})
			.thenReturn(patronRequest));
	}

	private PatronRequest definePatronRequest(PatronRequest.Status status, String localItemStatus,
			Integer localRenewalCount, Integer renewalCount) {

		final var patron = patronFixture.definePatron("365636", "home-library",
			borrowingHostLms, null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.localItemId("4324324")
			.localItemStatus(localItemStatus)
			.localRenewalCount(localRenewalCount)
			.renewalCount(renewalCount)
			.patron(patron)
			.status(status)
			.requestingIdentity(patron.getPatronIdentities().get(0))
			.localRequestId("3219073408")
			// This is necessary for the test that uses the request workflow service
			.currentStatusTimestamp(Instant.now())
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);

		return patronRequest;
	}

	private SupplierRequest defineSupplierRequest(PatronRequest patronRequest, String localItemId, Patron existingPatron) {

		final var patronIdentity = patronFixture.saveIdentityAndReturn(existingPatron, supplyingHostLms,
			"1182843", false, "-", SUPPLYING_HOST_LMS_CODE, null);

		return supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
				.id(UUID.randomUUID())
				.patronRequest(patronRequest)
				.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
				.localItemId(localItemId)
				.localItemBarcode("123456789")
				.virtualIdentity(patronIdentity)
				.build());
	}
}
