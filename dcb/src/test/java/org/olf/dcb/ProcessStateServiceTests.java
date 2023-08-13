package org.olf.dcb;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.model.ProcessState;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class ProcessStateServiceTests {

	@Inject
	ProcessStateService processStateService;

	@BeforeEach
	void beforeEach() {
	}

	@Test
	void processStateLifecycle() {
		Map<String, Object> test_state = new HashMap<String,Object>();
                test_state.put("key1","value1");

                UUID test_context = UUID.randomUUID();
                processStateService.updateState(test_context, "testProcess", test_state).block();

                ProcessState ps = processStateService.getState(test_context, "testProcess").block();

                assert(ps.getProcessState().get("key1").equals("value1"));

		// The getStateMap version is in preparation for a Hazelcast cache layer
		Map<String, Object> pure_map = processStateService.getStateMap(test_context, "testProcess").block();
		assert(pure_map.get("key1").equals("value1"));
	}
}
