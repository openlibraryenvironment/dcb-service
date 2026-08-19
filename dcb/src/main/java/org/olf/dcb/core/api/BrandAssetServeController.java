package org.olf.dcb.core.api;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import org.olf.dcb.core.branding.BrandAssetStore;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

/**
 * Serving a stored brand image back (R-17b).
 *
 * <h2>Anonymous, and that is the requirement rather than a concession</h2>
 *
 * A consortium's logo is rendered on an anonymous page to an unauthenticated patron.
 * Requiring a credential to fetch it would mean the sign-in page could not show the mark of
 * the organisation asking for the credential.
 *
 * <p>The path is real and starts {@code /discovery} on purpose: that is what puts this
 * route inside {@code ApiSecurityArchitectureTests}'
 * {@code theDiscoverySurfaceIsReachableOnlyByTheDiscoveryRole}, which permits
 * {@code IS_ANONYMOUS} here and would fail the build if somebody later gave this route a
 * staff role. On a pathless controller the guard could not see it at all.
 *
 * <h2>The response headers are load-bearing</h2>
 *
 * The key is the SHA-256 of the content, so the URL can never mean two different images
 * and the response is immutable for a year. {@code nosniff} matters more than usual here:
 * the object is user-supplied and served from the same origin as the patron interface, so
 * a browser must be told to believe the declared type rather than guess a better one.
 *
 * <h2>Bounded reads</h2>
 *
 * This is the only anonymous route in the service that reaches object storage, and it is
 * hit on the first paint of every patron page whose browser cache is cold. The store caches
 * what it serves — see {@code S3BrandAssetStore} — so a few hundred marks across 500
 * libraries do not become one object-storage GET per page load.
 */
@Controller("/discovery/brand-assets")
@Requires(beans = BrandAssetStore.class)
@Secured(IS_ANONYMOUS)
@Tag(name = "Branding API")
public class BrandAssetServeController {

	private final BrandAssetStore store;

	public BrandAssetServeController(BrandAssetStore store) {
		this.store = store;
	}

	@Operation(summary = "Fetch a stored brand image",
		description = "Anonymous, because a consortium's logo is rendered on the sign-in page. The "
			+ "key is the SHA-256 of the content, so the response is immutable and cacheable for a "
			+ "year: a replaced image is a different URL and no cache has to be told anything.")
	@Get("/{key}")
	public Mono<MutableHttpResponse<byte[]>> serve(@PathVariable String key) {
		// The key is a hex digest and an extension. Refusing anything else keeps a path
		// traversal from ever reaching the store, rather than trusting the store to
		// notice one.
		if (!key.matches("[0-9a-f]{64}\\.(png|jpg)")) {
			return Mono.just(HttpResponse.notFound());
		}

		return store.get(key)
			.map(asset -> HttpResponse.ok(asset.bytes())
				.contentType(MediaType.of(asset.contentType()))
				.header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
				.header("X-Content-Type-Options", "nosniff")
				// An image the patron's browser renders inline, never a file it offers to
				// save and open. Belt and braces with the type check above.
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline"))
			.defaultIfEmpty(HttpResponse.notFound());
	}
}
