package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.sierra.Paths.itemPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronCheckoutPath;
import static org.olf.dcb.core.interaction.sierra.Paths.patronPath;
import static org.olf.dcb.core.model.FunctionalSettingType.TRIGGER_SUPPLIER_RENEWAL;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalIdAsString;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.briefDescriptionContains;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasFromStatus;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasToStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasLocalRenewalCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasRenewalCount;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.isNotOutOfSequence;
import static org.olf.dcb.test.matchers.PatronRequestMatchers.isOutOfSequence;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import services.k_int.interaction.sierra.CheckoutEntry;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SupplierRenewalTransitionTests {
	private static final String BORROWING_HOST_LMS_CODE = "next-supplier-borrowing-tests";
	private static final String SUPPLYING_HOST_LMS_CODE = "next-supplier-tests";

	private static final String SUPPLYING_HOST_LMS_BASE_URL = "https://supplying-host-lms.com";

	private static final String LOANED_LOCAL_ITEM_STATUS = "LOANED";

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

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var token = "test-token";
		final var key = "key";
		final var secret = "secret";
		final var borrowingHostLmsBaseUrl = "https://borrowing-host-lms.com";

		hostLmsFixture.deleteAll();

		SierraTestUtils.mockFor(mockServerClient, SUPPLYING_HOST_LMS_BASE_URL)
			.setValidCredentials(key, secret, token, 3600);

		SierraTestUtils.mockFor(mockServerClient, borrowingHostLmsBaseUrl)
			.setValidCredentials(key, secret, token, 3600);

		borrowingHostLms = hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE,
			key, secret, borrowingHostLmsBaseUrl);

		supplyingHostLms = hostLmsFixture.createSierraHostLms(SUPPLYING_HOST_LMS_CODE,
			key, secret, SUPPLYING_HOST_LMS_BASE_URL);

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

		final var patronRequest = definePatronRequest(LOANED, LOANED_LOCAL_ITEM_STATUS, 1);

		final var localSupplyingItemId = generateNumericLocalIdAsString();
		final var localSupplyingPatronId = generateNumericLocalIdAsString();

		defineSupplierRequest(patronRequest, localSupplyingPatronId, localSupplyingItemId);

		final var checkoutId = generateNumericLocalIdAsString();

		final var checkout = CheckoutEntry.builder()
			.id(toSupplyingHostLmsUrl(patronCheckoutPath(checkoutId)))
			.patron(toSupplyingHostLmsUrl(patronPath(localSupplyingPatronId)))
			.item(toSupplyingHostLmsUrl(itemPath(localSupplyingItemId)))
			.build();

		sierraItemsAPIFixture.checkoutsForItem(localSupplyingItemId, checkout);
		sierraPatronsAPIFixture.mockRenewalSuccess(checkoutId, checkout);

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

		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);

		assertThat("There should be one matching audit entry",
			audits, hasItem(allOf(
				briefDescriptionContains("Supplier renewal : Placed"),
				hasFromStatus(LOANED),
				hasToStatus(LOANED)
			))
		);
	}

	@Test
	void shouldUpdateRenewalCountWhenSupplierRenewalRequestFails() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, true);

		final var patronRequest = definePatronRequest(LOANED, LOANED_LOCAL_ITEM_STATUS, 1);

		final var localSupplyingItemId = generateNumericLocalIdAsString();
		final var localSupplyingPatronId = generateNumericLocalIdAsString();

		defineSupplierRequest(patronRequest, localSupplyingPatronId, localSupplyingItemId);

		sierraItemsAPIFixture.checkoutsForItemWithNoRecordsFound(localSupplyingItemId);

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

		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);

		assertThat("There should be one matching audit entry",
			audits, hasItem(allOf(
				briefDescriptionContains("Supplier renewal : Failed"),
				hasFromStatus(LOANED),
				hasToStatus(LOANED)
			))
		);
	}

	@Test
	void shouldOnlyUpdateRenewalCountWhenConsortialSettingIsDisabled() {
		// Arrange
		consortiumFixture.createConsortiumWithFunctionalSetting(TRIGGER_SUPPLIER_RENEWAL, false);

		final var patronRequest = definePatronRequest(LOANED, LOANED_LOCAL_ITEM_STATUS, 1);
		defineSupplierRequest(patronRequest);

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

		final var patronRequest = definePatronRequest(LOANED, LOANED_LOCAL_ITEM_STATUS, 1);
		defineSupplierRequest(patronRequest);

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

		final var patronRequest = definePatronRequest(LOANED, LOANED_LOCAL_ITEM_STATUS, null);
		defineSupplierRequest(patronRequest);

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

		final var patronRequest = definePatronRequest(CANCELLED, LOANED_LOCAL_ITEM_STATUS, 1);
		defineSupplierRequest(patronRequest);

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

		final var patronRequest = definePatronRequest(CANCELLED, "TRANSIT", 1);
		defineSupplierRequest(patronRequest);

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

		final var patronRequest = definePatronRequest(CANCELLED, LOANED_LOCAL_ITEM_STATUS, 0);
		defineSupplierRequest(patronRequest);

		// Act
		final var exception = assertThrows(RuntimeException.class, () -> supplierRenewal(patronRequest));

		// Assert
		sierraPatronsAPIFixture.verifyNoCheckoutRelatedRequestsMade();

		assertThat(exception.getMessage(), containsString("Supplier renewal is not applicable for request"));

		assertNoAuditRecords(patronRequest);
	}

	private void assertNoAuditRecords(PatronRequest updatedPatronRequest) {
		final var audits = patronRequestsFixture.findAuditEntries(updatedPatronRequest);

		assertThat(audits, is(empty()));
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

	private PatronRequest definePatronRequest(PatronRequest.Status status,
		String localItemStatus, Integer localRenewalCount) {

		final var patron = patronFixture.definePatron(generateNumericLocalIdAsString(), "home-library",
			borrowingHostLms, null);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.localItemId(generateNumericLocalIdAsString())
			.localItemStatus(localItemStatus)
			.localRenewalCount(localRenewalCount)
			.renewalCount(0)
			.patron(patron)
			.status(status)
			.requestingIdentity(getValue(patron, Patron::getPatronIdentities, List::getFirst, null))
			.localRequestId(generateNumericLocalIdAsString())
			// This is necessary for the test that uses the request workflow service
			.currentStatusTimestamp(Instant.now())
			.build();

		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private void defineSupplierRequest(PatronRequest patronRequest) {
		defineSupplierRequest(patronRequest, generateNumericLocalIdAsString(),
			generateNumericLocalIdAsString());
	}

	private void defineSupplierRequest(PatronRequest patronRequest, String localPatronId, String localItemId) {
		final var existingPatron = getValueOrNull(patronRequest, PatronRequest::getPatron);

		final var patronIdentity = patronFixture.saveIdentityAndReturn(existingPatron, supplyingHostLms,
			localPatronId, false, "-", SUPPLYING_HOST_LMS_CODE, null);

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode(SUPPLYING_HOST_LMS_CODE)
			.localItemId(localItemId)
			.localItemBarcode(generateBarcode())
			.virtualIdentity(patronIdentity)
			.build());
	}

	private static String toSupplyingHostLmsUrl(String subPath) {
		return toUrl(SUPPLYING_HOST_LMS_BASE_URL, subPath);
	}

	private static String toUrl(String baseUrl, String subPath) {
		return baseUrl + subPath;
	}
}
