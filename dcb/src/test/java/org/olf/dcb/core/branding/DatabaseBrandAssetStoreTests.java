package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.storage.BrandAssetRepository;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * The database-backed store, against a real Postgres.
 *
 * <h2>Note what this test does NOT need</h2>
 *
 * No container of its own, no credentials, no endpoint override, no addressing mode. It is
 * an ordinary {@code @DcbTest} against the Postgres that
 * {@code DcbTestContainerContextBuilder} already provides for every test in the suite. That
 * is the whole argument for storing brand images here rather than in object storage, and
 * this file is the evidence for it.
 */
@DcbTest
@TestInstance(PER_CLASS)
class DatabaseBrandAssetStoreTests {

	@Inject
	private BrandAssetRepository repository;

	@Inject
	private DatabaseBrandAssetStore store;

	@Test
	void bytesSurviveTheRoundTripExactly() throws IOException {
		final var original = png(64, 64);
		final var asset = new BrandAsset(BrandAssetValidator.PNG, original);

		final var key = singleValueFrom(store.put(asset));
		final var read = singleValueFrom(store.get(key));

		assertThat("A brand image is not text and must not be transcoded on the way through",
			read.bytes(), is(original));
		assertThat(read.contentType(), is(BrandAssetValidator.PNG));
	}

	@Test
	void theKeyIsTheContentDigestAndAnExtension() throws IOException {
		final var key = singleValueFrom(store.put(new BrandAsset(BrandAssetValidator.PNG, png(32, 32))));

		assertThat(key, matchesRegex("[0-9a-f]{64}\\.png"));
	}

	/**
	 * Content-addressed keys make this idempotent rather than a conflict. Two
	 * administrators uploading the same consortium logo minutes apart is ordinary.
	 */
	@Test
	void storingTheSameBytesTwiceIsOneRow() throws IOException {
		final var asset = new BrandAsset(BrandAssetValidator.PNG, png(48, 48));

		final var first = singleValueFrom(store.put(asset));
		final var second = singleValueFrom(store.put(asset));

		assertThat(second, is(first));
		assertThat(singleValueFrom(store.get(first)).bytes(), is(asset.bytes()));
	}

	@Test
	void differentImagesGetDifferentKeys() throws IOException {
		final var one = singleValueFrom(store.put(new BrandAsset(BrandAssetValidator.PNG, png(16, 16))));
		final var two = singleValueFrom(store.put(new BrandAsset(BrandAssetValidator.PNG, png(24, 24))));

		assertThat(one, is(org.hamcrest.Matchers.not(two)));
	}

	@Test
	void anAbsentKeyIsEmptyRatherThanAnError() {
		assertThat(singleValueFrom(store.get("a".repeat(64) + ".png")
			.map(Object.class::cast)
			.defaultIfEmpty("absent")), is("absent"));
	}

	@Test
	void deletingRemovesTheRowAndIsIdempotent() throws IOException {
		final var key = singleValueFrom(store.put(new BrandAsset(BrandAssetValidator.PNG, png(20, 20))));

		assertThat(isStored(key), is(true));

		singleValueFrom(store.delete(key).thenReturn("done"));
		singleValueFrom(store.delete(key).thenReturn("done"));

		assertThat(isStored(key), is(false));
	}

	/**
	 * The delete has to evict, or a removed image goes on being served for the rest of the
	 * cache TTL. Reading it first is what puts it in the cache.
	 */
	@Test
	void aDeletedAssetStopsBeingServedImmediately() throws IOException {
		final var key = singleValueFrom(store.put(new BrandAsset(BrandAssetValidator.PNG, png(28, 28))));

		assertThat(singleValueFrom(store.get(key)), is(notNullValue()));

		singleValueFrom(store.delete(key).thenReturn("done"));

		assertThat(singleValueFrom(store.get(key)
			.map(Object.class::cast)
			.defaultIfEmpty("gone")), is("gone"));
	}

	/** A JPEG is stored and served as a JPEG, so the extension and type stay in step. */
	@Test
	void theStoredTypeIsCarriedBackOut() {
		final var jpegBytes = "pretend jpeg".getBytes(StandardCharsets.UTF_8);
		final var key = singleValueFrom(store.put(new BrandAsset(BrandAssetValidator.JPEG, jpegBytes)));

		assertThat(key, org.hamcrest.Matchers.endsWith(".jpg"));
		assertThat(singleValueFrom(store.get(key)).contentType(), is(BrandAssetValidator.JPEG));
	}

	private boolean isStored(String key) {
		return singleValueFrom(Mono.from(repository.findById(key)).hasElement());
	}

	private static byte[] png(int width, int height) throws IOException {
		final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		final var graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(0x33, 0x66, 0x99, 0x80));
			graphics.fillRect(0, 0, width, height);
		}
		finally {
			graphics.dispose();
		}

		final var out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);

		return out.toByteArray();
	}
}
