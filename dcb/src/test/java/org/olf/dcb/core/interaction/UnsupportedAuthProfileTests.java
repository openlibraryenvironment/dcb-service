package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * An agency whose auth profile its Host LMS cannot use is misconfigured. Answering with the
 * empty result a wrong PIN gets would leave every patron there unable to sign in, with
 * nothing to say why.
 */
@DcbTest
class UnsupportedAuthProfileTests {
	private static final String SIERRA = "unsupported-profile-sierra";
	private static final String POLARIS = "unsupported-profile-polaris";
	private static final String BARCODE = "barcode-14142";

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(SIERRA);
		hostLmsFixture.createPolarisHostLms(POLARIS, "staff", "password",
			"https://polaris.example.org", "DOMAIN", "access-id", "access-key");
	}

	@Test
	void shouldRefuseAnUnknownSierraProfileAsAConfigurationError() {
		assertConfigurationError(SIERRA, "BASIC/BARCODE+PASSWORD");
	}

	@Test
	void shouldRefuseAMissingSierraProfileAsAConfigurationError() {
		assertConfigurationError(SIERRA, null);
	}

	@Test
	void shouldRefuseAnUnknownPolarisProfileAsAConfigurationError() {
		assertConfigurationError(POLARIS, "BASIC/BARCODE+NAME");
	}

	@Test
	void shouldRefuseAMissingPolarisProfileAsAConfigurationError() {
		assertConfigurationError(POLARIS, null);
	}

	private void assertConfigurationError(String hostLmsCode, String authProfile) {
		final var client = hostLmsFixture.createClient(hostLmsCode);

		final var error = assertThrows(UnsupportedAuthProfileException.class,
			() -> Mono.from(client.patronAuth(authProfile, BARCODE, "secret")).block());

		assertThat(error.getMessage(), allOf(
			containsString(hostLmsCode),
			containsString(String.valueOf(authProfile)),
			not(containsString(BARCODE))));
	}
}
