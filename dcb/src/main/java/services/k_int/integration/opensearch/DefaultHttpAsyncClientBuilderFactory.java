package services.k_int.integration.opensearch;

import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.RestClient;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

@Requires(classes = { RestClient.class })
@Factory
public class DefaultHttpAsyncClientBuilderFactory {

	/**
	 * The http client configuration (e.g. encrypted communication over ssl, or
	 * anything that the {@link HttpAsyncClientBuilder} allows to set).
	 *
	 * @return The {@link HttpAsyncClientBuilder} bean with default configurations.
	 */
	@Bean
	@Singleton
	@Order( value = Ordered.LOWEST_PRECEDENCE )
	protected HttpAsyncClientBuilder httpAsyncClientBuilder() {
		return HttpAsyncClientBuilder.create();
	}
}
