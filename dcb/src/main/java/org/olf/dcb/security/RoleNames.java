package org.olf.dcb.security;

public interface RoleNames {
	public static final String ADMINISTRATOR = "ADMIN";
	public static final String INTERNAL_API = "INTERNAL_API";
	public static final String CONSORTIUM_ADMIN = "CONSORTIUM_ADMIN";
	public static final String LIBRARY_ADMIN = "LIBRARY_ADMIN";
	public static final String LIBRARY_READ_ONLY = "LIBRARY_READ_ONLY";
	public static final String INTEROP_TESTER = "INTEROP_TESTER";
	// Self-service role for patrons authenticated via discovery (wayfinder).
	// Grants access ONLY to endpoints whose identity derivation is self-scoped
	// to the caller's own JWT claims (localSystemCode / localSystemPatronId).
	public static final String PATRON = "PATRON";
}
