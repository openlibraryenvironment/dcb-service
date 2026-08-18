package org.olf.dcb.core.api;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.storage.ConsortiumRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * The consortium's patron-facing brand (N-1.1), for discovery services.
 *
 * <h2>Why this exists at all</h2>
 *
 * DCB already holds the consortium — name, display name, website — and it is already
 * administered in dcb-admin-ui. A discovery deployment that keeps a second copy of the
 * consortium's name in a hand-edited config file guarantees the two diverge, and the
 * first person to notice will be a buyer reading a screenshot. So DCB is the system of
 * record for BOTH levels of the brand chain, and the discovery app's static branding
 * file becomes a generated cache and an offline fallback rather than a source.
 *
 * <h2>What it deliberately does not serve</h2>
 *
 * Not `description` — that is staff-facing prose about the consortium and this is copy
 * shown to a patron under a search box. Not headerImageUrl or aboutImageUrl, which are
 * 36x36 and 48x48 admin-chrome icons rather than a brand mark. Not the functional
 * settings, the member list, or anything else on the entity: those describe how the
 * consortium is configured, and this route is anonymous.
 *
 * <h2>Anonymous, and bounded by construction</h2>
 *
 * Same justification as {@link DiscoveryLibrariesController}: a consortium's name,
 * website and logo are public facts, published on its own homepage. At most one
 * consortium row can exist in a DCB instance, so this is a single-row read that cannot
 * grow with the corpus, the membership or the request volume — there is no fan-out here
 * to amplify. Rate-limit /discovery/** at the ingress regardless, as the library
 * directory's note already says.
 *
 * An instance with no consortium — a standalone DCB — returns 404, and a consumer falls
 * back to its own configuration. That is the correct answer rather than an empty brand:
 * "there is no consortium here" and "the consortium has filled nothing in" are different
 * facts and a discovery app renders them differently.
 */
@Controller("/discovery/consortium")
@Secured(IS_ANONYMOUS)
@Tag(name = "Discovery API")
@Slf4j
public class DiscoveryConsortiumController {

	private final ConsortiumRepository consortiumRepository;

	public DiscoveryConsortiumController(ConsortiumRepository consortiumRepository) {
		this.consortiumRepository = consortiumRepository;
	}

	@Operation(summary = "The consortium brand",
		description = "The consortium's patron-facing brand for a discovery interface: display name, "
			+ "logo and its alt text, an optional square header icon, an optional landing "
			+ "background, patron welcome copy and the theme name. Every field except the "
			+ "name may be null - a consortium that has uploaded no mark is rendered by its name. "
			+ "themeName must be tolerated on read: an unrecognised value falls back to the consumer's "
			+ "default rather than failing. Returns 404 on an instance with no consortium, where a "
			+ "consumer should fall back to its own configuration.")
	@Get
	public Mono<ConsortiumBrand> get() {
		return Mono.from(consortiumRepository.findFirst())
			.map(ConsortiumBrand::from);
	}

	/**
	 * One brand level, in the shape the chain wants: a name, an optional mark, optional
	 * copy and an optional theme. Identical in shape to a library's level and to a
	 * standalone institution's, because the only difference between those deployments is
	 * how many levels there are — which is data, not a code path.
	 */
	@Serdeable
	public record ConsortiumBrand(
		String name,
		@Nullable String logoUrl,
		@Nullable String logoAlt,
		/**
		 * A square mark for the app bar and the favicon (R-17d). Distinct from the logo,
		 * which is a lockup: a consumer that has only one of the two should use the logo
		 * for the lockup slot and nothing for the icon slot, rather than squashing one
		 * into the other.
		 */
		@Nullable String headerIconUrl,
		/**
		 * The canvas behind a landing hero (R-17d). DECORATIVE — it carries no
		 * information, so a consumer renders it with an empty alt or as a CSS background,
		 * and text over it belongs on a scrim so that contrast is measured against a
		 * known colour rather than against whatever was uploaded.
		 *
		 * Consortium level only. There is no library equivalent and there should not be:
		 * a mark identifies an organisation, a canvas does not.
		 */
		@Nullable String backgroundImageUrl,
		@Nullable String welcome,
		@Nullable String themeName) {

		static ConsortiumBrand from(Consortium consortium) {
			// displayName is what the consortium calls itself in public; name is the
			// internal identifier, and several deployments have it as a code. Falling
			// back to it beats rendering nothing when displayName was never filled in.
			final var displayName = consortium.getDisplayName();

			return new ConsortiumBrand(
				displayName != null && !displayName.isBlank() ? displayName : consortium.getName(),
				consortium.getBrandLogoUrl(),
				// Named by its own name when no alt text was supplied. An <img> with no
				// alt is an unlabelled image to a screen reader; the name always exists.
				consortium.getBrandLogoUrl() == null
					? null
					: alt(consortium),
				consortium.getBrandHeaderIconUrl(),
				consortium.getBrandBackgroundImageUrl(),
				consortium.getPatronWelcome(),
				consortium.getDefaultThemeName());
		}

		private static String alt(Consortium consortium) {
			final var configured = consortium.getBrandLogoAlt();

			if (configured != null && !configured.isBlank()) {
				return configured;
			}

			final var displayName = consortium.getDisplayName();

			return displayName != null && !displayName.isBlank()
				? displayName
				: consortium.getName();
		}
	}
}
