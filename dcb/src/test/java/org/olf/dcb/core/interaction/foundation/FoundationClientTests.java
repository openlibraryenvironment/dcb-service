package org.olf.dcb.core.interaction.foundation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * PR-3: FoundationClient extends AbstractHostLmsClient.
 *
 * Proves the two things PR-3 is about:
 *   1. Operations DCB has not wired for this integration return Mono.empty()
 *      (inherited) rather than null - no more NPEs when the workflow calls an
 *      unimplemented host method.
 *   2. Implemented operations delegate to the resolved protocol strategy.
 *
 * Pure unit test - Mockito + StepVerifier, no Micronaut context or Testcontainers.
 */
class FoundationClientTests {

	private FoundationClient clientBackedBy(NcipAdaptor baseAdapter) {
		final var lms = mock(HostLms.class);
		when(lms.getCode()).thenReturn("TEST-NCIP");
		// Empty config -> base-protocol defaults to NCIP, no per-op overrides.
		when(lms.getClientConfig()).thenReturn(Map.of());

		final var beanContext = mock(BeanContext.class);
		when(beanContext.createBean(eq(NcipAdaptor.class), eq(lms))).thenReturn(baseAdapter);

		return new FoundationClient(lms, beanContext);
	}

	@Test
	void unwiredOperationsReturnEmptyNotNull() {
		final var client = clientBackedBy(mock(NcipAdaptor.class));

		// A spread of operations FoundationClient does not override - all inherited
		// from AbstractHostLmsClient. The point is that none of these are null.
		assertNotNull(client.createBib(null));
		assertNotNull(client.getSettings());

		StepVerifier.create(client.createBib(null)).verifyComplete();
		StepVerifier.create(client.cancelHoldRequest(null)).verifyComplete();
		StepVerifier.create(client.getItemByBarcode("anything")).verifyComplete();
	}

	@Test
	void checkOutDelegatesToCirculationStrategy() {
		final var baseAdapter = mock(NcipAdaptor.class);
		when(baseAdapter.checkOutItem(any())).thenReturn(Mono.just("OK"));

		final var client = clientBackedBy(baseAdapter);

		StepVerifier.create(client.checkOutItemToPatron(mock(CheckoutItemCommand.class)))
			.expectNext("OK")
			.verifyComplete();
	}

	@Test
	void getPatronByLocalIdDelegatesToPatronStrategy() {
		final var baseAdapter = mock(NcipAdaptor.class);
		when(baseAdapter.findPatron("p1"))
			.thenReturn(Mono.just(mock(org.olf.dcb.core.interaction.Patron.class)));

		final var client = clientBackedBy(baseAdapter);

		StepVerifier.create(client.getPatronByLocalId("p1"))
			.expectNextCount(1)
			.verifyComplete();
	}
}
