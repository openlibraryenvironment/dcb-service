package org.olf.dcb.core.interaction.folio;

public class MultipleUsersFoundException extends RuntimeException {
	MultipleUsersFoundException(CqlQuery query, String hostLmsCode) {
		super("Multiple users found in Host LMS: \"" + hostLmsCode + "\" for query: \"" + query + "\"");
	}
}
