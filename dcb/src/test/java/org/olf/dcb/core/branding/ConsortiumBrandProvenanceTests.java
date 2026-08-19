package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.DataChangeLog;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.DataChangeLogRepository;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/**
 * Where "who changed this brand image" lives, now that it is not on the consortium row.
 *
 * V9_0_004 dropped {@code header_image_uploader} and its email twin, and their
 * {@code about_} counterparts: a member of staff's name and address, stored beside the
 * image on a row every authenticated principal could read, with no retention rule. The
 * replacement is the audit trigger that already covers every other change to this table.
 *
 * This test is what makes that a claim rather than an assertion in a commit message.
 */
@DcbTest
@TestInstance(PER_CLASS)
class ConsortiumBrandProvenanceTests {

	@Inject
	private ConsortiumFixture consortiumFixture;

	@Inject
	private ConsortiumRepository consortiumRepository;

	@Inject
	private DataChangeLogRepository dataChangeLogRepository;

	@BeforeEach
	void beforeEach() {
		consortiumFixture.deleteAll();
	}

	@Test
	@DisplayName("changing a brand image records who did it, and the before and after")
	void changingABrandImageRecordsWhoDidItAndTheBeforeAndAfter() {
		// Arrange
		final var consortium = consortiumFixture.createConsortiumWithBrandImages("MOBIUS",
			"https://example.com/mobius-logo.png",
			"https://example.com/mobius-icon.png",
			null);

		// Act — exactly what UpdateConsortiumDataFetcher does to the entity: set the
		// image, and set lastEditedBy from the verified claim.
		singleValueFrom(consortiumRepository.update(consortium
			.setBrandHeaderIconUrl("https://example.com/mobius-icon-v2.png")
			.setLastEditedBy("a.librarian")
			.setReason("New mark")
			.setChangeCategory("Branding")));

		// Assert
		final var changes = logEntriesFor(consortium.getId());

		assertThat("the trigger logged the edit", changes, hasItem(allOf(
			containsString("brand_header_icon_url"),
			containsString("mobius-icon-v2.png"),
			containsString("mobius-icon.png"))));

		assertThat("the actor is recorded", actorsFor(consortium.getId()), hasItem("a.librarian"));
	}

	@Test
	@DisplayName("no brand image change puts an email address on the consortium row")
	void noBrandImageChangePutsAnEmailAddressOnTheConsortiumRow() {
		// The uploader columns are gone from the entity, so this is not a test that they
		// are unused — it is a test that nothing put an address back into what the audit
		// trail now carries in their place. The trigger serialises whatever changed, so a
		// field added to Consortium later would appear here without anybody deciding to.
		final var consortium = consortiumFixture.createConsortiumWithBrandImages("MOBIUS",
			"https://example.com/mobius-logo.png", null, null);

		singleValueFrom(consortiumRepository.update(consortium
			.setBrandLogoUrl("https://example.com/mobius-logo-v2.png")
			.setLastEditedBy("a.librarian")));

		for (var entry : logEntriesFor(consortium.getId())) {
			assertThat("the audit entry carries no address", entry, not(containsString("@")));
		}
	}

	@Test
	@DisplayName("the audit entry names the consortium, so provenance is findable")
	void theAuditEntryNamesTheConsortium() {
		// Without this the trail exists but nothing can ask "what happened to THIS
		// consortium's mark" - which is the only question the uploader columns answered.
		final var consortium = consortiumFixture.createConsortiumWithBrandImages("MOBIUS",
			null, "https://example.com/mobius-icon.png", null);

		final var entries = entriesFor(consortium.getId());

		assertThat(entries.isEmpty(), is(false));
		entries.forEach(entry ->
			assertThat(entry.getEntityType(), is("consortium")));
	}

	/**
	 * Collected as entities and projected in plain Java rather than through
	 * {@code Flux.map}, which rejects a null: a data_change_log row is allowed to carry a
	 * null in any of these columns and the test must observe that, not die of it.
	 */
	private List<DataChangeLog> entriesFor(UUID consortiumId) {
		return Flux.from(dataChangeLogRepository.queryAll())
			.filter(entry -> consortiumId.equals(entry.getEntityId()))
			.collectList()
			.block();
	}

	private List<String> logEntriesFor(UUID consortiumId) {
		return entriesFor(consortiumId).stream()
			.map(DataChangeLog::getChanges)
			.filter(Objects::nonNull)
			.toList();
	}

	private List<String> actorsFor(UUID consortiumId) {
		return entriesFor(consortiumId).stream()
			.map(DataChangeLog::getLastEditedBy)
			.filter(Objects::nonNull)
			.toList();
	}
}
