package org.olf.dcb.observability;

import org.slf4j.MDC;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;

/**
 * Puts the request id in the MDC of whichever thread the request is running on; Micronaut
 * re-applies it as the propagated context moves between threads.
 */
record MdcRequestId(String id) implements ThreadPropagatedContextElement<String> {

	@Override
	public String updateThreadContext() {
		final var previous = MDC.get(RequestId.MDC_KEY);
		MDC.put(RequestId.MDC_KEY, id);
		return previous;
	}

	@Override
	public void restoreThreadContext(String previous) {
		if (previous == null) {
			MDC.remove(RequestId.MDC_KEY);
		} else {
			MDC.put(RequestId.MDC_KEY, previous);
		}
	}
}
