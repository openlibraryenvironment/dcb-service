package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.StateModelController;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

class StateModelControllerExecutionBoundaryTests {
	@Test
	void stateModelRenderingDispatchesGraphvizWork() {
		final var executeOn = StateModelController.class.getAnnotation(ExecuteOn.class);
		assertNotNull(executeOn, "StateModelController must declare an execution boundary");
		assertEquals(TaskExecutors.BLOCKING, executeOn.value());
	}
}
