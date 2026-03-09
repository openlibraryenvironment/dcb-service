package org.olf.dcb.core.interaction.folio;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.http.client.DefaultHttpClientConfiguration;
import jakarta.inject.Singleton;

@Singleton
public class FolioOaiClientConfig extends DefaultHttpClientConfiguration {
	final Optional<Duration> folioTimeout = Optional.of( Duration.ofMinutes(10) );
	
	@Override
	public Optional<Duration> getReadTimeout() {
		return folioTimeout;
	}

}
