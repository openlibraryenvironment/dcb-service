package org.olf.dcb.security.provisioning.keycloak;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;

@Factory
public class KeycloakClientFactory {
	
	final HttpClient httpClient;
	
	public KeycloakClientFactory(@Client HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@EachBean(KeycloakConfig.class)
	KeycloakIdentityProviderClient getKeyCloakClient(KeycloakConfig conf) {
		return new KeycloakIdentityProviderClient(httpClient, conf);
	}
}
