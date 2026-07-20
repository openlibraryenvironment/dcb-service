package services.k_int.integration.opensearch;

import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import tools.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.ArrayUtils;
import jakarta.inject.Singleton;

@Requires(beans = DefaultOpenSearchConfigurationProperties.class)
@Factory
public class DefaultOpenSearchClientFactory {

	/**
	 * @param openSearchConfiguration The {@link DefaultOpenSearchConfigurationProperties} object
	 * @return The OpenSearch Rest Client
	 */
	@Bean(preDestroy = "close")
	RestClient restClient(DefaultOpenSearchConfigurationProperties openSearchConfiguration) {
		return restClientBuilder(openSearchConfiguration).build();
	}

	/**
	 * @param transport The {@link OpenSearchTransport} object.
	 * @return The OpenSearchClient.
	 * @since 4.2.0
	 */
	@Singleton
	OpenSearchClient openSearchClient(OpenSearchTransport transport) {
		return new OpenSearchClient(transport);
	}

	/**
	 * @param transport The {@link OpenSearchTransport} object.
	 * @return The OpenSearchAsyncClient.
	 * @since 4.2.0
	 */
	@Singleton
	OpenSearchAsyncClient openSearchAsyncClient(OpenSearchTransport transport) {
		return new OpenSearchAsyncClient(transport);
	}

	/**
	 * @param openSearchConfiguration The
	 *                                {@link DefaultOpenSearchConfigurationProperties}
	 *                                object.
	 * @param objectMapper            The {@link ObjectMapper} object.
	 * @return The {@link OpenSearchTransport}.
	 * @since 4.2.0
	 */
	// NB: this uses org.opensearch.client.json.jackson3.JacksonJsonpMapper, NOT the
	// legacy org.opensearch.client.json.jackson one. The legacy mapper is built
	// against Jackson 2 (com.fasterxml.jackson.databind.ObjectMapper) and cannot
	// accept the Jackson 3 ObjectMapper that Micronaut 5 injects. opensearch-java
	// 3.9.0 added the jackson3 variant, which keeps OpenSearch on the same
	// ObjectMapper as the rest of the application.
	@Singleton
	@Bean(preDestroy = "close")
	OpenSearchTransport openSearchTransport(DefaultOpenSearchConfigurationProperties openSearchConfiguration,
			ObjectMapper objectMapper) {
		RestClient restClient = restClientBuilder(openSearchConfiguration).build();

		OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
		return transport;
	}

	/**
	 * @param openSearchConfiguration The {@link DefaultOpenSearchConfigurationProperties} object
	 * @return The {@link RestClientBuilder}
	 */
	protected RestClientBuilder restClientBuilder(DefaultOpenSearchConfigurationProperties openSearchConfiguration) {
		RestClientBuilder builder = RestClient.builder(openSearchConfiguration.getHttpHosts())
				.setRequestConfigCallback(requestConfigBuilder -> {
					requestConfigBuilder = openSearchConfiguration.requestConfigBuilder;
					return requestConfigBuilder;
				}).setHttpClientConfigCallback(httpClientBuilder -> {
					httpClientBuilder = openSearchConfiguration.httpAsyncClientBuilder;
					return httpClientBuilder;
				});

		if (ArrayUtils.isNotEmpty(openSearchConfiguration.getDefaultHeaders())) {
			builder.setDefaultHeaders(openSearchConfiguration.getDefaultHeaders());
		}

		return builder;
	}

}
