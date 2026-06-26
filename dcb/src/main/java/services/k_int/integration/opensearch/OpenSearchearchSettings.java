package services.k_int.integration.opensearch;

import org.apache.hc.core5.http.HttpHost;

public interface OpenSearchearchSettings {

	/**
	 * The prefix to use for all OpenSearch settings.
	 */
	String PREFIX = "opensearch";

	/**
	 * Default OpenSearch host.
	 */
	HttpHost DEFAULT_HOST = new HttpHost("http", "127.0.0.1", 9200);

}
