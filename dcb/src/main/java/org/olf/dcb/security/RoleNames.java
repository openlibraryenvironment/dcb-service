package org.olf.dcb.security;

public interface RoleNames {
	public static final String ADMINISTRATOR = "ADMIN";
	public static final String INTERNAL_API = "INTERNAL_API";
	public static final String CONSORTIUM_ADMIN = "CONSORTIUM_ADMIN";
	public static final String LIBRARY_ADMIN = "LIBRARY_ADMIN";
	public static final String LIBRARY_READ_ONLY = "LIBRARY_READ_ONLY";
	public static final String INTEROP_TESTER = "INTEROP_TESTER";

	/**
	 * A discovery service BACKEND — wayfinder, or a third party's server-side
	 * integration. Held by a confidential client credential that never reaches a
	 * browser, and never by a patron.
	 *
	 * This role authenticates the CALLER. It does not identify a patron: the patron
	 * travels separately, as a signed assertion the caller mints and DCB verifies
	 * (see {@link org.olf.dcb.security.discovery.PatronAssertionVerifier}). That
	 * split is deliberate — it keeps per-patron ownership enforcement inside DCB
	 * instead of delegating it to the calling service.
	 *
	 * Reachable surface: /discovery/** ONLY. It must never appear in a @Secured on
	 * any other controller. There was previously a PATRON role here, granted to
	 * every federated patron and therefore to every patron's browser; because
	 * PatronRequestController defaulted to IS_AUTHENTICATED, that role also reached
	 * request placement, expedited checkout and state-machine rollback. Do not
	 * reintroduce a patron-held role.
	 */
	public static final String DISCOVERY_SERVICE = "DISCOVERY_SERVICE";
}
