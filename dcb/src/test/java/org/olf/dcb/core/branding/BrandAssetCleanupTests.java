package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

/**
 * The orphan policy (R-17b). Content-addressed keys mean a replaced asset always leaves
 * the old object behind, so without this the bucket grows once per edit forever — and
 * nothing queries a bucket, so nobody would notice.
 */
class BrandAssetCleanupTests {

	private static final String PREFIX = "/discovery/brand-assets/";
	private static final String OLD_KEY = "a".repeat(64) + ".png";
	private static final String NEW_KEY = "b".repeat(64) + ".png";

	@Test
	void shouldDeleteTheAssetAnEditStoppedReferencing() {
		final var store = new RecordingStore();

		cleanup(store).removeReplaced(List.of(
			change(PREFIX + OLD_KEY, PREFIX + NEW_KEY))).block();

		assertThat(store.deleted, contains(OLD_KEY));
	}

	@Test
	void shouldDeleteTheAssetWhenTheFieldIsClearedRatherThanReplaced() {
		final var store = new RecordingStore();

		cleanup(store).removeReplaced(List.of(change(PREFIX + OLD_KEY, null))).block();

		assertThat(store.deleted, contains(OLD_KEY));
	}

	/**
	 * A consortium's CDN is not ours to delete from, and the same URL may be in use by
	 * things we know nothing about.
	 */
	@Test
	void shouldNeverTouchAnExternalUrl() {
		final var store = new RecordingStore();

		cleanup(store).removeReplaced(List.of(
			change("https://cdn.example.org/logo.png", PREFIX + NEW_KEY))).block();

		assertThat(store.deleted, is(empty()));
	}

	@Test
	void shouldDoNothingWhenTheValueIsUnchanged() {
		final var store = new RecordingStore();

		cleanup(store).removeReplaced(List.of(
			change(PREFIX + OLD_KEY, PREFIX + OLD_KEY))).block();

		assertThat(store.deleted, is(empty()));
	}

	/**
	 * The case that makes this more than a one-line delete. A consortium that points its
	 * header icon at the image its logo already used must not have the object deleted out
	 * from under the field that is still using it — and because keys are content
	 * addressed, "the same image" really is the same object.
	 */
	@Test
	void shouldNotDeleteAnAssetAnotherFieldInTheSameEditNowUses() {
		final var store = new RecordingStore();

		cleanup(store).removeReplaced(List.of(
			// the logo moves off the old asset...
			change(PREFIX + OLD_KEY, PREFIX + NEW_KEY),
			// ...and the header icon moves onto it
			change(null, PREFIX + OLD_KEY))).block();

		assertThat(store.deleted, is(empty()));
	}

	/** No bucket configured means no store bean, and this must still be a no-op. */
	@Test
	void shouldDoNothingWhenNoStoreIsConfigured() {
		final var properties = new BrandAssetProperties();

		new BrandAssetCleanup(Optional.empty(), properties)
			.removeReplaced(List.of(change(PREFIX + OLD_KEY, null)))
			.block();
	}

	private static BrandAssetCleanup.Change change(String previous, String current) {
		return new BrandAssetCleanup.Change(previous, current);
	}

	private static BrandAssetCleanup cleanup(BrandAssetStore store) {
		return new BrandAssetCleanup(Optional.of(store), new BrandAssetProperties());
	}

	/** Four lines, and the reason {@link BrandAssetStore} is an interface. */
	private static final class RecordingStore implements BrandAssetStore {

		private final List<String> deleted = new ArrayList<>();

		@Override
		public Mono<String> put(BrandAsset asset) {
			return Mono.just(asset.key());
		}

		@Override
		public Mono<BrandAsset> get(String key) {
			return Mono.empty();
		}

		@Override
		public Mono<Void> delete(String key) {
			deleted.add(key);
			return Mono.empty();
		}
	}
}
