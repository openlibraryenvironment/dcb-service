package org.olf.dcb.core.branding;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

/**
 * Write-time validation for the brand fields on {@code Consortium} and {@code Library}.
 *
 * <h2>The logo URL is the part that matters</h2>
 *
 * It is rendered as the {@code src} of an {@code <img>} in the chrome of every page of
 * the patron app, on an anonymous route, for every patron of that library. A value that
 * is not an absolute http(s) URL therefore has no legitimate use and at least one
 * illegitimate one — {@code javascript:} and {@code data:} both survive a naive "is it a
 * string" check, and a protocol-relative {@code //host/x} leaves the origin without ever
 * looking like it did.
 *
 * The discovery app validates this again on read, and that is deliberate rather than
 * redundant: it also reads a deployment's static branding file, which never passes
 * through here at all. Neither check makes the other unnecessary. Both must learn the
 * same rule, and there is now one more case to learn than there was.
 *
 * <h2>One new accepted form, and every old rejection kept (R-17d)</h2>
 *
 * An uploaded asset is served from a path we own, and a site-relative path is exactly the
 * form this validator exists to reject. So the rule is widened rather than relaxed: an
 * absolute http(s) URL, <em>or</em> a path under our own asset prefix and nothing else
 * under it. {@code data:}, {@code javascript:}, protocol-relative {@code //host/x} and
 * every other site-relative path are still refused, each with a test.
 *
 * The prefix check is not "starts with /discovery/brand-assets/". It is that plus the
 * shape of the key the store actually mints — a SHA-256 and a known extension — because
 * "starts with" would accept {@code /discovery/brand-assets/../../something} and a prefix
 * test that can be walked out of is not a prefix test.
 *
 * <h2>The links are validated too, and for the same reason</h2>
 *
 * {@code patronWebsite} and {@code supportUrl} on a library, {@code websiteUrl} and
 * {@code supportUrl} on a consortium, become the {@code href} of an anchor in the footer
 * of every page of the patron app. {@code javascript:} is as dangerous in an href as in
 * an img src, so {@link #linkUrl} applies the same scheme rule — minus the asset-prefix
 * case, which is a stored image and is never a website.
 *
 * patronWebsite and websiteUrl predate the check, so a value stored before it is served as it
 * stands, and kept when an edit sends it back unchanged ({@link #changedLinkUrl}). The
 * discovery app validates on read for exactly that reason.
 *
 * <h2>Blank clears the field</h2>
 *
 * An administrator who has uploaded the wrong mark must be able to remove it. The
 * surrounding update fetchers treat every absent key as "leave alone" and have no way to
 * express "set to null", so an explicitly blank brand value is read as a clear. That is
 * scoped to these fields on purpose — changing it for the whole input would alter the
 * behaviour of every other field on the form.
 */
@Singleton
public class BrandingValidator {

	/**
	 * The shape {@code BrandAsset.key()} mints: a SHA-256 hex digest and the extension of a
	 * format we re-encode to. Anchored at both ends on purpose.
	 */
	private static final Pattern ASSET_KEY = Pattern.compile("[0-9a-f]{64}[.](png|jpg)");

	private final Set<String> themeNames;
	private final String assetPathPrefix;

	public BrandingValidator(BrandingProperties properties, BrandAssetProperties assetProperties) {
		this.themeNames = Set.copyOf(properties.getThemeNames());
		this.assetPathPrefix = assetProperties.getPublicPathPrefix();
	}

	/**
	 * @return the trimmed URL, or null to clear the field
	 * @throws HttpStatusException if it is neither an absolute http(s) URL nor a path
	 *         under our own asset prefix
	 */
	@Nullable
	public String logoUrl(@Nullable String raw) {
		final var value = trimmed(raw);

		if (value == null) {
			return null;
		}

		// The one site-relative form we accept: an asset this deployment stored itself,
		// named by its own content. Checked before the URI parse so the failure message
		// for a near-miss is about the path rather than about a missing scheme.
		if (value.startsWith(assetPathPrefix)) {
			if (!ASSET_KEY.matcher(value.substring(assetPathPrefix.length())).matches()) {
				throw badRequest("brand asset path must name an asset this service stored");
			}

			return value;
		}

		final URI uri;
		try {
			uri = new URI(value);
		}
		catch (URISyntaxException e) {
			throw badRequest("brand logo URL is not a valid URL");
		}

		// getScheme() is null for both "//host/x" and "/path/x", which is exactly the
		// protocol-relative and relative case we must not accept: this URL is rendered by
		// a different origin from the one that stored it.
		final var scheme = uri.getScheme() == null
			? null
			: uri.getScheme().toLowerCase(Locale.ROOT);

		if (!"https".equals(scheme) && !"http".equals(scheme)) {
			throw badRequest("brand image URL must be an absolute http(s) URL, "
				+ "or a path under " + assetPathPrefix);
		}

		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw badRequest("brand logo URL must name a host");
		}

		return value;
	}

	/**
	 * @return the trimmed URL, or null to clear the field
	 * @throws HttpStatusException if it is not an absolute http(s) URL naming a host
	 */
	@Nullable
	public String linkUrl(@Nullable String raw) {
		final var value = trimmed(raw);

		if (value == null) {
			return null;
		}

		// Deliberately NOT the asset-prefix form logoUrl accepts. That prefix names an
		// image this service stored; a website is somewhere else entirely, and accepting
		// a path here would let a support link point at a PNG.
		final URI uri;
		try {
			uri = new URI(value);
		}
		catch (URISyntaxException e) {
			throw badRequest("link URL is not a valid URL");
		}

		final var scheme = uri.getScheme() == null
			? null
			: uri.getScheme().toLowerCase(Locale.ROOT);

		if (!"https".equals(scheme) && !"http".equals(scheme)) {
			throw badRequest("link URL must be an absolute http(s) URL");
		}

		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw badRequest("link URL must name a host");
		}

		return value;
	}

	/**
	 * As {@link #linkUrl}, but a value equal to the one already stored is kept as it is: a link
	 * saved before this check existed must not stop the rest of the record being saved.
	 */
	@Nullable
	public String changedLinkUrl(@Nullable String raw, @Nullable String stored) {
		final var value = trimmed(raw);

		return value != null && value.equals(stored) ? stored : linkUrl(raw);
	}

	/**
	 * @return the theme name, or null to clear the field
	 * @throws HttpStatusException if it is not in the configured vocabulary
	 */
	@Nullable
	public String themeName(@Nullable String raw) {
		final var value = trimmed(raw);

		if (value == null) {
			return null;
		}

		// Case-sensitive, and the case matters: the frontend's registry lookup is
		// case-sensitive too, so anything accepted here must be a value that resolves
		// there. Accepting "openrs" would store a name that silently renders the
		// default — the exact failure this check exists to prevent.
		if (!themeNames.isEmpty() && !themeNames.contains(value)) {
			throw badRequest("unknown theme name '" + value + "'. Known themes: " + themeNames);
		}

		return value;
	}

	/** Free text with no rendering hazard — length is enforced by the column and @Size. */
	@Nullable
	public String text(@Nullable String raw) {
		return trimmed(raw);
	}

	@Nullable
	private static String trimmed(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		final var value = raw.trim();
		return value.isEmpty() ? null : value;
	}

	private static HttpStatusException badRequest(String message) {
		return new HttpStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
