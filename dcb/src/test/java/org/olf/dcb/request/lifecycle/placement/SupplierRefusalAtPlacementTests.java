package org.olf.dcb.request.lifecycle.placement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.remediation.RemediationCode.FOLIO_REQUEST_POLICY_REFUSED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.interaction.SupplierRefusedRequestProblem;
import org.olf.dcb.core.remediation.RemediationCatalogue;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplierPlacementGate;
import org.olf.dcb.storage.SupplierRequestRepository;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;
import reactor.core.publisher.Mono;

class SupplierRefusalAtPlacementTests {
	private SupplierPlacementGate gate;
	private SupplyingAgencyRequestStrategy strategy;
	private RequestWorkflowContext context;
	private SupplyingAgencyRequestStrategyService service;

	// The test instance is shared across tests (junit-platform.properties), so the mocks are not
	@BeforeEach
	void beforeEach() {
		final var resolver = mock(SupplyingAgencyRequestStrategyResolver.class);
		gate = mock(SupplierPlacementGate.class);
		strategy = mock(SupplyingAgencyRequestStrategy.class);
		context = new RequestWorkflowContext();
		service = new SupplyingAgencyRequestStrategyService(
			resolver, mock(SupplyingAgencyRequestProjector.class), mock(SupplierRequestRepository.class), gate,
			new RemediationCatalogue(new ResourceResolver(), ObjectMapper.getDefault()));

		when(gate.check(context)).thenReturn(Mono.just(SupplierPreflight.notChecked()));
		when(gate.warnIfUnreachable(any(), any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		when(resolver.resolve(any(), any())).thenReturn(strategy);
	}

	@Test
	void aSupplierThatRefusesTheRequestIsTreatedAsUnableToSupply() {
		when(strategy.place(context)).thenReturn(Mono.error(new SupplierRefusedRequestProblem("COLLEGE",
			FOLIO_REQUEST_POLICY_REFUSED, Map.of("hostLms", "COLLEGE", "requestType", "Hold", "patronGroup", "DCB patrons"),
			"raw response", null, null)));
		when(gate.refuse(any(), any())).thenReturn(Mono.just(context));

		final var placed = singleValueFrom(service.place(context));

		assertThat(placed, sameInstance(context));
		// The reason and the fix are the catalogue's words for this refusal
		verify(gate).refuse(context, SupplierPreflight.cannotSupply(
			"COLLEGE's request policy does not allow a Hold request for patron group \"DCB patrons\" on this item",
			"In COLLEGE's FOLIO circulation rules, allow Hold requests in the request policy that applies to patron group "
				+ "\"DCB patrons\" and this item. Allow both Page and Hold for every patron group DCB uses: FOLIO pages an "
				+ "available copy, and asks for a Hold once a copy is already paged or on loan."));
	}

	@Test
	void anyOtherFailureStillFailsThePlacement() {
		when(strategy.place(context)).thenReturn(Mono.error(new IllegalStateException("boom")));

		assertThrows(IllegalStateException.class, () -> singleValueFrom(service.place(context)));

		verify(gate, never()).refuse(any(), any());
	}
}
