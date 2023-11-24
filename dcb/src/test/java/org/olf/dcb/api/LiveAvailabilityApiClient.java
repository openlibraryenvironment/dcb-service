package org.olf.dcb.api;

import java.util.UUID;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;

@Singleton
public class LiveAvailabilityApiClient {
	private final HttpClient httpClient;

	public LiveAvailabilityApiClient(@Client("/") HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public AvailabilityResponse getAvailabilityReport(UUID clusterRecordId) {
		final var uri = UriBuilder.of("/items/availability")
			.queryParam("clusteredBibId", clusterRecordId)
			.build();

		return httpClient.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponse.class);
	}
}
