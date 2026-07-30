package org.olf.dcb.indexing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SharedIndexConfigurationTests {
	@Test
	void shouldDefaultToOneReplica() {
		assertThat(configuration(Optional.empty()).effectiveNumberOfReplicas(), is(1));
	}

	@Test
	void shouldUseConfiguredReplicaCount() {
		assertThat(configuration(Optional.of(0)).effectiveNumberOfReplicas(), is(0));
	}

	@Test
	void shouldRejectNegativeReplicaCount() {
		assertThrows(IllegalArgumentException.class,
			() -> configuration(Optional.of(-1)).effectiveNumberOfReplicas());
	}

	private static SharedIndexConfiguration configuration(Optional<Integer> replicas) {
		return new SharedIndexConfiguration(
			"shared-index",
			Optional.empty(),
			Optional.empty(),
			Optional.empty(),
			replicas,
			Optional.empty(),
			Optional.empty());
	}
}
