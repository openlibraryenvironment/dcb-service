package org.olf.dcb.security.discovery;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerAuthException;
import com.k_int.peerauth.PeerPrincipal;
import com.k_int.peerauth.service.NimbusPeerTokenVerifier;
import com.k_int.peerauth.service.PeerJwksResolver;
import com.nimbusds.jwt.SignedJWT;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns the {@code X-OpenRS-Patron-Assertion} header into a
 * {@link PatronAssertion} DCB has actually verified.
 *
 * WHY THIS EXISTS. A discovery service's own credential says only "this discovery
 * service is calling". If DCB accepted the patron identity as a plain parameter alongside it,
 * DCB would hold the authority while the caller decided how to spend it — the
 * confused-deputy problem — and per-patron ownership would stop being enforceable
 * here. Instead the caller signs a short-lived assertion with its own key, and DCB
 * verifies it: signature, trusted issuer, audience, expiry, and a DCB-chosen cap on
 * lifetime. Ownership then keys off a claim DCB validated.
 *
 * Mechanism deliberately reused from ki-mn-peer-auth, exactly as
 * {@link org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthGuard} does for
 * NCIP peers. A second hand-rolled JWT verifier in this codebase would be a bug
 * with a long fuse.
 */
@Slf4j
@Singleton
public class PatronAssertionVerifier {

	public static final String ASSERTION_HEADER = "X-OpenRS-Patron-Assertion";

	private static final String CLAIM_SYSTEM_CODE = "localSystemCode";
	private static final String CLAIM_PATRON_ID = "localSystemPatronId";

	private final DiscoveryServiceProperties properties;
	private final NimbusPeerTokenVerifier verifier;
	private final Clock clock;

	public PatronAssertionVerifier(DiscoveryServiceProperties properties,
		DiscoveryTrustedServiceStore trustedServiceStore, PeerJwksResolver jwksResolver) {

		this(properties, trustedServiceStore, jwksResolver, Clock.systemUTC());
	}

	PatronAssertionVerifier(DiscoveryServiceProperties properties,
		DiscoveryTrustedServiceStore trustedServiceStore, PeerJwksResolver jwksResolver,
		Clock clock) {

		this.properties = properties;
		this.clock = clock;
		this.verifier = new NimbusPeerTokenVerifier(trustedServiceStore, jwksResolver, clock);
	}

	/**
	 * @throws InvalidPatronAssertionException if the header is absent, malformed,
	 *         untrusted, expired, over-long-lived, or carries no patron identity.
	 *         Deliberately one exception type: a caller learns that its assertion
	 *         was not accepted, not which check it tripped.
	 */
	public PatronAssertion verify(HttpRequest<?> request) {
		if (!properties.isEnabled()) {
			throw new InvalidPatronAssertionException(
				"Discovery patron assertions are not enabled on this DCB");
		}

		final var header = request.getHeaders().get(ASSERTION_HEADER);

		if (header == null || header.isBlank()) {
			throw new InvalidPatronAssertionException("Missing " + ASSERTION_HEADER + " header");
		}

		final PeerPrincipal principal;

		try {
			principal = verifier.verify(
				PeerAuthContext.singleTenant(properties.getAudience()), header.trim());
		} catch (PeerAuthException e) {
			log.warn("Rejected patron assertion: {}", e.getMessage());
			throw new InvalidPatronAssertionException("Patron assertion was not accepted", e);
		}

		enforceLifetimeBound(header.trim(), principal);

		final var systemCode = claim(principal, CLAIM_SYSTEM_CODE);
		final var patronId = claim(principal, CLAIM_PATRON_ID);

		log.debug("Accepted patron assertion from {} for patron {}@{}",
			principal.peerId(), patronId, systemCode);

		return new PatronAssertion(systemCode, patronId, principal.peerId());
	}

	/**
	 * The library checks that `exp` is in the future. It cannot know how far in the
	 * future DCB is willing to tolerate — an assertion valid for a day is a bearer
	 * token by another name, and its replay window is ours to bound, not the
	 * issuer's.
	 */
	private void enforceLifetimeBound(String token, PeerPrincipal principal) {
		try {
			final var claims = SignedJWT.parse(token).getJWTClaimsSet();
			final Date issuedAt = claims.getIssueTime();
			final Date expiresAt = claims.getExpirationTime();

			if (issuedAt == null) {
				throw new InvalidPatronAssertionException("Patron assertion has no iat claim");
			}

			// expiresAt is non-null: NimbusPeerTokenVerifier.verifyTimes already rejected
			// an assertion without one before we got here.
			final var lifetime = Duration.between(issuedAt.toInstant(), expiresAt.toInstant());

			if (lifetime.compareTo(properties.getMaxAssertionLifetime()) > 0) {
				log.warn("Rejected patron assertion from {}: lifetime {} exceeds the {} maximum",
					principal.peerId(), lifetime, properties.getMaxAssertionLifetime());

				throw new InvalidPatronAssertionException("Patron assertion lifetime is too long");
			}

			// Tolerate no meaningful clock skew forwards: an assertion issued in the
			// future is either a broken clock or a manufactured replay window.
			if (issuedAt.toInstant().isAfter(clock.instant().plusSeconds(60))) {
				throw new InvalidPatronAssertionException("Patron assertion iat is in the future");
			}
		} catch (InvalidPatronAssertionException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidPatronAssertionException("Patron assertion could not be read", e);
		}
	}

	private static String claim(PeerPrincipal principal, String name) {
		final var value = principal.claims().get(name);

		if (!(value instanceof String text) || text.isBlank()) {
			throw new InvalidPatronAssertionException(
				"Patron assertion is missing the " + name + " claim");
		}

		return text;
	}

	public static class InvalidPatronAssertionException extends RuntimeException {
		public InvalidPatronAssertionException(String message) {
			super(message);
		}

		public InvalidPatronAssertionException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
