package org.olf.dcb.security.provisioning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.olf.dcb.security.provisioning.keycloak.KeycloakConfig;
import org.olf.dcb.security.provisioning.keycloak.KeycloakIdentityProviderClient;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest(propertySources = "keycloak-config.yml", startApplication = false, contextBuilder = org.olf.dcb.test.DcbTestContainerContextBuilder.class)
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakConfigTests {
	
	@Inject
	BeanContext context;

	@Test
    @Order(1)
	@DisplayName("A complete configuration builds and the beans are available")
	void aCompleteConfigurationBuilds() {
		assertDoesNotThrow(() -> {
			context.createBean(KeycloakConfig.class);
			context.createBean(KeycloakIdentityProviderClient.class);
		});
	}
	
	@Test
    @Order(2)
	@Property(name = IdentityProviderConfig.CONFIG_ROOT + ".keycloak.client-secret", value = "")
	@DisplayName("A blank client secret fails validation")
	void aBlankSecretFailsImmediately() {
		assertThrows(BeanInstantiationException.class, () -> 
			context.createBean(KeycloakConfig.class));
	}

	@Test
    @Order(3)
	@Property(name = IdentityProviderConfig.CONFIG_ROOT + ".keycloak.client-id", value = "")
	@DisplayName("A missing client id fails validation")
	void aMissingClientIdFailsImmediately() {
		assertThrows(BeanInstantiationException.class, () ->
			context.createBean(KeycloakConfig.class));
	}
}
