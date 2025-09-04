package org.olf.dcb.api;

import org.olf.dcb.core.api.ResolutionPreview;
import org.olf.dcb.request.resolution.ResolutionParameters;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;

@Singleton
public class ResolutionApiClient {
	private final HttpClient httpClient;

	public ResolutionApiClient(@Client("/patrons/requests/resolution") HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public ResolutionPreview previewResolution(ResolutionParameters parameters) {
		final var uri = UriBuilder.of("/preview").build();

		return httpClient.toBlocking()
			.retrieve(HttpRequest.POST(uri, parameters), ResolutionPreview.class);
	}
}
