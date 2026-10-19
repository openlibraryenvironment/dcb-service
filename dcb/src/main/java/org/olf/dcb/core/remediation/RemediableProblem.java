package org.olf.dcb.core.remediation;

import java.util.Map;

/**
 * A failure that names its catalogue entry. Whatever ends a request in ERROR with one of these
 * gets the entry's summary as its error message and the entry's next steps in its audit.
 */
public interface RemediableProblem {
	RemediationCode remediationCode();

	/** Values for the entry's placeholders. Never personal data: they are shown to staff and kept in the audit. */
	Map<String, String> remediationValues();
}
