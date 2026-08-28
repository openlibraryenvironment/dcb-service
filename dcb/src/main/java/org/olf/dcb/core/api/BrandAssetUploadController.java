package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

import java.io.IOException;

import org.olf.dcb.core.branding.BrandAssetProperties;
import org.olf.dcb.core.branding.BrandAssetStore;
import org.olf.dcb.core.branding.BrandAssetValidator;
import org.olf.dcb.security.RoleNames;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Uploading a brand image (R-17b, R-17c).
 *
 * <h2>Why this is its own controller</h2>
 *
 * The upload and the read have different audiences and opposite security postures, and
 * they used to share a pathless {@code @Controller} with absolute paths on the methods.
 * That is not a style question: {@code ApiSecurityArchitectureTests} reads
 * {@code definition.stringValue(Controller.class)} to decide which rules apply to a route,
 * so every route on a pathless controller reports its path as the controller root. The
 * anonymous read at {@code /discovery/brand-assets/{key}} was therefore invisible to
 * {@code theDiscoverySurfaceIsReachableOnlyByTheDiscoveryRole} — the guard stayed green
 * while covering less than it appeared to. Two controllers with real paths puts both
 * routes back inside it.
 *
 * <h2>Authorisation is deliberately not decided twice</h2>
 *
 * This carries the same roles the GraphQL brand fetchers already check. It does not ask
 * "may this administrator brand this library?" in a second vocabulary: the upload produces
 * a URL and nothing else, and it is the {@code updateConsortium} / {@code updateLibrary}
 * mutation — already authorised, already audited — that decides where that URL may land.
 * An upload with no mutation after it is an orphaned object, not a brand change.
 *
 * <h2>Why the bytes are proxied instead of presigned</h2>
 *
 * The security of this feature is byte validation, and you cannot validate bytes you never
 * receive. A presigned PUT would keep image traffic off the app tier, which is the right
 * trade at a volume we do not have: a few hundred libraries, one mark each, changed about
 * once a year. If that ever changes, a presigned PUT is a different implementation of
 * {@link BrandAssetStore} and this controller is unaffected.
 *
 * <h2>The size cap is enforced before this code runs</h2>
 *
 * {@code micronaut.server.multipart.max-file-size} is pinned to the same value as
 * {@code dcb.branding.assets.max-bytes}, so an oversized upload is refused while the
 * request is being decoded rather than after {@code getBytes()} has put it all on the
 * heap. {@link BrandAssetValidator} checks the length again anyway — it is reachable from
 * places the HTTP layer is not, and a cap enforced in one place is a cap that moves when
 * somebody adds a second caller.
 */
@Controller("/brand-assets")
@Requires(beans = BrandAssetStore.class)
@Secured({ RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN, RoleNames.LIBRARY_ADMIN })
@Tag(name = "Branding API")
@Slf4j
public class BrandAssetUploadController {

	private final BrandAssetStore store;
	private final BrandAssetValidator validator;
	private final BrandAssetProperties properties;

	public BrandAssetUploadController(BrandAssetStore store, BrandAssetValidator validator,
		BrandAssetProperties properties) {

		this.store = store;
		this.validator = validator;
		this.properties = properties;
	}

	@Operation(summary = "Upload a brand image",
		description = "Accepts a PNG or JPEG identified by its magic bytes - never by its filename "
			+ "or its declared content type. SVG is refused: it is a script-capable document and "
			+ "would be served from the same origin as the patron interface. The image is re-encoded "
			+ "before it is stored, so what is served is what a decoder produced and not the bytes "
			+ "that arrived. Returns the site-relative URL to store in a brand field.")
	@Post(consumes = MULTIPART_FORM_DATA)
	@Consumes(MULTIPART_FORM_DATA)
	// Reading, decoding, and re-encoding image bytes are synchronous operations.
	@ExecuteOn(TaskExecutors.BLOCKING)
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

	/**
	 * Carry the refusal's own words to the caller.
	 *
	 * <p>Without this the response body is a bare problem detail —
	 * {@code {"type":"about:blank","status":400}} — and every sentence
	 * {@link BrandAssetValidator} writes is discarded before it reaches anyone. That
	 * matters more here than it usually does: the whole argument for validating on the
	 * server is that the client's claims about the bytes are worthless, and it is undone if
	 * the administrator is then told only that something failed. "The image is 6000x4000;
	 * the limit is 4096 pixels on either edge" is actionable; a status code is not.
	 *
	 * <p>This only sees failures raised from the route. A request rejected during multipart
	 * decoding never reaches routing, which is why the two size caps are pinned to one
	 * value in {@code application.yml} rather than left to drift apart.
	 */
	@Error(exception = HttpStatusException.class)
	public HttpResponse<Refusal> refused(HttpStatusException exception) {
		return HttpResponse.<Refusal>status(exception.getStatus())
			.body(new Refusal(exception.getMessage()));
	}

	/**
	 * What an upload returns: the URL to store, and enough to show a confirmation.
	 *
	 * <p>{@code contentType} always matches what was uploaded — the validator re-encodes in
	 * the format the image arrived as and never substitutes the container. It is returned
	 * so a form can confirm what was stored, not because it can surprise anyone.
	 */
	@Serdeable
	public record UploadedAsset(String url, String contentType, int bytes) {
	}

	/**
	 * Named {@code message} because that is what both admin forms already read, and because
	 * the field is the entire point of the type.
	 */
	@Serdeable
	public record Refusal(String message) {
	}
}
