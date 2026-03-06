package org.olf.dcb.core.interaction.folio;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import jakarta.inject.Singleton;

@Singleton
public class FolioOaiClientConfig extends HttpClientConfiguration {
	final Optional<Duration> folioTimeout = Optional.of( Duration.ofMinutes(10) );
	
	HttpClientConfiguration defaultConfig;
	
	public FolioOaiClientConfig(DefaultHttpClientConfiguration defaultConfig) {
		this.defaultConfig = defaultConfig;
	}
	
	@Override
	public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
		return defaultConfig.getConnectionPoolConfiguration();
	}
	
	@Override
	public Optional<Duration> getReadTimeout() {
		return folioTimeout;
	}

}
