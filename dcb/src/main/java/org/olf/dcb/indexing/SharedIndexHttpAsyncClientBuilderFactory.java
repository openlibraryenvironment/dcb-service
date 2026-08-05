package org.olf.dcb.indexing;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

/**
 * Supplies the HTTP client builder that carries the shared index credentials
 * ({@code dcb.index.username} / {@code dcb.index.password}) and the optional
 * trust-all-certs behaviour.
 *
 * <p>NB: two builders are produced deliberately, because the two supported backends are
 * built against different Apache HttpClient generations:
 *
 * <ul>
 *   <li>opensearch-rest-client 3.7.0 uses HttpClient 5
 *       ({@code org.apache.hc.client5...HttpAsyncClientBuilder})</li>
 *   <li>micronaut-elasticsearch 6.0.0 still uses HttpClient 4
 *       ({@code org.apache.http...HttpAsyncClientBuilder}), injected into
 *       DefaultElasticsearchConfigurationProperties and applied via
 *       setHttpClientConfigCallback</li>
 * </ul>
 *
 * <p>The Micronaut 5 migration moved this factory wholesale to HttpClient 5. That is correct
 * for OpenSearch, but it silently left Elasticsearch with micronaut-elasticsearch's own
 * credential-less default builder, so every request to a secured Elasticsearch failed with
 * 401. Supplying both types keeps either backend authenticated.
 */
@Factory
public class SharedIndexHttpAsyncClientBuilderFactory {

	private static final String TRUST_ALL_CERTS = "trust-all-certs";
	private static final Logger log = LoggerFactory.getLogger(SharedIndexHttpAsyncClientBuilderFactory.class);

	@Bean
	@Singleton
	@Order( value = Ordered.HIGHEST_PRECEDENCE )
	@Requires(bean = SharedIndexConfiguration.class)
	HttpAsyncClientBuilder builder(SharedIndexConfiguration config, Environment env) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		var builder = HttpAsyncClientBuilder.create();
		
		if (env.getActiveNames().contains(TRUST_ALL_CERTS)) {
			// We can turn OFF cert varification
			var tlsStrategy = ClientTlsStrategyBuilder.create()
				.setSslContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
				.buildAsync();

			builder.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
				.setTlsStrategy(tlsStrategy)
				.build());

			final String warningBanner =
					"********************* WARNING ********************\n"
				+ "* SSL Certificate verification has been disabled *\n"
				+ "*          DO NOT DO THIS IN PRODUCTION          *\n"
				+ "**************************************************";
			
			System.out.println(warningBanner);
		}
		
		if (config.username().isEmpty() || config.password().isEmpty()) return builder;
		
		final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(config.username().get(), config.password().get().toCharArray()));

		
		return builder.setDefaultCredentialsProvider(credentialsProvider);

	}

	/**
	 * The HttpClient 4 equivalent, consumed by micronaut-elasticsearch. Marked @Primary
	 * because micronaut-elasticsearch's own DefaultHttpAsyncClientBuilderFactory also
	 * publishes a bean of this type; without it the injection point is ambiguous. This
	 * factory method only exists when a shared index is configured, so when it is not,
	 * the Micronaut default applies unopposed.
	 */
	@Bean
	@Singleton
	@Primary
	@Order( value = Ordered.HIGHEST_PRECEDENCE )
	@Requires(bean = SharedIndexConfiguration.class)
	org.apache.http.impl.nio.client.HttpAsyncClientBuilder legacyBuilder(SharedIndexConfiguration config, Environment env)
		throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		var builder = org.apache.http.impl.nio.client.HttpAsyncClientBuilder.create();

		if (env.getActiveNames().contains(TRUST_ALL_CERTS)) {
			builder.setSSLContext(new org.apache.http.ssl.SSLContextBuilder()
				.loadTrustMaterial(null, org.apache.http.conn.ssl.TrustAllStrategy.INSTANCE)
				.build());

			log.warn("SSL certificate verification has been disabled for the shared index client. DO NOT DO THIS IN PRODUCTION.");
		}

		if (config.username().isEmpty() || config.password().isEmpty()) return builder;

		final var credentialsProvider = new org.apache.http.impl.client.BasicCredentialsProvider();
		credentialsProvider.setCredentials(org.apache.http.auth.AuthScope.ANY,
			new org.apache.http.auth.UsernamePasswordCredentials(config.username().get(), config.password().get()));

		log.info("Shared index Elasticsearch client configured with credentials for user [{}]", config.username().get());

		return builder.setDefaultCredentialsProvider(credentialsProvider);
	}
}
