package org.olf.dcb.request.lifecycle.evidence;

import io.micronaut.context.annotation.Prototype;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Prototype
public class LifecycleEvidenceIdempotencyGuard {
	private final Set<String> seenEvidenceKeys = ConcurrentHashMap.newKeySet();

	public boolean firstSeen(LifecycleEvidence evidence) {
		return seenEvidenceKeys.add(keyFor(evidence));
	}

	private static String keyFor(LifecycleEvidence evidence) {
		return String.join("|",
			Objects.toString(evidence.source(), ""),
			Objects.toString(evidence.protocol(), ""),
			Objects.toString(evidence.role(), ""),
			Objects.toString(evidence.resource(), ""),
			Objects.toString(evidence.correlationId(), ""),
			Objects.toString(evidence.status(), ""),
			Objects.toString(evidence.messageTimestamp(), ""),
			Objects.toString(evidence.rawMessageReference(), ""));
	}
}

