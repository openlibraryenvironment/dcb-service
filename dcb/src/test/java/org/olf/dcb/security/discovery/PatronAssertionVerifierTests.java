package org.olf.dcb.security.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.security.discovery.PatronAssertionVerifier.InvalidPatronAssertionException;

import com.k_int.peerauth.service.TrustedPeerJwksResolver;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.micronaut.http.HttpRequest;

/**
 * The patron assertion is the mechanism that keeps per-patron ownership enforcement
 * inside DCB rather than delegating it to whichever discovery service is calling.
 * If any of these checks can be bypassed, a discovery service can act as any patron
 * in the consortium, so each one gets a test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatronAssertionVerifierTests {

	private static final String ISSUER = "https://discovery.test.invalid";
	private static final String SERVICE_ID = "a-discovery-service";
	private static final String AUDIENCE = "dcb";

	private static RSAKey signingKey;
	private static RSAKey otherKey;

	@BeforeAll
	static void generateKeys() throws Exception {
		// Deliberately the SAME kid on both: anAssertionSignedByTheWrongKeyIsRejected
		// proves verification turns on the key, not on a matching key id.
		signingKey = new RSAKeyGenerator(2048).keyID("discovery-1").generate();
		otherKey = new RSAKeyGenerator(2048).keyID("discovery-1").generate();
	}

	// ---- subject under test, wired the way the bean container wires it ----

	private static PatronAssertionVerifier verifier(Clock clock) {
		return verifier(clock, properties(true, Duration.ofMinutes(5)));
	}

	private static PatronAssertionVerifier verifier(Clock clock, DiscoveryServiceProperties props) {
		return new PatronAssertionVerifier(props, new DiscoveryTrustedServiceStore(props),
			new TrustedPeerJwksResolver(), clock);
	}

	private static DiscoveryServiceProperties properties(boolean enabled, Duration maxLifetime) {
		final var service = new DiscoveryServiceProperties.TrustedService();
		service.setServiceId(SERVICE_ID);
		service.setIssuer(ISSUER);
		// Inline JWKS: TrustedPeerJwksResolver prefers it over jwksUri, so no HTTP.
		service.setJwks(Map.of("keys", List.of(signingKey.toPublicJWK().toJSONObject())));

		final var props = new DiscoveryServiceProperties();
		props.setEnabled(enabled);
		props.setAudience(AUDIENCE);
		props.setMaxAssertionLifetime(maxLifetime);
		props.setTrustedServices(List.of(service));

		return props;
	}

	// ---- assertion minting, i.e. what a discovery service does ----

	private static String assertion(AssertionSpec spec) throws Exception {
		final var claims = new JWTClaimsSet.Builder()
			.issuer(spec.issuer)
			.subject(spec.subject)
			.audience(spec.audience)
			.issueTime(Date.from(spec.issuedAt))
			.expirationTime(Date.from(spec.issuedAt.plus(spec.lifetime)));

		if (spec.systemCode != null) {
			claims.claim("localSystemCode", spec.systemCode);
		}
		if (spec.patronId != null) {
			claims.claim("localSystemPatronId", spec.patronId);
		}

		final var jwt = new SignedJWT(
			new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(spec.key.getKeyID()).build(),
			claims.build());

		jwt.sign(new RSASSASigner(spec.key));

		return jwt.serialize();
	}

	private static final class AssertionSpec {
		String issuer = ISSUER;
		String subject = SERVICE_ID;
		String audience = AUDIENCE;
		String systemCode = "home-lms";
		String patronId = "patron-1";
		Instant issuedAt = Instant.parse("2026-08-12T10:00:00Z");
		Duration lifetime = Duration.ofMinutes(2);
		RSAKey key = signingKey;
	}

	private static AssertionSpec spec() {
		return new AssertionSpec();
	}

	private static Clock clockAt(String instant) {
		return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
	}

	private static HttpRequest<?> requestWith(String assertion) {
		final var request = HttpRequest.GET("/discovery/requests");
		return assertion == null
			? request
			: request.header(PatronAssertionVerifier.ASSERTION_HEADER, assertion);
	}

	private static InvalidPatronAssertionException rejects(Clock clock, String token) {
		return assertThrows(InvalidPatronAssertionException.class,
			() -> verifier(clock).verify(requestWith(token)));
	}

	// ---- the happy path ----

	@Test
	void aWellFormedAssertionFromATrustedServiceYieldsTheAssertedPatron() throws Exception {
		final var patron = verifier(clockAt("2026-08-12T10:00:30Z"))
			.verify(requestWith(assertion(spec())));

		assertEquals("home-lms", patron.systemCode());
		assertEquals("patron-1", patron.patronId());
		// Recorded so the audit trail can say which service vouched for this patron.
		assertEquals(SERVICE_ID, patron.assertingService());
	}

	// ---- each check that stands between a caller and impersonating any patron ----

	@Test
	void anAssertionSignedByTheWrongKeyIsRejected() throws Exception {
		final var forged = spec();
		forged.key = otherKey; // same kid, different private key

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(forged));
	}

	@Test
	void anAssertionFromAnUnknownIssuerIsRejected() throws Exception {
		final var untrusted = spec();
		untrusted.issuer = "https://not-onboarded.test.invalid";

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(untrusted));
	}

	@Test
	void anAssertionForAnotherAudienceIsRejected() throws Exception {
		// A correctly-signed assertion meant for a different DCB must not work here.
		final var wrongAudience = spec();
		wrongAudience.audience = "some-other-dcb";

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(wrongAudience));
	}

	@Test
	void anAssertionWhoseSubjectIsNotTheServiceIsRejected() throws Exception {
		// The subject identifies the SERVICE. A caller putting the patron there is
		// trying to get DCB to trust an unbounded, caller-chosen value.
		final var patronAsSubject = spec();
		patronAsSubject.subject = "patron-1";

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(patronAsSubject));
	}

	@Test
	void anExpiredAssertionIsRejected() throws Exception {
		rejects(clockAt("2026-08-12T10:05:00Z"), assertion(spec()));
	}

	@Test
	void anAssertionLivingLongerThanDcbAllowsIsRejected() throws Exception {
		// Valid signature, valid audience, unexpired — but a 12-hour patron assertion
		// is a bearer token, and its replay window is DCB's to bound, not the issuer's.
		final var longLived = spec();
		longLived.lifetime = Duration.ofHours(12);

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(longLived));
	}

	@Test
	void anAssertionIssuedInTheFutureIsRejected() throws Exception {
		final var future = spec();
		future.issuedAt = Instant.parse("2026-08-12T12:00:00Z");

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(future));
	}

	@Test
	void anAssertionWithoutPatronClaimsIsRejected() throws Exception {
		final var noPatron = spec();
		noPatron.systemCode = null;
		noPatron.patronId = null;

		rejects(clockAt("2026-08-12T10:00:30Z"), assertion(noPatron));
	}

	@Test
	void aMissingHeaderIsRejected() {
		rejects(clockAt("2026-08-12T10:00:30Z"), null);
	}

	@Test
	void garbageIsRejected() {
		rejects(clockAt("2026-08-12T10:00:30Z"), "not-a-jwt");
	}

	/** A deployment not configured for discovery must fail closed, not open. */
	@Test
	void everyAssertionIsRejectedWhenDiscoveryIsDisabled() throws Exception {
		final var disabled = properties(false, Duration.ofMinutes(5));

		assertThrows(InvalidPatronAssertionException.class,
			() -> verifier(clockAt("2026-08-12T10:00:30Z"), disabled)
				.verify(requestWith(assertion(spec()))));
	}

	/** Two services sharing an issuer means either can mint the other's assertions. */
	@Test
	void aDuplicateIssuerRefusesToStart() {
		final var first = new DiscoveryServiceProperties.TrustedService();
		first.setServiceId(SERVICE_ID);
		first.setIssuer(ISSUER);
		first.setJwks(Map.of("keys", List.of()));

		final var second = new DiscoveryServiceProperties.TrustedService();
		second.setServiceId("impostor");
		second.setIssuer(ISSUER);
		second.setJwks(Map.of("keys", List.of()));

		final var props = new DiscoveryServiceProperties();
		props.setTrustedServices(List.of(first, second));

		assertThrows(IllegalStateException.class, () -> new DiscoveryTrustedServiceStore(props));
	}
}
