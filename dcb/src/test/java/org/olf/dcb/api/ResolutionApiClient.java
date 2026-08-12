package org.olf.dcb.api;

import org.olf.dcb.core.api.ResolutionPreview;
import org.olf.dcb.request.resolution.ResolutionParameters;

import java.util.List;

import org.olf.dcb.security.RoleNames;
import org.olf.dcb.security.TestStaticTokenValidator;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;

@Singleton
public class ResolutionApiClient {
	// Resolution preview is staff-only: it runs live availability against member LMS
	// APIs and returns consortium-wide item detail. It used to be anonymous.
	private static final String ACCESS_TOKEN = "test-resolution-client-token";

	private final HttpClient httpClient;

	public ResolutionApiClient(@Client("/patrons/requests/resolution") HttpClient httpClient) {
		this.httpClient = httpClient;

		TestStaticTokenValidator.add(ACCESS_TOKEN, "test-resolution-client",
			List.of(RoleNames.ADMINISTRATOR));
	}

	public ResolutionPreview previewResolution(ResolutionParameters parameters) {
		final var uri = UriBuilder.of("/preview").build();

		return httpClient.toBlocking()
			.retrieve(HttpRequest.POST(uri, parameters).bearerAuth(ACCESS_TOKEN),
				ResolutionPreview.class);
	}
}
