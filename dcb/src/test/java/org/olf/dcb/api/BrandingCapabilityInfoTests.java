package org.olf.dcb.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.test.DcbTest;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

/**
 * {@code /info} advertises whether this deployment accepts brand image uploads.
 *
 * <h2>Why this is a test and not a comment</h2>
 *
 * DCB Admin's {@code BrandImageField} renders an upload button and a URL field side by
 * side. With {@code dcb.branding.assets.store=none} the upload routes are ABSENT, so the
 * button produces a 404 — and a 404 carries no {@code message} for the form to display, so
 * the administrator sees a generic "upload failed" every time, forever, on a deployment
 * that turned uploads off deliberately.
 *
 * <p>The frontend already reads {@code /info} through {@code useDCBServiceInfo}, so this
 * key is the whole fix: the form can offer the control or not, rather than offering one
 * that cannot work. That makes it a cross-repo contract, and a key nobody asserts is a key
 * somebody renames.
 */
@DcbTest
@TestInstance(PER_CLASS)
class BrandingCapabilityInfoTests {

	@Inject
	@Client("/")
	private HttpClient client;

	@Test
	void infoSaysWhichBrandAssetStoreIsInUse() {
		final var info = client.toBlocking().retrieve(
			HttpRequest.GET("/info"), Argument.mapOf(String.class, Object.class));

		assertThat("DCB Admin reads this to decide whether to offer an upload control",
			brandingStore(info), is("database"));
	}

	/**
	 * {@code /info} is deliberately anonymous — the admin app reads it before a user has
	 * done anything, and it carries no tenant data.
	 */
	@Test
	void infoIsReadableWithoutACredential() {
		final var status = client.toBlocking()
			.exchange(HttpRequest.GET("/info"))
			.getStatus();

		assertThat(status.getCode(), is(200));
	}

	@SuppressWarnings("unchecked")
	private static Object brandingStore(Map<String, Object> info) {
		final var dcb = (Map<String, Object>) info.get("dcb");
		final var branding = (Map<String, Object>) dcb.get("branding");
		final var assets = (Map<String, Object>) branding.get("assets");

		return assets.get("store");
	}
}
