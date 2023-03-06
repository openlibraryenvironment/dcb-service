package org.olf.reshare.dcb.core.interaction.sierra;

import org.olf.reshare.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;
import services.k_int.interaction.sierra.SierraApiClient;

@Singleton
public class HostLmsSierraApiClientFactory {

	private final HttpClient client;
	private final BeanContext context;

	public HostLmsSierraApiClientFactory(HttpClient client, BeanContext context) {
		this.client = client;
		this.context = context;
	}

	SierraApiClient createClientFor(final HostLms hostLms) {
		return context.createBean(HostLmsSierraApiClient.class, hostLms, client);
	}
}
