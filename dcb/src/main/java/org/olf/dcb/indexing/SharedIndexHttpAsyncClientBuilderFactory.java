package org.olf.dcb.indexing;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

@Factory
public class SharedIndexHttpAsyncClientBuilderFactory {
	
	private static final String DEVELOPMENT_FULL = "development"; 

	@Bean
	@Requires(bean = SharedIndexConfiguration.class)
	@Order( value = Ordered.HIGHEST_PRECEDENCE )
	@Singleton
	HttpAsyncClientBuilder builder(SharedIndexConfiguration config, Environment env) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		var builder = HttpAsyncClientBuilder.create();
		
		if (env.getActiveNames().contains(Environment.DEVELOPMENT) || env.getActiveNames().contains(DEVELOPMENT_FULL)) {
			// We can turn OFF cert varification
			builder.setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build());
			
		}
		
		if (config.username().isEmpty() || config.password().isEmpty()) return builder;
		
		final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.username().get(), config.password().get()));

		
		return builder.setDefaultCredentialsProvider(credentialsProvider);
		
	}
}
