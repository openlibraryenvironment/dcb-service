package org.olf.dcb.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a deliberate decision that EVERY authenticated principal may call this route —
 * including service credentials held by discovery services and any other client the
 * Keycloak realm will ever authenticate.
 *
 * {@code @Secured(IS_AUTHENTICATED)} is not a permission. It is a claim about the whole
 * realm, and that claim is maintained in Keycloak configuration by people who do not
 * read this codebase. It grew false the moment low-trust credentials joined the realm:
 * PatronRequestController defaulted to it, which is how a patron's browser token came to
 * reach request placement, expedited checkout and state-machine rollback.
 *
 * So the annotation is not banned — it is made visible. A route carrying this marker is a
 * decision somebody made and a reviewer can see in a diff, rather than a default nobody
 * noticed. {@link org.olf.dcb.security.ApiSecurityArchitectureTests} fails the build when
 * a route is open to all principals without one.
 *
 * ONLY valid where the route confers no authority and returns no tenant-scoped data. If
 * it mutates anything, this is the wrong tool and the arch test will reject it outright —
 * name the roles instead. If two callers could legitimately get different answers, it is
 * also the wrong tool.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface OpenToAllPrincipals {

	/**
	 * Why every principal in the realm may call this. Reviewed on every change, so
	 * write the reason, not a restatement of the annotation name.
	 */
	String justification();
}
