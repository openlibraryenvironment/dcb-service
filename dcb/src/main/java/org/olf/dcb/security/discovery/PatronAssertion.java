package org.olf.dcb.security.discovery;

/**
 * A patron identity DCB has VERIFIED, not one it was told.
 *
 * The only way to obtain one is through {@link PatronAssertionVerifier}, so a
 * controller holding an instance of this knows the signature, issuer, audience,
 * expiry and lifetime bound were all checked. That is the point of the type: it
 * makes "I checked" and "I did not check" different things the compiler can see.
 *
 * @param systemCode      the patron's home Host LMS code (claim: localSystemCode)
 * @param patronId        the patron's local id in that system (claim: localSystemPatronId)
 * @param assertingService the discovery service that vouched for this patron, for audit
 */
public record PatronAssertion(String systemCode, String patronId, String assertingService) {
}
