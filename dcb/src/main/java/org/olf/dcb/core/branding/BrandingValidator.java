package org.olf.dcb.core.branding;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

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
 * through here at all. Neither check makes the other unnecessary.
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

	private final Set<String> themeNames;

	public BrandingValidator(BrandingProperties properties) {
		this.themeNames = Set.copyOf(properties.getThemeNames());
	}

	/**
	 * @return the trimmed URL, or null to clear the field
	 * @throws HttpStatusException if it is not an absolute http(s) URL
	 */
	@Nullable
	public String logoUrl(@Nullable String raw) {
		final var value = trimmed(raw);

		if (value == null) {
			return null;
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
			throw badRequest("brand logo URL must be an absolute http(s) URL");
		}

		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw badRequest("brand logo URL must name a host");
		}

		return value;
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
