package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.discovery.DiscoveryPatronRequestsController;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

class DiscoveryControllerExecutionBoundaryTests {
	@Test
	void patronRoutesDispatchSynchronousAssertionVerification() {
		final var executeOn = DiscoveryPatronRequestsController.class
			.getAnnotation(ExecuteOn.class);
		assertNotNull(executeOn,
			"DiscoveryPatronRequestsController must declare an execution boundary");
		assertEquals(TaskExecutors.BLOCKING, executeOn.value());
	}
}
