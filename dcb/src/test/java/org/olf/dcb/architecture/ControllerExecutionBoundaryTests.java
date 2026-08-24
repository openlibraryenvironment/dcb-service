package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.ExportController;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/** Protects the explicit dispatch required by ExportController's synchronous waits. */
class ControllerExecutionBoundaryTests {
	@Test
	void exportControllerDispatchesBlockingWork() {
		final var executeOn = ExportController.class.getAnnotation(ExecuteOn.class);

		// Removing or weakening this boundary can deadlock work needing the request event loop.
		assertNotNull(executeOn, "ExportController must declare an execution boundary");
		assertEquals(TaskExecutors.BLOCKING, executeOn.value());
	}
}
