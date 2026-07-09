package org.olf.dcb.request.lifecycle.ncip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.StrategyType;

/**
 * PR-6b: the NCIP declarative borrowing strategy self-identifies as the
 * DECLARATIVE handler for protocol ncip-v202. Combined with the resolver logic
 * (PR-1/PR-2) and the DI-health proof from the borrowing integration test, this
 * closes the loop: a host configured {strategy: declarative, protocol: ncip-v202}
 * resolves this strategy.
 *
 * type() and supportsProtocol() do not touch the injected collaborators, so the
 * contract is asserted without standing up the full dependency graph.
 */
class NcipBorrowingRequestStrategyContractTests {
	private final NcipBorrowingRequestStrategy strategy =
		new NcipBorrowingRequestStrategy(null, null, null, null, null, null);

	@Test
	void identifiesAsDeclarative() {
		assertEquals(StrategyType.DECLARATIVE, strategy.type());
	}

	@Test
	void supportsOnlyNcipV202() {
		assertTrue(strategy.supportsProtocol("ncip-v202"));
		assertFalse(strategy.supportsProtocol("iso18626"));
		assertFalse(strategy.supportsProtocol(null));
	}
}
