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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

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
}
