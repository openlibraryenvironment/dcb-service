package org.olf.dcb.core.patroncheck;

import java.util.Map;

/**
 * Why a patron would be refused, as library staff are told it: a fixed vocabulary with no
 * detail from the patron's record.
 */
public enum PatronCheckReason {
	BLOCKED,
	EXPIRED,
	INACTIVE,
	OVER_LOAN_LIMIT,
	OVER_HOLD_LIMIT,
	UNKNOWN_PATRON_TYPE,
	INELIGIBLE_PATRON_TYPE,
	NO_BARCODE,
	LIBRARY_NOT_BORROWING,
	NO_HOME_LIBRARY_MAPPING,
	LIMITS_UNCHECKED,
	NOT_FOUND,
	/** A failure code this vocabulary has no name for yet: refused, but not explained. */
	UNCLASSIFIED;

	private static final Map<String, PatronCheckReason> BY_FAILURE_CODE = Map.ofEntries(
		Map.entry("PATRON_BLOCKED", BLOCKED),
		Map.entry("PATRON_EXPIRED", EXPIRED),
		Map.entry("PATRON_INACTIVE", INACTIVE),
		Map.entry("PATRON_DELETED", NOT_FOUND),
		Map.entry("PATRON_NOT_FOUND", NOT_FOUND),
		Map.entry("EXCEEDS_AGENCY_LIMIT", OVER_LOAN_LIMIT),
		Map.entry("PATRON_HOLD_LIMIT_REACHED", OVER_HOLD_LIMIT),
		Map.entry("PATRON_TYPE_NOT_MAPPED", UNKNOWN_PATRON_TYPE),
		Map.entry("LOCAL_PATRON_TYPE_IS_NON_NUMERIC", UNKNOWN_PATRON_TYPE),
		Map.entry("PATRON_INELIGIBLE", INELIGIBLE_PATRON_TYPE),
		Map.entry("INVALID_PATRON_BARCODE", NO_BARCODE),
		Map.entry("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING", LIBRARY_NOT_BORROWING),
		Map.entry("PATRON_NOT_ASSOCIATED_WITH_AGENCY", NO_HOME_LIBRARY_MAPPING),
		Map.entry("REQUEST_LIMITS_UNCHECKED", LIMITS_UNCHECKED));

	public static PatronCheckReason fromFailureCode(String failureCode) {
		return BY_FAILURE_CODE.getOrDefault(failureCode, UNCLASSIFIED);
	}
}
