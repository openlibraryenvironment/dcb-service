package org.olf.dcb.core.branding;

import java.time.Duration;

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
	 * Where uploaded images are kept: {@code database} or {@code none}.
	 *
	 * <p>{@code none} removes the upload routes from the running service — not "present and
	 * failing", absent, because both controllers are {@code @Requires(beans =
	 * BrandAssetStore.class)}. Brand fields still accept an absolute CDN URL, which is a
	 * first-class way to brand a consortium (§R-7.4) and not a degraded mode. A deployment
	 * that does not want images in its database says so and loses nothing else.
	 *
	 * <p>Object storage was the original implementation and will return as a third value.
	 * It is a reasonable option for estates that already run a bucket; it was the wrong
	 * thing to require of everybody, because it made the feature untestable without one.
	 */
	private String store = "database";

	/**
	 * How long an unreferenced asset is kept before {@link BrandAssetSweep} removes it.
	 *
	 * <p>Not a tuning knob so much as a correctness one: an administrator uploads and then
	 * saves the URL with a separate mutation, so an asset is legitimately unreferenced in
	 * between. Too short and the sweep deletes the image somebody is part-way through
	 * choosing.
	 */
	private Duration orphanGracePeriod = Duration.ofDays(1);

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
