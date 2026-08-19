package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * The caching behaviour of the store, which the API tests cannot see because they replace
 * the whole store with an in-memory one.
 *
 * <p>The point of the cache is a number — how many times an anonymous patron request
 * reaches object storage — so the assertions here count calls rather than describe
 * intentions.
 */
class S3BrandAssetStoreTests {

	private static final String KEY = "a".repeat(64) + ".png";
	private static final byte[] BYTES = "not really a png".getBytes(StandardCharsets.UTF_8);

	private S3Client s3;
	private S3BrandAssetStore store;

	@BeforeEach
	void setUp() {
		s3 = mock(S3Client.class);

		final var properties = new BrandAssetProperties();
		properties.setBucket("a-bucket");
		properties.setPrefix("brand/");

		store = new S3BrandAssetStore(s3, properties);
	}

	/**
	 * The whole reason the cache exists. This route is anonymous and is hit on first paint
	 * of every patron page with a cold browser cache; without this it is one billed
	 * object-storage GET per page load across 500 libraries.
	 */
	@Test
	void aSecondReadOfTheSameAssetDoesNotReachObjectStorage() {
		givenTheBucketHolds(KEY);

		final var first = store.get(KEY).block();
		final var second = store.get(KEY).block();

		assertThat(first, is(notNullValue()));
		assertThat(second, is(notNullValue()));
		assertThat(second.bytes(), is(BYTES));

		verify(s3, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
	}

	/**
	 * A miss is not cached. Caching "there is no such key" would leave an asset invisible
	 * for the whole TTL if somebody asked for it a second before it was uploaded, and an
	 * absent object is already the cheap answer.
	 */
	@Test
	void aMissIsNotRemembered() {
		when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenThrow(NoSuchKeyException.builder().message("nope").build());

		assertThat(store.get(KEY).block(), is(nullValue()));
		assertThat(store.get(KEY).block(), is(nullValue()));

		verify(s3, times(2)).getObjectAsBytes(any(GetObjectRequest.class));
	}

	/**
	 * A deleted object must stop being served immediately, not at the end of the TTL. The
	 * eviction is unconditional for that reason: dropping an entry we could have kept is
	 * the cheap mistake, serving one that no longer exists is not.
	 */
	@Test
	void deletingAnAssetStopsItBeingServedFromCache() {
		givenTheBucketHolds(KEY);

		store.get(KEY).block();
		store.delete(KEY).block();
		store.get(KEY).block();

		verify(s3, times(2)).getObjectAsBytes(any(GetObjectRequest.class));
	}

	@SuppressWarnings("unchecked")
	private void givenTheBucketHolds(String key) {
		final ResponseBytes<GetObjectResponse> object = mock(ResponseBytes.class);

		when(object.response()).thenReturn(GetObjectResponse.builder().contentType("image/png").build());
		when(object.asByteArray()).thenReturn(BYTES);

		when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(object);
	}
}
