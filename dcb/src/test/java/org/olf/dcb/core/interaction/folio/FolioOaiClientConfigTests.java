package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.micronaut.http.client.DefaultHttpClientConfiguration;

class FolioOaiClientConfigTests {

	// FolioOaiClientConfig must extend the CONCRETE DefaultHttpClientConfiguration, so every
	// setting it does not explicitly delegate -- getHttp2Configuration() among ~14 others --
	// inherits a non-null default. Extending the abstract HttpClientConfiguration instead
	// leaves getHttp2Configuration() null, and an h2/ALPN negotiation against a real FOLIO
	// server then NPEs in ConnectionManager.makeFrameCodec ("Connect Error: null"). Plaintext
	// MockServer never fires ALPN, so this unit check is the guard against reverting the base.
	@Test
	void inheritsNonNullHttp2ConfigurationFromTheConcreteDefault() {
		var config = new FolioOaiClientConfig(new DefaultHttpClientConfiguration());

		assertThat(config.getHttp2Configuration(), notNullValue());
	}
}
