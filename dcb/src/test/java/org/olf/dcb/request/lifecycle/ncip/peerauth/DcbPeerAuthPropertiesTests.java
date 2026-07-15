package org.olf.dcb.request.lifecycle.ncip.peerauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

@MicronautTest(
	transactional = false,
	propertySources = "classpath:peer-auth-properties.yml",
	contextBuilder = DcbTestContainerContextBuilder.class)
class DcbPeerAuthPropertiesTests {
	@Inject
	DcbPeerAuthProperties properties;

	@Test
	void bindsNestedNcipAndLocalIdentityConfiguration() {
		assertTrue(properties.isNcipEnabled());
		assertEquals("https://dcb.example/peer-auth", properties.getLocalIdentity().getIssuer());
		assertEquals("key-1", properties.getLocalIdentity().getKeyId());
		assertEquals(Duration.ofMinutes(5), properties.getLocalIdentity().getTokenLifetime());
	}
}
