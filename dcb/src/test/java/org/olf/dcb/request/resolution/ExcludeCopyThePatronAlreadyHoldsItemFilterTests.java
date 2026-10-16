package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import lombok.Builder;
import reactor.core.publisher.Mono;

@DcbTest
class ExcludeCopyThePatronAlreadyHoldsItemFilterTests {
	private static final String SUPPLIER = "HELD-SUPPLIER";
	private static final String BORROWER = "HELD-BORROWER";
	private static final String PATRON_LOCAL_ID = "held-patron";
	private static final String COPY_ID = "held-copy-1";

	@Inject
	HostLmsFixture hostLmsFixture;
	@Inject
	PatronFixture patronFixture;
	@Inject
	PatronRequestsFixture patronRequestsFixture;
	@Inject
	SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	ExcludeCopyThePatronAlreadyHoldsItemFilter filter;

	private DataHostLms borrower;
	private Patron patron;

	@BeforeAll
	void beforeAll() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(SUPPLIER, "key", "secret", "https://supplier.example.com", "item");
		borrower = hostLmsFixture.createSierraHostLms(BORROWER, "key", "secret", "https://borrower.example.com", "item");
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();

		patron = patronFixture.savePatron("home-library");
		patronFixture.saveIdentityAndReturn(patron, borrower, PATRON_LOCAL_ID, true, "-", BORROWER, null);
	}

	@Test
	void shouldExcludeACopyThePatronStillHoldsFromAnErroredRequest() {
		earlierRequest(patron, ERROR, "hold-1", HOLD_CONFIRMED);

		assertThat(evaluate(filter, byPatronId(patron.getId())), is(false));
	}

	@Test
	void shouldExcludeItForAPatronKnownOnlyByTheirIdAtTheBorrowingSystem() {
		earlierRequest(patron, ERROR, "hold-1", HOLD_CONFIRMED);

		assertThat(evaluate(filter, byLocalId(PATRON_LOCAL_ID)), is(false));
	}

	@Test
	void shouldIncludeTheCopyOnceTheHoldIsRecordedCancelled() {
		earlierRequest(patron, ERROR, "hold-1", HOLD_CANCELLED);

		assertThat(evaluate(filter, byPatronId(patron.getId())), is(true));
	}

	@Test
	void shouldIncludeTheCopyOnceTheHoldIsRecordedMissing() {
		earlierRequest(patron, ERROR, "hold-1", HOLD_MISSING);

		assertThat(evaluate(filter, byPatronId(patron.getId())), is(true));
	}

	@Test
	void shouldIncludeTheCopyWhenNoHoldWasPlaced() {
		earlierRequest(patron, ERROR, null, null);

		assertThat(evaluate(filter, byPatronId(patron.getId())), is(true));
	}

	@Test
	void shouldIncludeTheCopyForAnotherPatron() {
		earlierRequest(patron, ERROR, "hold-1", HOLD_CONFIRMED);

		final var another = patronFixture.savePatron("home-library");

		assertThat(evaluate(filter, byPatronId(another.getId())), is(true));
	}

	@Test
	void shouldIncludeTheCopyForTheRequestBeingResolved() {
		final var earlier = earlierRequest(patron, ERROR, "hold-1", HOLD_CONFIRMED);

		final var parameters = Parameters.builder()
			.borrowingHostLmsCode(BORROWER)
			.patronId(patron.getId())
			.patronRequestId(earlier.getId())
			.build();

		assertThat(evaluate(filter, parameters), is(true));
	}

	@Test
	void shouldIncludeTheCopyOnceTheEarlierRequestIsFinalised() {
		earlierRequest(patron, FINALISED, "hold-1", HOLD_CONFIRMED);

		assertThat(evaluate(filter, byPatronId(patron.getId())), is(true));
	}

	@Test
	void shouldIncludeTheCopyWhenTheRecordsCannotBeRead() {
		final var unreadable = mock(PatronRequestRepository.class);
		when(unreadable.existsLiveSupplierHoldForPatronOnCopy(any(), any(), any(), any(), any(), any(), any()))
			.thenReturn(Mono.error(new RuntimeException("database unavailable")));

		final var overUnreadableRecords = new ExcludeCopyThePatronAlreadyHoldsItemFilter(unreadable);

		assertThat(evaluate(overUnreadableRecords, byPatronId(patron.getId())), is(true));
	}

	private PatronRequest earlierRequest(Patron requester, PatronRequest.Status status,
		String supplierHoldId, String supplierHoldStatus) {

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(requester)
			.patronHostlmsCode(BORROWER)
			.status(status)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode(SUPPLIER)
			.localItemId(COPY_ID)
			.localId(supplierHoldId)
			.localStatus(supplierHoldStatus)
			.isActive(true)
			.build());

		return patronRequest;
	}

	private static Parameters byPatronId(UUID patronId) {
		return Parameters.builder().borrowingHostLmsCode(BORROWER).patronId(patronId).build();
	}

	private static Parameters byLocalId(String patronLocalId) {
		return Parameters.builder().borrowingHostLmsCode(BORROWER).patronLocalId(patronLocalId).build();
	}

	private boolean evaluate(ItemFilter itemFilter, ItemFilterParameters parameters) {
		return singleValueFrom(itemFilter.filterItem(parameters).apply(copy()));
	}

	private Item copy() {
		return Item.builder()
			.localId(COPY_ID)
			.agency(DataAgency.builder()
				.code(SUPPLIER + "-agency")
				.hostLms(hostLmsFixture.findByCode(SUPPLIER))
				.build())
			.build();
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode, String requestedVolumeDesignation, UUID patronId,
		String patronLocalId, UUID patronRequestId)
		implements ItemFilterParameters { }
}
