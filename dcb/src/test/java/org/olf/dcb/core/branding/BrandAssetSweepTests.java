package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.StoredBrandAsset;
import org.olf.dcb.storage.BrandAssetRepository;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The orphan sweep, which is the reason storing images in the database is defensible at
 * all.
 *
 * <h2>What it is defending against</h2>
 *
 * An administrator uploads, receives a URL, and saves it with a SEPARATE mutation.
 * {@code BrandAssetCleanup} only ever removes an asset that was once referenced and then
 * replaced, so an upload nobody saves is orphaned the moment it lands. Without this sweep an
 * authenticated administrator can insert unbounded two-megabyte rows by uploading
 * repeatedly and saving nothing.
 *
 * <h2>One case per referencing column, deliberately</h2>
 *
 * Four columns can hold an uploaded asset. A fifth added without touching the sweep query
 * would have its images deleted out from under it, and the symptom would be a broken logo
 * on a patron page weeks later. Asserting them individually means the failure names the
 * column.
 */
@DcbTest
@TestInstance(PER_CLASS)
class BrandAssetSweepTests {

	private static final String PREFIX = "/discovery/brand-assets/";
	private static final Instant UPLOADED = Instant.parse("2026-01-01T00:00:00Z");

	@Inject
	private BrandAssetRepository assets;

	@Inject
	private ConsortiumRepository consortia;

	@Inject
	private LibraryRepository libraries;

	@BeforeEach
	void beforeEach() {
		singleValueFrom(Flux.from(consortia.queryAll())
			.concatMap(consortium -> consortia.delete(consortium.getId()))
			.then()
			.thenReturn("done"));

		singleValueFrom(Flux.from(libraries.queryAll())
			.concatMap(library -> libraries.delete(library.getId()))
			.then()
			.thenReturn("done"));

		// Assets outlive the rows that referenced them, so without this every test after
		// the first counts the previous test's orphans. Nothing is referenced at this
		// point, so a sweep far in the future drains the table using the same mechanism
		// under test rather than a second one that could disagree with it.
		sweepAt(UPLOADED.plus(Duration.ofDays(3650)));
	}

	@Test
	void anAssetNobodyReferencesIsRemoved() {
		final var key = givenAnUploadedAsset();

		assertThat(sweepAt(UPLOADED.plus(Duration.ofDays(2))), is(1L));
		assertThat(isStored(key), is(false));
	}

	/**
	 * The grace period. Between the upload and the mutation that saves the URL the asset is
	 * legitimately unreferenced, and deleting it there would remove the image the
	 * administrator is part-way through choosing.
	 */
	@Test
	void anAssetUploadedMomentsAgoIsLeftAlone() {
		final var key = givenAnUploadedAsset();

		assertThat(sweepAt(UPLOADED.plus(Duration.ofHours(1))), is(0L));
		assertThat(isStored(key), is(true));
	}

	@Test
	void anAssetReferencedByTheConsortiumLogoSurvives() {
		assertSurvivesWhenReferencedBy((consortium, url) -> consortium.setBrandLogoUrl(url));
	}

	@Test
	void anAssetReferencedByTheConsortiumHeaderIconSurvives() {
		assertSurvivesWhenReferencedBy((consortium, url) -> consortium.setBrandHeaderIconUrl(url));
	}

	@Test
	void anAssetReferencedByTheConsortiumBackgroundSurvives() {
		assertSurvivesWhenReferencedBy((consortium, url) -> consortium.setBrandBackgroundImageUrl(url));
	}

	@Test
	void anAssetReferencedByALibraryLogoSurvives() {
		final var key = givenAnUploadedAsset();

		singleValueFrom(libraries.save(Library.builder()
			.id(UUID.randomUUID())
			.agencyCode("SWEEP-LIB")
			.fullName("Sweep Test Library")
			.shortName("Sweep")
			.abbreviatedName("SWP")
			.brandLogoUrl(PREFIX + key)
			.build()));

		assertThat(sweepAt(UPLOADED.plus(Duration.ofDays(2))), is(0L));
		assertThat(isStored(key), is(true));
	}

	/** A referenced asset survives while an unreferenced one beside it does not. */
	private void assertSurvivesWhenReferencedBy(java.util.function.BiConsumer<Consortium, String> reference) {
		final var referenced = givenAnUploadedAsset();
		final var orphan = givenAnUploadedAsset();

		final var consortium = Consortium.builder()
			.id(UUID.randomUUID())
			.name("Sweep Test Consortium")
			.build();

		reference.accept(consortium, PREFIX + referenced);

		singleValueFrom(consortia.save(consortium));

		assertThat("Only the orphan goes", sweepAt(UPLOADED.plus(Duration.ofDays(2))), is(1L));
		assertThat(isStored(referenced), is(true));
		assertThat(isStored(orphan), is(false));
	}

	private String givenAnUploadedAsset() {
		final var key = UUID.randomUUID().toString().replace("-", "").repeat(2) + ".png";

		singleValueFrom(assets.upsert(key, BrandAssetValidator.PNG,
			new byte[] { 1, 2, 3 }, 3, UPLOADED));

		return key;
	}

	private long sweepAt(Instant now) {
		final var properties = new BrandAssetProperties();
		properties.setOrphanGracePeriod(Duration.ofDays(1));
		properties.setPublicPathPrefix(PREFIX);

		return singleValueFrom(new BrandAssetSweep(assets, properties,
			Clock.fixed(now, ZoneOffset.UTC)).sweep());
	}

	private boolean isStored(String key) {
		return singleValueFrom(Mono.from(assets.findById(key)).hasElement());
	}
}
