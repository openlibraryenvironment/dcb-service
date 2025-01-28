package org.olf.dcb.request.workflow;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.*;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.*;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.TRIGGER_SUPPLIER_RENEWAL;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.*;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.*;

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

		sierraItemsAPIFixture = sierraApiFixtureProvider.itemsApiFor(mockServerClient);
		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
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
		final var supplierRequest = defineSupplierRequest(patronRequest, "4324324", existingPatron);

		sierraItemsAPIFixture.checkoutsForItem("4324324");
		sierraPatronsAPIFixture.mockRenewalSuccess("1811242");

		// Act
		final var updatedPatronRequest = supplierRenewal(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(LOANED),
			hasLocalRenewalCount(1),
			hasRenewalCount(1)
		));

		assertRenewalSuccessAudit(updatedPatronRequest);
	}

	@Test
void shouldFailSupplierRenewalWhenRequestFails() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", 1, 0);
		final var existingPatron = patronRequest.getPatron();
		final var supplierRequest = defineSupplierRequest(patronRequest, "4324324", existingPatron);

		sierraItemsAPIFixture.checkoutsForItemWithNoRecordsFound("4324324");

		// Act
		final var updatedPatronRequest = supplierRenewal(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(LOANED),
			hasLocalRenewalCount(1),
			hasRenewalCount(0)
		));

		assertRenewalFailureAudit(updatedPatronRequest);
	}

	@Test
	void shouldNotTriggerSupplierRenewalWhenConsortialSettingIsDisabled() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, false);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", 1, 0);

		// Act
		final var updatedPatronRequest = supplierRenewal(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue(),
			hasStatus(LOANED),
			hasLocalRenewalCount(1),
			hasRenewalCount(0)
		));

		assertNoAuditRecords(updatedPatronRequest);
	}

	@Test
	void shouldNotTriggerSupplierRenewalWhenLocalRenewalCountIsNull() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(LOANED, "LOANED", null, 0);

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> supplierRenewal(patronRequest));

		// Assert
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
