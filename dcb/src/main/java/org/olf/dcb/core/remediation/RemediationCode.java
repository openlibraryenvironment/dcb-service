package org.olf.dcb.core.remediation;

/**
 * Every failure DCB can name and say what to do about. Each constant has exactly one entry in
 * {@code remediation/catalogue.json}, or the application does not start.
 */
public enum RemediationCode {
	FOLIO_REQUEST_POLICY_REFUSED,
	SIERRA_HOLD_REFUSED_FOR_PATRON_RECORD,
	ALMA_NO_REQUEST_RULE_FOR_USER_GROUP
}
