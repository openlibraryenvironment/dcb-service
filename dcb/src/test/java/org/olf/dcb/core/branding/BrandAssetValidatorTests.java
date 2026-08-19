package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * What an upload has to survive (R-17c). No context and no bucket: this is the security
 * boundary of the feature and it should cost nothing to run on every build.
 *
 * Every case here is one an administrator could actually produce, or one an attacker
 * would actually try. There is no case for "a valid PNG is accepted" alone, because that
 * passes with no validation at all.
 */
class BrandAssetValidatorTests {

	private final BrandAssetValidator validator = new BrandAssetValidator(new BrandAssetProperties());

	@Test
	void shouldAcceptAPngAndKeepItAPngWhenItHasTransparency() throws IOException {
		final var stored = validator.validate(png(64, 64, true));

		assertThat(stored.contentType(), is(BrandAssetValidator.PNG));
		assertThat(BrandAssetValidator.sniff(stored.bytes()), is(BrandAssetValidator.PNG));
	}

	/**
	 * An opaque PNG stays a PNG.
	 *
	 * This used to be stored as a JPEG, to save bytes on a background photograph. The
	 * saving was real and the signal was not: no alpha channel is a proxy for "photograph",
	 * and it misfires on a logo exported opaque - which is what Figma and Illustrator
	 * produce unless somebody ticks the transparency box. JPEG is worst at flat colour and
	 * letterforms, and the result was permanent, since the key is content-addressed and the
	 * response is immutable for a year.
	 */
	@Test
	void shouldKeepAnOpaquePngAsAPng() throws IOException {
		final var stored = validator.validate(png(64, 64, false));

		assertThat(stored.contentType(), is(BrandAssetValidator.PNG));
		assertThat(BrandAssetValidator.sniff(stored.bytes()), is(BrandAssetValidator.PNG));
	}

	/** The other direction: a photograph uploaded as a JPEG is not inflated into a PNG. */
	@Test
	void shouldKeepAJpegAsAJpeg() throws IOException {
		final var stored = validator.validate(jpeg(64, 64));

		assertThat(stored.contentType(), is(BrandAssetValidator.JPEG));
		assertThat(BrandAssetValidator.sniff(stored.bytes()), is(BrandAssetValidator.JPEG));
	}

	/**
	 * The whole reason SVG is not on the allow-list. It is a script-capable document and
	 * one served from our origin is stored XSS in the chrome of every patron page,
	 * including the sign-in page.
	 */
	@Test
	void shouldRejectAnSvg() {
		final var svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\">"
			+ "<script>alert(document.cookie)</script></svg>").getBytes(StandardCharsets.UTF_8);

		assertBadRequest(() -> validator.validate(svg));
	}

	/** Decided 2026-08-18: the JDK cannot re-encode it, so we will not store it. */
	@Test
	void shouldRejectAWebP() {
		final var webp = new byte[64];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
		System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);

		assertBadRequest(() -> validator.validate(webp));
	}

	/**
	 * The case that makes magic-byte sniffing necessary rather than tidy. The filename
	 * and the multipart Content-Type are chosen by whoever made the request, so neither
	 * is evidence: only the bytes are.
	 */
	@Test
	void shouldRejectAScriptWhateverItClaimsToBe() {
		assertBadRequest(() -> validator.validate(
			"<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void shouldRejectAnEmptyUpload() {
		assertBadRequest(() -> validator.validate(new byte[0]));
	}

	/**
	 * The decompression bomb. A tiny PNG that declares enormous dimensions costs
	 * gigabytes of heap the instant something decodes it, so the refusal has to come from
	 * the header — before the decode — and not from a try/catch around an
	 * OutOfMemoryError, which is not recoverable.
	 */
	@Test
	void shouldRejectAnImageLargerThanTheDimensionCapWithoutDecodingIt() throws IOException {
		final var properties = new BrandAssetProperties();
		properties.setMaxDimension(32);

		final var narrow = new BrandAssetValidator(properties);

		final var exception = assertThrows(HttpStatusException.class,
			() -> narrow.validate(png(64, 64, true)));

		assertThat(exception.getStatus(), is(HttpStatus.BAD_REQUEST));
		assertThat(exception.getMessage().contains("64x64"), is(true));
	}

	@Test
	void shouldRejectAnImageOverTheByteCapAsTooLargeRatherThanAsInvalid() throws IOException {
		final var properties = new BrandAssetProperties();
		properties.setMaxBytes(10);

		final var tiny = new BrandAssetValidator(properties);

		final var exception = assertThrows(HttpStatusException.class,
			() -> tiny.validate(png(64, 64, true)));

		// 413 rather than 400: "too big" and "not an image" are different facts and an
		// administrator can act on the first one.
		assertThat(exception.getStatus(), is(HttpStatus.REQUEST_ENTITY_TOO_LARGE));
	}

	/**
	 * The polyglot. A file that is a valid PNG and then keeps going is served by most
	 * things as an image and read by something else as whatever was appended. Re-encoding
	 * is what makes the tail impossible to carry — and this test is the reason the
	 * validator decodes and rewrites instead of checking the header and storing what
	 * arrived.
	 */
	@Test
	void shouldDropAnythingAppendedAfterTheImageData() throws IOException {
		final var image = png(32, 32, true);
		final var payload = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

		final var polyglot = new byte[image.length + payload.length];
		System.arraycopy(image, 0, polyglot, 0, image.length);
		System.arraycopy(payload, 0, polyglot, image.length, payload.length);

		final var stored = validator.validate(polyglot);

		assertThat(indexOf(stored.bytes(), payload), is(-1));
		assertThat(stored.size(), is(lessThan(polyglot.length)));
		assertThat(stored.size(), is(greaterThan(0)));
	}

	@Test
	void shouldNameTheStoredKeyAfterTheContentSoTheSameFileIsTheSameObject() throws IOException {
		final var first = validator.validate(png(32, 32, true));
		final var second = validator.validate(png(32, 32, true));

		assertThat(first.key(), is(second.key()));
		assertThat(first.key().endsWith(".png"), is(true));
	}

	@Test
	void shouldGiveADifferentKeyToADifferentImage() throws IOException {
		assertThat(validator.validate(png(32, 32, true)).key()
				.equals(validator.validate(png(48, 48, true)).key()),
			is(false));
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

	/** A JPEG, which has no alpha channel by construction. */
	private static byte[] jpeg(int width, int height) throws IOException {
		final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		final var graphics = image.createGraphics();
		try {
			graphics.setColor(new Color(0x33, 0x66, 0x99));
			graphics.fillRect(0, 0, width, height);
		}
		finally {
			graphics.dispose();
		}

		final var out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", out);

		return out.toByteArray();
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	private static void assertBadRequest(Runnable action) {
		final var exception = assertThrows(HttpStatusException.class, action::run);

		assertThat(exception.getStatus(), is(HttpStatus.BAD_REQUEST));
	}
}
