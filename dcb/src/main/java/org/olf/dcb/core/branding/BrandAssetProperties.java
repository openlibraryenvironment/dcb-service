package org.olf.dcb.core.branding;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * The limits and the location for uploaded brand assets (R-17b, R-17c).
 *
 * <p>The caps are configuration rather than constants because they are deployment facts: a
 * consortium that genuinely needs a larger canvas raises one knowingly instead of somebody
 * patching a number. What each one is for, and the numbers behind the defaults, are in
 * {@code docs/branding.md}.
 *
 * <p>The one thing worth repeating here, because it is a safety property and not a
 * preference: {@code maxDimension} is a decompression-bomb limit. It is checked from the
 * image header before any pixel is decoded — see {@link BrandAssetValidator} — because a
 * 40 KB PNG can declare 30000x30000 and cost 3.6 GB of heap the moment something calls
 * {@code ImageIO.read} on it.
 */
@ConfigurationProperties("dcb.branding.assets")
@Getter
@Setter
public class BrandAssetProperties {

	/**
	 * The S3-API bucket that holds the objects. Blank disables uploads entirely: the
	 * upload route is absent and the admin forms fall back to the CDN control, which is
	 * a first-class route in its own right (§R-7.4) and not a degraded mode.
	 */
	private String bucket = "";

	/** Key prefix inside the bucket, so a shared bucket stays legible. */
	private String prefix = "brand/";

	/**
	 * The path uploaded assets are served from, and the one {@link BrandingValidator}
	 * accepts as a site-relative brand URL. Anything else site-relative is still refused.
	 */
	private String publicPathPrefix = "/discovery/brand-assets/";

	/** Largest accepted upload, after which the request is refused rather than read. */
	private long maxBytes = 2L * 1024 * 1024;

	/** Largest accepted edge, in pixels, read from the header before any decode. */
	private int maxDimension = 4096;
}
