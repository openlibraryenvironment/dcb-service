package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.AdminController;
import org.olf.dcb.core.api.ExportController;
import org.olf.dcb.core.api.SqlController;
import org.olf.dcb.request.lifecycle.ncip.NcipController;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/** Protects the explicit dispatch required by ExportController's synchronous waits. */
class ControllerExecutionBoundaryTests {
	@Test
	void exportControllerDispatchesBlockingWork() {
		assertBlocking(ExportController.class);
	}

	@Test
	void ncipControllerDispatchesSynchronousXmlWork() {
		assertBlocking(NcipController.class);
	}

	@Test
	void sqlControllerDispatchesSynchronousJdbcWork() {
		assertBlocking(SqlController.class);
	}

	@Test
	void adminThreadDumpDispatchesJvmInspection() throws NoSuchMethodException {
		final var executeOn = AdminController.class.getMethod("threads")
			.getAnnotation(ExecuteOn.class);

		assertNotNull(executeOn, "AdminController.threads must declare an execution boundary");
		assertEquals(TaskExecutors.BLOCKING, executeOn.value());
	}

	private static void assertBlocking(Class<?> controller) {
		final var executeOn = controller.getAnnotation(ExecuteOn.class);

		// Removing or weakening these boundaries can starve work needing Netty event loops.
		assertNotNull(executeOn, controller.getSimpleName() + " must declare an execution boundary");
		assertEquals(TaskExecutors.BLOCKING, executeOn.value());
	}
}
