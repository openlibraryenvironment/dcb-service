package org.olf.dcb.core.interaction.foundation;

import java.net.URI;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.uri.UriBuilder;

/**
 * An NCIP responder's address, and the token a responder that authenticates by path segment
 * needs (ByWater's Koha NCIP plugin: {@code .../ncip/{token}}). The token is a credential held
 * apart from the address and joined to it only to send, so neither config nor logs carry it.
 */
public record NcipEndpoint(String url, @Nullable String token) {
	URI requestUri() {
		final var uri = UriBuilder.of(url);

		return (token == null ? uri : uri.path(token)).build();
	}

	@Override
	public String toString() {
		return "NcipEndpoint[url=" + url + ", token=" + (token == null ? "none" : "[redacted]") + "]";
	}
}
