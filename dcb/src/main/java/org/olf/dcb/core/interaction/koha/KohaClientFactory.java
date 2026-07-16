package org.olf.dcb.core.interaction.koha;

import org.olf.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;

@Singleton
public class KohaClientFactory {

	private final HttpClient client;
	private final BeanContext context;

	public KohaClientFactory(HttpClient client, BeanContext context) {
		this.client = client;
		this.context = context;
	}

	public KohaApiClient createClientFor(final HostLms hostLms) {
		return context.createBean(KohaApiClientImpl.class, hostLms, client);
	}
}
