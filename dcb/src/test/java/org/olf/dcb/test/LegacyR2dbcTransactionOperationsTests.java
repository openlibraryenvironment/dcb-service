package org.olf.dcb.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import jakarta.inject.Inject;

@DcbTest
@Property(name = "dcb.r2dbc.legacy-transaction-operations.enabled", value = "true")
class LegacyR2dbcTransactionOperationsTests {
	@Inject
	private R2dbcReactorTransactionOperations transactionOperations;

	@Test
	void canRestoreTheLegacyTransactionImplementation() {
		assertThat(transactionOperations.getClass().getSimpleName(),
			is("ReplacementR2dbcReactorTransactionOperations"));
	}
}
