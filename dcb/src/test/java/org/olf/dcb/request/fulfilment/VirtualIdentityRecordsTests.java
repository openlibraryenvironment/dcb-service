package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
class VirtualIdentityRecordsTests {
	@Inject
	private VirtualIdentityRecords virtualIdentityRecords;
	@Inject
	private PatronRequestRepository patronRequestRepository;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;

	private DataHostLms homeSystem;
	private DataHostLms lenderSystem;

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();

		homeSystem = hostLmsFixture.createDummyHostLms("virtual-records-home");
		lenderSystem = hostLmsFixture.createDummyHostLms("virtual-records-lender");
	}

	@Test
	void forgettingAVirtualPatronUnlinksItsFinishedRequestsAndDeletesOnlyThatRecord() {
		final var patron = patronFixture.savePatron("home-library");
		final var home = identity(patron, homeSystem, "home-1", true);
		final var virtual = identity(patron, lenderSystem, "virtual-1", false);
		final var request = request(patron, home, Status.FINALISED);
		final var supplierRequest = supplierRequest(request, virtual);
		final var inactiveId = inactiveSupplierRequest(request, virtual);

		final var otherPatron = patronFixture.savePatron("home-library");
		final var otherHome = identity(otherPatron, homeSystem, "home-2", true);
		final var otherVirtual = identity(otherPatron, lenderSystem, "virtual-2", false);
		final var otherSupplierRequest = supplierRequest(request(otherPatron, otherHome, Status.FINALISED), otherVirtual);

		singleValueFrom(virtualIdentityRecords.forget(virtual.getId()).thenReturn(true));

		assertThat(patronFixture.findIdentities(patron).stream().map(PatronIdentity::getId).toList(),
			contains(home.getId()));
		assertThat(supplierRequestsFixture.findById(supplierRequest.getId()).getVirtualIdentity(), nullValue());
		assertThat(inactiveSupplierRequestsFixture.findAllFor(request).getFirst().getId(), is(inactiveId));
		assertThat(inactiveSupplierRequestsFixture.findAllFor(request).getFirst().getVirtualIdentity(), nullValue());
		assertThat(supplierRequestsFixture.findById(otherSupplierRequest.getId()).getVirtualIdentity(), notNullValue());
		assertThat(patronFixture.findIdentities(otherPatron).size(), is(2));
	}

	@Test
	void everyRequestNotYetFinalisedOrArchivedCountsAsUnfinished() {
		final var patron = patronFixture.savePatron("home-library");
		final var home = identity(patron, homeSystem, "home-3", true);

		request(patron, home, Status.FINALISED);
		request(patron, home, Status.ARCHIVED);
		assertThat(unfinished(patron), is(0L));

		request(patron, home, Status.ERROR);
		request(patron, home, Status.RETURN_TRANSIT);
		assertThat(unfinished(patron), is(2L));
	}

	private long unfinished(Patron patron) {
		return singleValueFrom(patronRequestRepository.countUnfinishedRequestsForPatron(patron.getId()));
	}

	private PatronIdentity identity(Patron patron, DataHostLms system, String localId, boolean homeIdentity) {
		return patronFixture.saveIdentityAndReturn(patron, system, localId, homeIdentity, "-", "home-library", null);
	}

	private PatronRequest request(Patron patron, PatronIdentity home, Status status) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(home)
			.status(status)
			.build());
	}

	private SupplierRequest supplierRequest(PatronRequest request, PatronIdentity virtual) {
		return supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.hostLmsCode(lenderSystem.getCode())
			.localItemId("item-" + randomUUID())
			.virtualIdentity(virtual)
			.isActive(false)
			.build());
	}

	private UUID inactiveSupplierRequest(PatronRequest request, PatronIdentity virtual) {
		final var id = randomUUID();

		inactiveSupplierRequestsFixture.save(InactiveSupplierRequest.builder()
			.id(id)
			.patronRequest(request)
			.hostLmsCode(lenderSystem.getCode())
			.localItemId("item-" + randomUUID())
			.virtualIdentity(virtual)
			.build());

		return id;
	}
}
