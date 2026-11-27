package org.olf.dcb.core.patroncheck;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

/** The whole answer to a patron check: nothing from the patron's record is in it. */
@Serdeable
public record PatronCheckResult(Outcome outcome, List<PatronCheckReason> reasons) {

	public enum Outcome {
		ELIGIBLE,
		REFUSED
	}

	public PatronCheckResult {
		reasons = List.copyOf(reasons);
	}

	static PatronCheckResult eligible() {
		return new PatronCheckResult(Outcome.ELIGIBLE, List.of());
	}

	static PatronCheckResult refused(List<PatronCheckReason> reasons) {
		return new PatronCheckResult(Outcome.REFUSED, reasons);
	}
}
