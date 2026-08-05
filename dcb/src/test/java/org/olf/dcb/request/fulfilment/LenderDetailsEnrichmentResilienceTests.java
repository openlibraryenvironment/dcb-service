package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import reactor.core.publisher.Mono;

/**
 * Loading the supplier Host LMS is an enrichment that lets SUPPLIER capability
 * and tracking resolve per-host. It must never be able to abort context
 * decoration - doing so would stall the imperative workflow for every request
 * on the affected system. A failed lookup leaves lenderSystem null, which the
 * capability resolver treats as "use instance-wide config", i.e. IMPERATIVE.
 */
class LenderDetailsEnrichmentResilienceTests {
	private final HostLmsService hostLmsService = mock(HostLmsService.class);

	private final RequestWorkflowContextHelper helper = new RequestWorkflowContextHelper(
		mock(SupplierRequestService.class),
		mock(SupplierRequestRepository.class),
		mock(PatronRequestRepository.class),
		mock(AgencyRepository.class),
		mock(LibraryRepository.class),
		mock(LocationService.class),
		hostLmsService,
		mock(PatronService.class),
		mock(PatronRequestAuditService.class));

	// per_class test instance lifecycle is the module default, so the mock is
	// shared between methods - reset it to keep each stubbing independent.
	@BeforeEach
	void resetMocks() {
		reset(hostLmsService);
	}

	@Test
	void loadsLenderSystemWhenHostIsKnown() {
		final var lenderSystem = DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("supplier-host")
			.name("supplier-host")
			.build();

		when(hostLmsService.findByCode("supplier-host")).thenReturn(Mono.just(lenderSystem));

		final var context = helper.decorateContextWithLenderDetails(context()).block();

		assertThat(context, is(notNullValue()));
		assertThat(context.getLenderSystem(), is(sameInstance(lenderSystem)));
		assertThat(context.getLenderSystemCode(), is("supplier-host"));
	}

	@Test
	void unknownHostLeavesLenderSystemUnsetWithoutFailing() {
		// UnknownHostLmsException is not constructible from here; the guard is
		// deliberately type-agnostic, so any lookup error exercises the same path.
		when(hostLmsService.findByCode("supplier-host"))
			.thenReturn(Mono.error(new RuntimeException("No Host LMS found for code supplier-host")));

		final var context = helper.decorateContextWithLenderDetails(context()).block();

		assertThat(context, is(notNullValue()));
		assertThat(context.getLenderSystem(), is(nullValue()));
		assertThat(context.getLenderSystemCode(), is("supplier-host"));
	}

	/**
	 * The regression that matters: a transient infrastructure fault is not an
	 * UnknownHostLmsException, and must not take the workflow down either.
	 */
	@Test
	void transientLookupFailureLeavesLenderSystemUnsetWithoutFailing() {
		when(hostLmsService.findByCode("supplier-host"))
			.thenReturn(Mono.error(new IllegalStateException("connection pool exhausted")));

		final var context = helper.decorateContextWithLenderDetails(context()).block();

		assertThat(context, is(notNullValue()));
		assertThat(context.getLenderSystem(), is(nullValue()));
	}

	@Test
	void emptyLookupLeavesLenderSystemUnsetWithoutFailing() {
		when(hostLmsService.findByCode("supplier-host")).thenReturn(Mono.empty());

		final var context = helper.decorateContextWithLenderDetails(context()).block();

		assertThat(context, is(notNullValue()));
		assertThat(context.getLenderSystem(), is(nullValue()));
	}

	@Test
	void contextWithoutSupplierRequestIsPassedThroughUntouched() {
		final var context = new RequestWorkflowContext();

		final var result = helper.decorateContextWithLenderDetails(context).block();

		assertThat(result, is(sameInstance(context)));
		assertThat(result.getLenderSystem(), is(nullValue()));
	}

	private static RequestWorkflowContext context() {
		return new RequestWorkflowContext()
			.setSupplierRequest(new SupplierRequest()
				.setHostLmsCode("supplier-host")
				.setLocalAgency("supplier-agency"));
	}
}
