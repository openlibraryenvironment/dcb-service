package org.olf.dcb.core.remediation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A catalogue entry with its placeholders filled in for one failure. */
public record Remediation(RemediationCode code, Audience audience, String summary,
	List<String> nextSteps, String docs) {

	/** Whose staff can act on it. */
	public enum Audience {
		SUPPLYING_LIBRARY,
		BORROWING_LIBRARY,
		PICKUP_LIBRARY,
		CONSORTIUM,
		OPERATOR
	}

	public Map<String, Object> toAuditData() {
		final var data = new LinkedHashMap<String, Object>();
		data.put("code", code.name());
		data.put("audience", audience.name());
		data.put("summary", summary);
		data.put("nextSteps", nextSteps);
		data.put("docs", docs);
		return data;
	}
}
