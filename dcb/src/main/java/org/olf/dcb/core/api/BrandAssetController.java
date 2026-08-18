package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;
import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import java.io.IOException;

import org.olf.dcb.core.branding.BrandAssetProperties;
import org.olf.dcb.core.branding.BrandAssetStore;
import org.olf.dcb.core.branding.BrandAssetValidator;
import org.olf.dcb.security.RoleNames;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Uploading a brand image, and serving it back (R-17b, R-17c).
 *
 * <h2>Two routes, two audiences, two security postures</h2>
 *
 * {@code POST /brand-assets} is an administrator action and carries the same roles the
 * GraphQL brand fetchers already check. It does not decide "may this administrator brand
 * this library?" a second time in a different vocabulary: the upload produces a URL and
 * nothing else, and it is the {@code updateConsortium} / {@code updateLibrary} mutation —
 * already authorised, already audited — that decides where that URL is allowed to land.
 * An upload with no mutation after it is an orphaned object, not a brand change.
 *
 * {@code GET /discovery/brand-assets/{key}} is anonymous because a consortium's logo is
 * rendered on an anonymous page to an unauthenticated patron. Requiring a credential to
 * fetch it would mean the sign-in page could not show the mark of the organisation asking
 * for the credential.
 *
 * <h2>Why the bytes are proxied instead of presigned</h2>
 *
 * The security of this feature is byte validation, and you cannot validate bytes you
 * never receive. A presigned PUT would keep image traffic off the app tier, which is the
 * right trade at a volume we do not have: a few hundred libraries, one mark each, changed
 * about once a year. If that ever changes, a presigned PUT is a different implementation
 * of {@link BrandAssetStore} and this controller is unaffected.
 *
 * <h2>The response headers on the read are load-bearing</h2>
 *
 * The key is the SHA-256 of the content, so the URL can never mean two different images
 * and the response is immutable for a year. {@code nosniff} matters more than usual here:
 * the object is user-supplied and served from the same origin as the patron interface, so
 * a browser must be told to believe the declared type rather than guess a better one.
 */
@Controller
@Requires(beans = BrandAssetStore.class)
@Tag(name = "Branding API")
@Slf4j
public class BrandAssetController {

	private final BrandAssetStore store;
	private final BrandAssetValidator validator;
	private final BrandAssetProperties properties;

	public BrandAssetController(BrandAssetStore store, BrandAssetValidator validator,
		BrandAssetProperties properties) {

		this.store = store;
		this.validator = validator;
		this.properties = properties;
	}

	@Operation(summary = "Upload a brand image",
		description = "Accepts a PNG, JPEG or WebP identified by its magic bytes - never by its "
			+ "filename or its declared content type. SVG is refused: it is a script-capable "
			+ "document and would be served from the same origin as the patron interface. The image "
			+ "is re-encoded before it is stored, so what is served is what a decoder produced and "
			+ "not the bytes that arrived. Returns the site-relative URL to store in a brand field.")
	@Post(value = "/brand-assets", consumes = MULTIPART_FORM_DATA)
	@Consumes(MULTIPART_FORM_DATA)
	@Secured({ RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN, RoleNames.LIBRARY_ADMIN })
	public Mono<UploadedAsset> upload(@Part("file") CompletedFileUpload file) {
		final byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException e) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "the upload could not be read");
		}

		// Validation first, storage second. Nothing that failed a check reaches the
		// bucket, so a rejected upload leaves nothing behind to sweep.
		final var asset = validator.validate(bytes);

		return store.put(asset)
			.map(key -> new UploadedAsset(properties.getPublicPathPrefix() + key,
				asset.contentType(), asset.size()));
	}

	@Operation(summary = "Fetch a stored brand image",
		description = "Anonymous, because a consortium's logo is rendered on the sign-in page. The "
			+ "key is the SHA-256 of the content, so the response is immutable and cacheable for a "
			+ "year: a replaced image is a different URL and no cache has to be told anything.")
	@Get("/discovery/brand-assets/{key}")
	@Secured(IS_ANONYMOUS)
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

	/** What an upload returns: the URL to store, and enough to show a confirmation. */
	@Serdeable
	public record UploadedAsset(String url, String contentType, int bytes) {
	}
}
