package org.olf.dcb.core.interaction;

/** Agency auth profiles whose meaning does not depend on the Host LMS. */
public final class AuthProfiles {
	/**
	 * The library's patrons sign in at its own identity provider, through the discovery front end,
	 * so DCB never sees or checks their credentials. A discovery front end then finds the patron by
	 * lookup or resolve, neither of which reads the profile.
	 */
	public static final String SAML = "saml";

	private AuthProfiles() {
	}

	public static boolean signsInAtInstitution(String authProfile) {
		return SAML.equalsIgnoreCase(authProfile);
	}
}
