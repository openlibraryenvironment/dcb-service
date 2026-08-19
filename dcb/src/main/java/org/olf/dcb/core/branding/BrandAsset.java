package org.olf.dcb.core.branding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One stored brand image: the bytes and the media type they are served as (R-17b).
 *
 * The media type is not the client's. It is decided by {@link BrandAssetValidator} from
 * what the bytes actually turned out to be after they were decoded and written back out,
 * which is the only version of it worth storing — a {@code Content-Type} header on an
 * upload is a claim by whoever made the request.
 */
public record BrandAsset(String contentType, byte[] bytes) {

	public int size() {
		return bytes.length;
	}

	/**
	 * The name this asset is stored and served under: the SHA-256 of its bytes, plus the
	 * extension for the media type it is stored as.
	 *
	 * <p>Content-addressed, which is what makes the served URL immutable and cacheable for
	 * a year: a replaced image is a different URL, so no cache anywhere has to be told
	 * about the change, and re-uploading an identical file is idempotent rather than a
	 * second object.
	 *
	 * <p>On the asset rather than on a store, because it is a property of the content. It
	 * used to be a static on the S3 implementation, which meant the answer to "what is this
	 * called" depended on which store happened to exist.
	 */
	public String key() {
		final MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is required of every JVM. If it is absent the platform is broken in
			// a way no fallback here would survive.
			throw new IllegalStateException("SHA-256 unavailable", e);
		}

		return HexFormat.of().formatHex(digest.digest(bytes))
			+ BrandAssetValidator.extensionFor(contentType);
	}
}
