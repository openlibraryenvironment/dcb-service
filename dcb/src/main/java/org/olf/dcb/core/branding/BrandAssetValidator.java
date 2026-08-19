package org.olf.dcb.core.branding;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * What an uploaded brand image has to survive before it is stored (R-17c).
 *
 * <h2>SVG is not accepted, and that is a decision rather than an omission</h2>
 *
 * An SVG is a script-capable document. One served from our own origin is stored XSS in
 * the chrome of every page a patron sees, including the sign-in page. Sanitising was
 * considered and rejected: a sanitiser is a moving allow-list against a format that keeps
 * growing script surfaces, and what it buys is an administrator's convenience against a
 * patron's ability to use the catalogue safely. A consortium whose brand pack is SVG-only
 * exports a PNG at 2x, which is what it already does for every social platform.
 *
 * <h2>The allow-list is enforced by magic bytes, never by what the client said</h2>
 *
 * Neither the filename extension nor the request's {@code Content-Type} is evidence about
 * the bytes: both are chosen by whoever made the request. So the format is decided by the
 * first few bytes of the content and nothing else, and the media type we later serve the
 * object with is the one this class decided, not the one that arrived.
 *
 * <h2>The order of the checks is the whole point</h2>
 *
 * <ol>
 *   <li><b>Size</b>, before anything else. A cap that is only applied after the bytes are
 *       in memory has already lost.</li>
 *   <li><b>Format</b>, by magic bytes.</li>
 *   <li><b>Dimensions</b>, read from the image <em>header</em> — {@code ImageReader}
 *       gives width and height without decoding a single pixel. This is the
 *       decompression-bomb check: a 40 KB PNG can declare 30000x30000 and cost 3.6 GB of
 *       heap the instant something decodes it, so the refusal has to happen before the
 *       decode, not after.</li>
 *   <li><b>Re-encode</b>, last, and <em>in the format the image arrived as</em>. What gets
 *       stored is what a decoder produced from the pixels, not the bytes that arrived:
 *       that drops EXIF, colour profiles, trailing payloads and every polyglot trick that
 *       depends on a parser reading past the end of the image. It also means we never
 *       store a file we could not parse. It does not change the container — see
 *       {@link #write}.</li>
 * </ol>
 *
 * <h2>Why the allow-list is PNG and JPEG, and not WebP — decided 2026-08-18</h2>
 *
 * The JDK ships no WebP codec, so accepting WebP would mean storing bytes we could not
 * decode — the single case where the re-encode rule above is suspended, and precisely the
 * case where it matters most. The alternative was a third-party image decoder parsing
 * untrusted bytes, which is a supply-chain and attack-surface decision bought for an
 * administrator's convenience. Neither is worth it: a brand pack that is WebP-only
 * exports a PNG, which it already does for every platform that will not take WebP.
 *
 * The admin form says PNG or JPEG before the file picker rather than after the upload
 * fails, so nobody discovers this by being refused.
 */
@Singleton
@Slf4j
public class BrandAssetValidator {

	public static final String PNG = "image/png";
	public static final String JPEG = "image/jpeg";

	private static final byte[] PNG_MAGIC = {
		(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private final BrandAssetProperties properties;

	public BrandAssetValidator(BrandAssetProperties properties) {
		this.properties = properties;
	}

	/**
	 * @return the asset as it should be stored — re-encoded bytes and the media type they
	 *         actually are
	 * @throws HttpStatusException with 400 for anything that is not an image we accept,
	 *         and 413 for something merely too large
	 */
	public BrandAsset validate(byte[] uploaded) {
		if (uploaded == null || uploaded.length == 0) {
			throw badRequest("the uploaded file is empty");
		}

		if (uploaded.length > properties.getMaxBytes()) {
			throw new HttpStatusException(HttpStatus.REQUEST_ENTITY_TOO_LARGE,
				"the image is %d bytes; the limit is %d".formatted(uploaded.length, properties.getMaxBytes()));
		}

		final var declared = sniff(uploaded);

		if (declared == null) {
			throw badRequest("the file is not a PNG or a JPEG. SVG is not accepted: it is a "
				+ "script-capable document and would be served from the same origin as the "
				+ "patron interface. WebP is not accepted: it cannot be re-encoded here, and "
				+ "an image we cannot decode is one we will not store");
		}

		return reencode(uploaded, declared);
	}

	/**
	 * The media type the bytes actually are, or null if they are neither. Deliberately
	 * reads only the leading bytes: this is an identification, not a parse.
	 */
	static String sniff(byte[] bytes) {
		if (startsWith(bytes, PNG_MAGIC, 0)) {
			return PNG;
		}

		if (startsWith(bytes, JPEG_MAGIC, 0)) {
			return JPEG;
		}

		return null;
	}

	private static boolean startsWith(byte[] bytes, byte[] magic, int offset) {
		if (bytes.length < offset + magic.length) {
			return false;
		}

		for (int i = 0; i < magic.length; i++) {
			if (bytes[offset + i] != magic[i]) {
				return false;
			}
		}

		return true;
	}

	private BrandAsset reencode(byte[] uploaded, String declared) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(uploaded))) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

			if (!readers.hasNext()) {
				// Unreachable while the allow-list is PNG and JPEG, both of which every
				// JVM reads. Kept because the sniff and the decoder registry are two
				// different lists, and a build that removed a reader should fail here with
				// a sentence rather than a NoSuchElementException.
				throw badRequest("this build cannot decode " + declared);
			}

			final ImageReader reader = readers.next();
			try {
				reader.setInput(input);

				// Header only. No pixels have been decoded at this point and none must be
				// until these two numbers have been checked.
				final int width = reader.getWidth(0);
				final int height = reader.getHeight(0);

				if (width > properties.getMaxDimension() || height > properties.getMaxDimension()) {
					throw badRequest("the image is %dx%d; the limit is %d pixels on either edge"
						.formatted(width, height, properties.getMaxDimension()));
				}

				final BufferedImage decoded = reader.read(0);

				return write(decoded, declared);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException e) {
			log.warn("Brand asset upload could not be decoded: {}", e.getMessage());
			throw badRequest("the file could not be read as an image");
		}
	}

	/**
	 * Written back out in the format it arrived as.
	 *
	 * <p>The re-encode exists to drop EXIF, colour profiles, trailing payloads and every
	 * polyglot trick that depends on a parser reading past the end of the image. Decoding
	 * and rewriting in the <em>same</em> container does all of that. Changing the container
	 * is a separate and optional payload optimisation, and it is not done here.
	 *
	 * <h2>It used to convert, and why that was wrong</h2>
	 *
	 * Any image with no alpha channel was stored as a JPEG, on the reasoning that a
	 * background photograph written back as PNG is several megabytes where the JPEG is a
	 * few hundred kilobytes. The reasoning was sound; the signal was not.
	 * {@code hasAlpha()} is a proxy for "is this a photograph", and it misfires on the most
	 * common brand upload there is — a mark exported opaque, which is what Figma and
	 * Illustrator produce unless somebody ticks the transparency box. JPEG is worst at
	 * precisely what a logo is: flat colour, hard edges, letterforms. Those marks acquired
	 * ringing that {@code ImageIO}'s default quality gives no dial to soften, and the
	 * result was permanent — a content-addressed key served {@code immutable} for a year,
	 * in the chrome of every patron page.
	 *
	 * <p>The size that was being optimised is bounded anyway: {@code max-bytes} caps the
	 * pathological case, and the immutable response means a background is transferred once
	 * per browser per year. That is a smaller cost than a permanently degraded mark.
	 *
	 * <p>Transparency was never at risk either way — the JPEG branch was unreachable for
	 * any image that had an alpha channel to lose.
	 */
	private BrandAsset write(BufferedImage decoded, String target) throws IOException {
		final BufferedImage toWrite;

		if (JPEG.equals(target)) {
			// JPEG cannot be written from every BufferedImage type ImageIO can produce —
			// a TYPE_CUSTOM or an indexed model throws at write time rather than at read
			// time. Copying into TYPE_INT_RGB is the cheap way to make that impossible.
			toWrite = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
			final var graphics = toWrite.createGraphics();
			try {
				graphics.drawImage(decoded, 0, 0, null);
			}
			finally {
				graphics.dispose();
			}
		}
		else {
			toWrite = decoded;
		}

		final var out = new ByteArrayOutputStream();

		if (!ImageIO.write(toWrite, JPEG.equals(target) ? "jpeg" : "png", out)) {
			throw badRequest("the image could not be re-encoded");
		}

		final var bytes = out.toByteArray();

		if (bytes.length > properties.getMaxBytes()) {
			// A re-encode can grow a file — an aggressively compressed source written
			// back out at default settings, most often. Checked again for the same reason
			// it was checked the first time.
			throw new HttpStatusException(HttpStatus.REQUEST_ENTITY_TOO_LARGE,
				"the image is %d bytes once re-encoded; the limit is %d"
					.formatted(bytes.length, properties.getMaxBytes()));
		}

		return new BrandAsset(target, bytes);
	}

	/** The extension a stored key carries, so an object is recognisable in a listing. */
	static String extensionFor(String contentType) {
		return JPEG.equals(contentType) ? ".jpg" : ".png";
	}

	private static HttpStatusException badRequest(String message) {
		return new HttpStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
