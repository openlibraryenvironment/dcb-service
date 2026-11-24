package org.olf.dcb.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import com.hazelcast.core.HazelcastInstance;

import jakarta.inject.Inject;

@DcbTest
class TestHazelcastConfigTests {
	@Inject
	private HazelcastInstance hazelcastInstance;

	@Test
	void aTestContextStartsHazelcastWithoutDiscoveryWhereverItRuns() {
		final var join = hazelcastInstance.getConfig().getNetworkConfig().getJoin();

		assertThat("cluster name", hazelcastInstance.getConfig().getClusterName(), is("dcb-service-test-cluster"));
		assertThat("Kubernetes discovery", join.getKubernetesConfig().isEnabled(), is(false));
	}
}
