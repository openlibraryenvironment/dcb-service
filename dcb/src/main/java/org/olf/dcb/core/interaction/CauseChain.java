package org.olf.dcb.core.interaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class CauseChain {
	private CauseChain() {
	}

	/** The error and its causes, outermost first, each once: a cycle in the chain ends the walk. */
	static List<Throwable> of(Throwable error) {
		final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final List<Throwable> chain = new ArrayList<>();

		for (var current = error; current != null && seen.add(current); current = current.getCause()) {
			chain.add(current);
		}

		return chain;
	}
}
