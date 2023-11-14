package services.k_int.integration.opensearch;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.NodeSelector;

public interface DefaultOpenSearchConfiguration {

	/**
	 * The one or more hosts that the client will communicate with, provided as
	 * instances of {@link HttpHost}.
	 *
	 * @return An array of {@link HttpHost}
	 */
	HttpHost[] getHttpHosts();

	/**
	 * The default headers that need to be sent with each request, to prevent having
	 * to specify them with each single request.
	 *
	 * @return An array of {@link Header}.
	 */
	Header[] getDefaultHeaders();

	/**
	 * The timeout that should be honoured in case multiple attempts are made for
	 * the same request. The default value is 30 seconds.
	 *
	 * @return The maximum retry timeout in millis.
	 */
	int getMaxRetryTimeoutMillis();

	/**
	 * The node selector to be used to filter the nodes the client will send
	 * requests to among the ones that are set to the client itself. By default the
	 * client sends requests to every configured node.
	 *
	 * @return The {@link NodeSelector} to be used.
	 */
	NodeSelector getNodeSelector();

	/**
	 * @return The builder to create default request configurations.
	 */
	RequestConfig.Builder getRequestConfigBuilder();

	/**
	 * The http client configuration (e.g. encrypted communication over ssl, or
	 * anything that the {@link HttpAsyncClientBuilder} allows to set).
	 *
	 * @return The {@link HttpAsyncClientBuilder} bean
	 */
	HttpAsyncClientBuilder getHttpAsyncClientBuilder();

}
