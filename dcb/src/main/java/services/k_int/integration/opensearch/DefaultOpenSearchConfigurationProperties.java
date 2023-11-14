package services.k_int.integration.opensearch;

import java.util.Collections;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.client.NodeSelector;
import org.opensearch.client.RestClientBuilder;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;

@Requires(classes = RestClientBuilder.class)
@ConfigurationProperties(OpenSearchearchSettings.PREFIX)
public class DefaultOpenSearchConfigurationProperties implements DefaultOpenSearchConfiguration {

	protected HttpAsyncClientBuilder httpAsyncClientBuilder;

	/**
	 * The default request configurations.
	 */
	@ConfigurationBuilder(configurationPrefix = "request.default")
	protected RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

	private int maxRetryTimeoutMillis;
	private NodeSelector nodeSelector;
	private HttpHost[] httpHosts = Collections.singletonList(OpenSearchearchSettings.DEFAULT_HOST)
			.toArray(new HttpHost[1]);
	private Header[] defaultHeaders;

	@Override
	public HttpHost[] getHttpHosts() {
		return this.httpHosts;
	}

	@Override
	public Header[] getDefaultHeaders() {
		return this.defaultHeaders;
	}

	@Override
	public int getMaxRetryTimeoutMillis() {
		return this.maxRetryTimeoutMillis;
	}

	@Override
	public NodeSelector getNodeSelector() {
		return this.nodeSelector;
	}

	@Override
	public RequestConfig.Builder getRequestConfigBuilder() {
		return this.requestConfigBuilder;
	}

	@Override
	public HttpAsyncClientBuilder getHttpAsyncClientBuilder() {
		return httpAsyncClientBuilder;
	}

	/**
	 * @param httpHosts One or more hosts that client will connect to.
	 */
	public void setHttpHosts(HttpHost[] httpHosts) {
		this.httpHosts = httpHosts;
	}

	/**
	 * @param defaultHeaders The defaults {@link Header} to sent with each request.
	 */
	public void setDefaultHeaders(Header[] defaultHeaders) {
		this.defaultHeaders = defaultHeaders;
	}

	/**
	 * @param maxRetryTimeoutMillis The maximum retry timeout in millis.
	 */
	public void setMaxRetryTimeoutMillis(int maxRetryTimeoutMillis) {
		this.maxRetryTimeoutMillis = maxRetryTimeoutMillis;
	}

	/**
	 * @param nodeSelector The {@link NodeSelector} to be used, in case of multiple
	 *                     nodes.
	 */
	public void setNodeSelector(NodeSelector nodeSelector) {
		this.nodeSelector = nodeSelector;
	}

	/**
	 * @param httpAsyncClientBuilder The {@link HttpAsyncClientBuilder} bean
	 */
	@Inject
	public void setHttpAsyncClientBuilder(HttpAsyncClientBuilder httpAsyncClientBuilder) {
		this.httpAsyncClientBuilder = httpAsyncClientBuilder;
	}
}
