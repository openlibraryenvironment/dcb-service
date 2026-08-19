package org.olf.dcb.core.branding;

import java.util.List;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * The vocabulary a brand's {@code defaultThemeName} is validated against (N-1.3).
 *
 * <h2>Why a configured list and not an enum</h2>
 *
 * The themes themselves live in the discovery frontend, which is where a colour can
 * actually be rendered and measured. dcb-service cannot know them by construction, so
 * hardcoding an enum here would mean a schema change every time a brand is added, and
 * the two would drift the first time somebody forgot.
 *
 * A configured list keeps the stored value a plain string — which §F-7 requires anyway,
 * so that a deployment with tenant-supplied themes can widen the vocabulary without a
 * migration — while still refusing a typo at the point an administrator makes it.
 *
 * <h2>Why validate at all, if the reader tolerates a bad value</h2>
 *
 * The reader must tolerate one: an unrecognised theme has to fall back to the default
 * rather than white-screen a patron, and a theme removed between releases arrives at a
 * running app from a column nobody edited. That is a robustness property, not a licence
 * to store rubbish. Catching it on write is what stops an administrator setting a theme,
 * seeing no change, and having nothing to tell them why.
 */
@ConfigurationProperties("dcb.branding")
@Getter
@Setter
public class BrandingProperties {

	/**
	 * Theme names an administrator may choose from. Defaults to the registry
	 * symposia-ui ships; a deployment running a different discovery frontend sets
	 * {@code dcb.branding.theme-names} to its own.
	 *
	 * Empty means "accept any non-blank name": the honest behaviour for a deployment
	 * whose frontend we do not know, and it still refuses blanks and oversized values.
	 */
	private List<String> themeNames = List.of("openRS", "kInt");
}
