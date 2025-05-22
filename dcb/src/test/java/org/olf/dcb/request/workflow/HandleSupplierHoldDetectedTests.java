package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.PatronRequest.RenewalStatus.ALLOWED;
import static org.olf.dcb.core.model.PatronRequest.RenewalStatus.DISALLOWED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
class HandleSupplierHoldDetectedTests {
	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	HandleSupplierHoldDetected handleSupplierHoldDetectedTransition;

	@Inject
	AgencyFixture agencyFixture;
	@Inject
	PatronFixture patronFixture;
	@Inject
	PatronRequestsFixture patronRequestsFixture;
	@Inject
	SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	HostLmsFixture hostLmsFixture;

	private DataHostLms supplyingHostLms;
	private DataHostLms borrowingHostLms;
	private DataAgency borrowingAgency;

	@BeforeEach
	void beforeAll() {
		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();

		supplyingHostLms = hostLmsFixture.createDummyHostLms("supplying-host-lms");
		borrowingHostLms = hostLmsFixture.createDummyHostLms("borrowing-host-lms");

		borrowingAgency = agencyFixture.defineAgency("borrowing-agency",
			"Borrowing Agency", borrowingHostLms);
	}

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldBeApplicableWhenRenewalStatusIsAllowed() {
		// Arrange
		final var patronRequest = definePatronRequest(LOANED, ALLOWED);

		supplierRequestsFixture.saveSupplierRequest(
			supplierRequestWithRequiredFields(patronRequest, supplyingHostLms.getCode())
				.localHoldCount(1)
				.build());

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should be applicable", isApplicable, is(true));
	}

	@Test
	void shouldNotBeApplicableWhenRenewalStatusIsNull() {
		// Arrange
		final var patronRequest = definePatronRequest(LOANED, null);

		supplierRequestsFixture.saveSupplierRequest(
			supplierRequestWithRequiredFields(patronRequest, supplyingHostLms.getCode())
				.localHoldCount(1)
				.build());

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should be applicable", isApplicable, is(true));
	}

	@Test
	void shouldNotBeApplicableWhenStatusIsNotLoaned() {
		// Arrange
		final var patronRequest = definePatronRequest(
			REQUEST_PLACED_AT_BORROWING_AGENCY, ALLOWED);

		supplierRequestsFixture.saveSupplierRequest(
			supplierRequestWithRequiredFields(patronRequest, supplyingHostLms.getCode())
				.localHoldCount(1)
				.build());

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable", isApplicable, is(false));
	}

	@Test
	void shouldNotBeApplicableWhenThereIsNoAssociatedSupplierRequest() {
		// Arrange
		final var patronRequest = definePatronRequest(LOANED, ALLOWED);

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable", isApplicable, is(false));
	}

	@Test
	void shouldNotBeApplicableWhenSupplierHoldCountIsZero() {
		// Arrange
		final var patronRequest = definePatronRequest(LOANED, ALLOWED);

		supplierRequestsFixture.saveSupplierRequest(
			supplierRequestWithRequiredFields(patronRequest, supplyingHostLms.getCode())
				.localHoldCount(0)
				.build());

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable", isApplicable, is(false));
	}

	@Test
	void shouldNotBeApplicableWhenSupplierHoldCountIsNull() {
		// Arrange
		final var patronRequest = definePatronRequest(LOANED, ALLOWED);

		supplierRequestsFixture.saveSupplierRequest(
			supplierRequestWithRequiredFields(patronRequest, supplyingHostLms.getCode())
				.localHoldCount(null)
				.build());

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable", isApplicable, is(false));
	}

	@Test
	void shouldNotBeApplicableWhenRenewalStatusIsNotAllowed() {
		// Arrange
		final var patronRequest = definePatronRequest(LOANED, DISALLOWED);

		supplierRequestsFixture.saveSupplierRequest(
			supplierRequestWithRequiredFields(patronRequest, supplyingHostLms.getCode())
				.localHoldCount(1)
				.build());

		// Act
		final var isApplicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable", isApplicable, is(false));
	}

	private Boolean isApplicable(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
			.map(handleSupplierHoldDetectedTransition::isApplicableFor));
	}

	private PatronRequest definePatronRequest(PatronRequest.Status status,
		PatronRequest.RenewalStatus renewalStatus) {

		final var patron = patronFixture.definePatron(randomUUID().toString(),
			"home-library", borrowingHostLms, borrowingAgency);

		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.renewalStatus(renewalStatus)
			.build());
	}

	private SupplierRequest.SupplierRequestBuilder<?, ?> supplierRequestWithRequiredFields(
		PatronRequest patronRequest, String hostLmsCode) {

		return SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localId(randomUUID().toString())
			.localItemId(randomUUID().toString())
			.hostLmsCode(hostLmsCode);
	}
}
