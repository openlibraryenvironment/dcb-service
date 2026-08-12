package org.olf.dcb.request.workflow.exceptions;

/**
 * The agency a patron resolved to does not take part in consortial borrowing.
 * <p>
 * Preflight rejects this before a request is created, but preflight can be turned
 * off and a request can reach validation by other routes. On a shared system the
 * consequence of missing it is placing an interlending request on behalf of a
 * library that is not in the consortium at all.
 */
public class AgencyNotParticipatingInBorrowingException extends RuntimeException {
	public AgencyNotParticipatingInBorrowingException(String agencyCode) {
		super("Agency \"%s\" is not participating in borrowing".formatted(agencyCode));
	}
}
