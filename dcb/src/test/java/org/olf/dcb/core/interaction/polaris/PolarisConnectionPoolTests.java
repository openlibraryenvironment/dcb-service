package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.client.DefaultHttpClientConfiguration;
import jakarta.inject.Inject;

@DcbTest
class PolarisConnectionPoolTests {
	@Inject
	private PolarisClientConfig polarisClientConfig;
	@Inject
	private DefaultHttpClientConfiguration sharedClientConfig;

	@Test
	void polarisConnectionsCarryFewerStreamsThanPolarisAllows() {
		final var shared = sharedClientConfig.getConnectionPoolConfiguration();
		final var polaris = polarisClientConfig.getConnectionPoolConfiguration();

		// The shared pool allows more than Polaris accepts, so the cap below is doing the work
		assertThat(shared.getMaxConcurrentRequestsPerHttp2Connection(), is(greaterThan(100)));

		assertThat(polaris.getMaxConcurrentRequestsPerHttp2Connection(), is(lessThanOrEqualTo(100)));
		assertThat(polaris.isEnabled(), is(shared.isEnabled()));
	}
}
