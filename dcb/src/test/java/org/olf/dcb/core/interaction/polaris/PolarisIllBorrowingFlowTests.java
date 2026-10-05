package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class PolarisIllBorrowingFlowTests {
	// A value bound from stored config is never the same String instance as a constant
	@Test
	void shouldFollowTheIllFlowWhenTheStoredConfigSaysIll() {
		final var config = PolarisConfig.builder()
			.borrowerLendingFlow(new String("ILL"))
			.build();

		assertThat(PolarisLmsClient.followsIllBorrowingFlow(config), is(true));
	}

	@Test
	void shouldFollowTheStandardFlowOtherwise() {
		assertThat(PolarisLmsClient.followsIllBorrowingFlow(PolarisConfig.builder().build()), is(false));
	}
}
