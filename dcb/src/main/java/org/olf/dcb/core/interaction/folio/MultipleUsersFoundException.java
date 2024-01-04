package org.olf.dcb.core.interaction.folio;

public class MultipleUsersFoundException extends RuntimeException {
	public MultipleUsersFoundException(String hostLmsCode, CqlQuery query) {
		super(
			"Multiple users found in Host LMS: \"" + hostLmsCode + "\" for query: \"" + query + "\"");
	}
}
