package org.olf.dcb.indexing;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
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
	@Requires(bean = SharedIndexConfiguration.class)
	@Order( value = Ordered.HIGHEST_PRECEDENCE )
	@Singleton
	HttpAsyncClientBuilder builder(SharedIndexConfiguration config, Environment env) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		var builder = HttpAsyncClientBuilder.create();
		
		if (env.getActiveNames().contains(TRUST_ALL_CERTS)) {
			// We can turn OFF cert varification
			builder.setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build());

			final String warningBanner =
					"********************* WARNING ********************\n"
				+ "* SSL Certificate verification has been disabled *\n"
				+ "*          DO NOT DO THIS IN PRODUCTION          *\n"
				+ "**************************************************";
			
			System.out.println(warningBanner);
		}
		
		if (config.username().isEmpty() || config.password().isEmpty()) return builder;
		
		final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.username().get(), config.password().get()));

		
		return builder.setDefaultCredentialsProvider(credentialsProvider);
		
	}
}
