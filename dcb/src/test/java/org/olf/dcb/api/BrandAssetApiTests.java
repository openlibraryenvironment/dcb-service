package org.olf.dcb.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.BrandAssetUploadController.UploadedAsset;
import org.olf.dcb.core.branding.BrandAsset;
import org.olf.dcb.core.branding.BrandAssetStore;
import org.olf.dcb.core.branding.BrandAssetValidator;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * The brand asset routes over HTTP, which had no coverage at all.
 *
 * <h2>Why this exists as an API test and not another validator test</h2>
 *
 * {@code BrandAssetValidatorTests} proves the bytes are judged correctly, and it is
 * thorough. What nothing proved was that a request can reach it: multipart binding, the
 * two independent size caps, whether a refusal keeps its sentence by the time it is a
 * response body, and whether the anonymous read route rejects a traversal. Every one of
 * those lives in the HTTP layer, and the one that bit hardest — a framework file-size
 * limit smaller than the configured one — is invisible from below it.
 *
 * <h2>The store is stubbed on purpose</h2>
 *
 * {@code BrandAssetStore} exists so the tests do not need a bucket; its own javadoc says
 * so. An in-memory one keeps this test about the HTTP layer rather than about MinIO, and
 * it counts its reads so the caching claim on the anonymous route is measurable rather
 * than asserted.
 */
@DcbTest
@TestInstance(PER_CLASS)
class BrandAssetApiTests {

