package org.olf.dcb.core.interaction.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;

/**
 * PR-5: unified host-scoped capability schema, nested Foundation (imperative) scope.
 */
class ImperativeCapabilityConfigTests {

	private static HostLms host(Map<String, Object> clientConfig) {
		final var lms = mock(HostLms.class);
		when(lms.getCode()).thenReturn("TEST");
		when(lms.getClientConfig()).thenReturn(clientConfig);
		return lms;
	}

	@Test
	void readsSettingFromNestedImperativeBlock() {
		final var lms = host(Map.of("capabilities",
			Map.of("imperative", Map.of("base-protocol", "SIP2"))));

		assertEquals("SIP2", ImperativeCapabilityConfig.setting(lms, "base-protocol"));
	}

	@Test
	void fallsBackToTopLevelForPreUnificationConfig() {
		final var lms = host(Map.of("base-protocol", "SIP2"));

		assertEquals("SIP2", ImperativeCapabilityConfig.setting(lms, "base-protocol"));
	}

	@Test
	void nestedScopeWinsOverTopLevel() {
		final var lms = host(Map.of(
			"base-protocol", "NCIP",
			"capabilities", Map.of("imperative", Map.of("base-protocol", "SIP2"))));

		assertEquals("SIP2", ImperativeCapabilityConfig.setting(lms, "base-protocol"));
	}

	@Test
	void missingSettingIsNull() {
		assertNull(ImperativeCapabilityConfig.setting(host(Map.of()), "base-protocol"));
	}

	@Test
	void foundationClientSelectsBaseProtocolFromNestedScope() {
		final var lms = host(Map.of("capabilities",
			Map.of("imperative", Map.of("base-protocol", "SIP2"))));

		final var beanContext = mock(BeanContext.class);
		when(beanContext.createBean(eq(Sip2Adaptor.class), eq(lms)))
			.thenReturn(mock(Sip2Adaptor.class));

		new FoundationClient(lms, beanContext);

		verify(beanContext).createBean(Sip2Adaptor.class, lms);
	}
}
