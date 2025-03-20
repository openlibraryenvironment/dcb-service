package org.olf.dcb.core.interaction.alma;

import org.olf.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;
import services.k_int.interaction.alma.AlmaApiClient;

@Singleton
public class AlmaClientFactory {

	private final HttpClient client;
	private final BeanContext context;

	public AlmaClientFactory(HttpClient client, BeanContext context) {
		this.client = client;
		this.context = context;
	}

	public AlmaApiClient createClientFor(final HostLms hostLms) {
		return context.createBean(AlmaApiClientImpl.class, hostLms, client);
	}
}
