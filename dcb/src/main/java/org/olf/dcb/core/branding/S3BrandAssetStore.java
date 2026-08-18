package org.olf.dcb.core.branding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The S3-API implementation of {@link BrandAssetStore} (R-17b).
 *
 * <h2>Why S3 and not a column</h2>
 *
 * The brand columns store a URL in every design that was considered, so "bytes or URL"
 * was never the question — the question was who holds the bytes. Not Postgres: a
 * background image is 200–800 KB, it would bloat every backup, and serving it would put
 * image traffic on an R2DBC request path.
 *
 * S3-API rather than a specific vendor means MinIO in {@code docker-compose.yml} and S3
 * (or R2, or Ceph) in a deployment are the same code with a different endpoint. That is
 * the property worth having: the dev path and the deployed path are not allowed to
 * diverge, because the one that only runs in production is the one nobody has exercised.
 *
 * <h2>Blocking client, deliberately, and confined</h2>
 *
 * {@code S3Client} is synchronous. Every call here is wrapped and pushed to the bounded
 * elastic scheduler, so a slow bucket costs a worker thread and never an event loop
 * thread. The async client would avoid the wrap and cost a second HTTP stack in the
 * image; at a few hundred uploads a year, that is the wrong trade.
 */
@Singleton
@Requires(beans = S3Client.class)
@Requires(property = "dcb.branding.assets.bucket", notEquals = "")
@Slf4j
public class S3BrandAssetStore implements BrandAssetStore {

	private final S3Client s3;
	private final String bucket;
	private final String prefix;

	public S3BrandAssetStore(S3Client s3, BrandAssetProperties properties) {
		this.s3 = s3;
		this.bucket = properties.getBucket();
		this.prefix = properties.getPrefix();
	}

	@Override
	public Mono<String> put(BrandAsset asset) {
		return Mono.fromCallable(() -> {
				final var key = keyFor(asset);

				s3.putObject(PutObjectRequest.builder()
						.bucket(bucket)
						.key(prefix + key)
						.contentType(asset.contentType())
						// Belt and braces with the serving route's own header. An object
						// that some other path serves directly must still not be sniffed
						// into something executable.
						.metadata(java.util.Map.of("x-content-type-options", "nosniff"))
						.build(),
					RequestBody.fromBytes(asset.bytes()));

				log.info("Stored brand asset {} ({} bytes, {})", key, asset.size(), asset.contentType());
				return key;
			})
			.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Mono<BrandAsset> get(String key) {
		return Mono.fromCallable(() -> {
				final ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(
					GetObjectRequest.builder().bucket(bucket).key(prefix + key).build());

				return new BrandAsset(object.response().contentType(), object.asByteArray());
			})
			// A missing key is a 404 at the edge of the system, not an exception in the
			// middle of it. Anything else stays an error: "the bucket is unreachable"
			// and "there is no such logo" must not render as the same page.
			.onErrorResume(NoSuchKeyException.class, e -> Mono.empty())
			.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Mono<Void> delete(String key) {
		return Mono.fromRunnable(() ->
				s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(prefix + key).build()))
			.subscribeOn(Schedulers.boundedElastic())
			.doOnError(e -> log.warn("Could not remove replaced brand asset {}: {}", key, e.getMessage()))
			// Best effort, by design. An administrator whose new logo is live must not be
			// shown an error because the old object survived.
			.onErrorResume(e -> Mono.empty())
			.then();
	}

	/**
	 * The SHA-256 of the bytes, plus the extension for the media type they were stored
	 * as. Content-addressed so the served URL can be immutable and cached forever, and so
	 * re-uploading the identical file is idempotent rather than a second object.
	 */
	static String keyFor(BrandAsset asset) {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is required of every JVM. If it is absent the platform is broken in
			// a way no fallback here would survive.
			throw new IllegalStateException("SHA-256 unavailable", e);
		}

		return HexFormat.of().formatHex(digest.digest(asset.bytes()))
			+ BrandAssetValidator.extensionFor(asset.contentType());
	}
}