	private static final String ADMIN_TOKEN = "brand-asset-tests-admin";
	private static final String READ_ONLY_TOKEN = "brand-asset-tests-read-only";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private BrandAssetStore store;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ADMIN_TOKEN, "an-admin", List.of(ADMINISTRATOR));
		TestStaticTokenValidator.add(READ_ONLY_TOKEN, "front-desk", List.of(LIBRARY_READ_ONLY));
	}

	// ---- upload ----

	@Test
	void aTransparentPngIsStoredAndItsUrlReturned() throws IOException {
		final var uploaded = upload("logo.png", png(64, 64, true), ADMIN_TOKEN);

		assertThat(uploaded.url(), allOf(
			startsWith("/discovery/brand-assets/"),
			matchesRegex("/discovery/brand-assets/[0-9a-f]{64}\\.png")));

		assertThat("A mark with an alpha channel must keep it, or it renders on a box in dark mode",
			uploaded.contentType(), is("image/png"));
	}

	/**
	 * The regression test for the defect this change fixes.
	 *
	 * <p>{@code micronaut.server.multipart.max-file-size} defaults to 1 MB in Micronaut 5
	 * while {@code dcb.branding.assets.max-bytes} defaults to 2 MB. Left unpinned, an
	 * upload between those two numbers is refused during multipart decoding — before
	 * routing, so the controller's {@code @Error} handler never runs and the administrator
	 * gets a bare problem detail. This is a legitimate background image at a size the
	 * configuration says is allowed.
	 */
	@Test
	void anImageLargerThanTheFrameworkDefaultButInsideTheConfiguredCapIsAccepted() throws IOException {
		// 600x600 of incompressible noise lands around 1.4MB: comfortably over the 1MB
		// framework default and comfortably under the 2MB configured cap, with room for
		// the re-encode to grow it slightly without crossing either.
		final var large = noisyPng(600, 600);

		assertThat("The fixture has to straddle the two limits or it proves nothing",
			large.length, allOf(
				org.hamcrest.Matchers.greaterThan(1024 * 1024),
				org.hamcrest.Matchers.lessThan(2 * 1024 * 1024)));

		final var uploaded = upload("background.png", large, ADMIN_TOKEN);

		assertThat(uploaded.url(), startsWith("/discovery/brand-assets/"));
	}

	/**
	 * A refusal that does not say why is the failure this feature already fixed once. The
	 * assertion is on the BODY deliberately: the status alone was never the problem.
	 */
	@Test
	void anSvgRenamedAsAPngIsRefusedWithAReason() {
		final var svg = """
			<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>
			""".getBytes(StandardCharsets.UTF_8);

		final var error = assertThrows(HttpClientResponseException.class,
			() -> upload("logo.png", svg, ADMIN_TOKEN));

		assertThat(error.getStatus(), is(HttpStatus.BAD_REQUEST));
		assertThat("The administrator has to be told what to do instead",
			bodyOf(error), allOf(
				containsString("not a PNG or a JPEG"),
				containsString("script-capable")));
	}

	@Test
	void anEmptyUploadIsRefusedWithAReason() {
		final var error = assertThrows(HttpClientResponseException.class,
			() -> upload("logo.png", new byte[0], ADMIN_TOKEN));

		assertThat(error.getStatus(), is(HttpStatus.BAD_REQUEST));
	}

	/** Uploading is a mutation, and read-only library staff do not get to brand a library. */
	@Test
	void readOnlyStaffCannotUpload() throws IOException {
		final var error = assertThrows(HttpClientResponseException.class,
			() -> upload("logo.png", png(32, 32, true), READ_ONLY_TOKEN));

		assertThat(error.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	@Test
	void anonymousCallersCannotUpload() throws IOException {
		final var body = MultipartBody.builder()
			.addPart("file", "logo.png", MediaType.IMAGE_PNG_TYPE, png(32, 32, true))
			.build();

		final var error = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().retrieve(
				HttpRequest.POST("/brand-assets", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
				UploadedAsset.class));

		assertThat(error.getStatus(), is(oneOf(
			HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	// ---- serve ----

	@Test
	void aStoredAssetIsServedWithTheHeadersThatMakeItSafeAndCacheable() throws IOException {
		final var uploaded = upload("logo.png", png(48, 48, true), ADMIN_TOKEN);
		final var key = uploaded.url().substring("/discovery/brand-assets/".length());

		final var response = client.toBlocking()
			.exchange(HttpRequest.GET("/discovery/brand-assets/" + key), byte[].class);

		assertThat(response.getStatus(), is(HttpStatus.OK));
		assertThat(response.getHeaders().get("X-Content-Type-Options"), is("nosniff"));
		assertThat(response.getHeaders().get(HttpHeaders.CACHE_CONTROL), containsString("immutable"));
		assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION), is("inline"));
		assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE), startsWith("image/png"));
	}

	/**
	 * Anonymous by requirement: the sign-in page must be able to show the mark of the
	 * organisation asking for the credential.
	 */
	@Test
	void servingIsAnonymous() throws IOException {
		final var uploaded = upload("logo.png", png(24, 24, true), ADMIN_TOKEN);
		final var key = uploaded.url().substring("/discovery/brand-assets/".length());

		final var status = client.toBlocking()
			.exchange(HttpRequest.GET("/discovery/brand-assets/" + key), byte[].class)
			.getStatus();

		assertThat(status, is(HttpStatus.OK));
	}

	@Test
	void aTraversalNeverReachesTheStore() {
		final var before = ((CountingBrandAssetStore) store).reads();

		for (final var key : List.of("../../etc/passwd", "..%2F..%2Fapplication.yml",
			"not-a-digest.png", "0123456789.png")) {

			final var error = assertThrows(HttpClientResponseException.class,
				() -> client.toBlocking().exchange(
					HttpRequest.GET("/discovery/brand-assets/" + key), byte[].class));

			assertThat(key, error.getStatus(), is(oneOf(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST)));
		}

		assertThat("The key shape is checked before the store is asked anything",
			((CountingBrandAssetStore) store).reads(), is(before));
	}

	@Test
	void anUnknownButWellFormedKeyIsANotFound() {
		final var error = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(
				HttpRequest.GET("/discovery/brand-assets/" + "a".repeat(64) + ".png"), byte[].class));

		assertThat(error.getStatus(), is(HttpStatus.NOT_FOUND));
	}

	// ---- helpers ----

	private UploadedAsset upload(String filename, byte[] bytes, String token) {
		final var body = MultipartBody.builder()
			.addPart("file", filename, MediaType.IMAGE_PNG_TYPE, bytes)
			.build();

		return client.toBlocking().retrieve(
			HttpRequest.POST("/brand-assets", body)
				.contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
				.bearerAuth(token),
			UploadedAsset.class);
	}

	private static String bodyOf(HttpClientResponseException error) {
		return error.getResponse().getBody(String.class).orElse("");
	}

	/** A PNG with or without an alpha channel, which is what decides the stored type. */
	private static byte[] png(int width, int height, boolean transparent) throws IOException {
		final var image = new BufferedImage(width, height,
			transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);

		final var graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(0x33, 0x66, 0x99, transparent ? 0x80 : 0xFF));
			graphics.fillRect(0, 0, width, height);
		}
		finally {
			graphics.dispose();
		}

		final var out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);

		return out.toByteArray();
	}

	/**
	 * A PNG that does not compress, so its size is predictable. A flat fill of any
	 * dimension is a few kilobytes and would never straddle the limits this test is about.
	 */
	private static byte[] noisyPng(int width, int height) throws IOException {
		final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		// Deterministic, so a failure is reproducible and the size does not wander
		// between runs.
		int state = 0x12345678;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				state = state * 1103515245 + 12345;
				image.setRGB(x, y, 0xFF000000 | (state >>> 8));
			}
		}

		final var out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);

		return out.toByteArray();
	}

	@MockBean(BrandAssetStore.class)
	BrandAssetStore brandAssetStore() {
		return new CountingBrandAssetStore();
	}

	/**
	 * In memory, and counting. The count is what lets the traversal test assert that the
	 * store was never asked, rather than that it happened to answer nothing.
	 */
	static class CountingBrandAssetStore implements BrandAssetStore {

		private final Map<String, BrandAsset> objects = new ConcurrentHashMap<>();
		private final AtomicInteger reads = new AtomicInteger();

		int reads() {
			return reads.get();
		}

		@Override
		public Mono<String> put(BrandAsset asset) {
			final var key = keyFor(asset);
			objects.put(key, asset);

			return Mono.just(key);
		}

		@Override
		public Mono<BrandAsset> get(String key) {
			reads.incrementAndGet();

			return Mono.justOrEmpty(objects.get(key));
		}

		@Override
		public Mono<Void> delete(String key) {
			objects.remove(key);

			return Mono.empty();
		}

		/** The same content-addressing the real store uses, so served URLs round-trip. */
		private static String keyFor(BrandAsset asset) {
			try {
				final var digest = java.security.MessageDigest.getInstance("SHA-256");

				return java.util.HexFormat.of().formatHex(digest.digest(asset.bytes()))
					+ (BrandAssetValidator.JPEG.equals(asset.contentType()) ? ".jpg" : ".png");
			}
			catch (java.security.NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-256 unavailable", e);
			}
		}
	}
}
