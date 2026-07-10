package org.olf.dcb.request.lifecycle.ncip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.StrategyType;

/**
 * The NCIP declarative supplying strategy self-identifies as the DECLARATIVE
 * handler for protocol ncip-v202 - the keys the SupplyingAgencyRequestStrategyResolver
 * selects on. Mirrors the borrowing contract test.
 */
class NcipSupplyingRequestStrategyContractTests {
	private final NcipSupplyingRequestStrategy strategy =
		new NcipSupplyingRequestStrategy(null, null, null, null, null);

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
