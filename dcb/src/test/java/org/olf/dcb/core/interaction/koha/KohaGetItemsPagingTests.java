package org.olf.dcb.core.interaction.koha;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Availability for a title with many copies. Koha answers RESTdefaultPageSize rows (20 by
 * default) unless asked for a page, so a title held at every branch of a shared Koha was
 * reported with twenty copies. The read now pages, and stops at a bound.
 */
@MockServerMicronautTest
class KohaGetItemsPagingTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String BIBLIO_ID = "4001";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
		mockKohaFixture.mockNoActiveHolds();
	}

	@Test
	void shouldReadEveryPageOfATitleHeldAtManyBranches() {
		mockKohaFixture.mockItemsPage(BIBLIO_ID, 1, items(1, 100));
		mockKohaFixture.mockItemsPage(BIBLIO_ID, 2, items(101, 200));
		mockKohaFixture.mockItemsPage(BIBLIO_ID, 3, items(201, 230));

		final var items = client.getItems(bibRecord()).block();

		assertThat(items, hasSize(230));
	}

	@Test
	void shouldStopAtAShortPageWithoutAskingForAnother() {
		mockKohaFixture.mockItemsPage(BIBLIO_ID, 1, items(1, 3));

		assertThat(client.getItems(bibRecord()).block(), hasSize(3));
	}

	@Test
	void shouldStopAtAnEmptyPage() {
		mockKohaFixture.mockItemsPage(BIBLIO_ID, 1, items(1, 100));
		mockKohaFixture.mockItemsPage(BIBLIO_ID, 2);

		assertThat(client.getItems(bibRecord()).block(), hasSize(100));
	}

	@Test
	void shouldStopAtTheBoundRatherThanReadATitleWithoutEnd() {
		for (int page = 1; page <= 6; page++) {
			mockKohaFixture.mockItemsPage(BIBLIO_ID, page, items((page - 1) * 100 + 1, page * 100));
		}

		assertThat(client.getItems(bibRecord()).block(), hasSize(500));
	}

	private static KohaItem[] items(int from, int to) {
		return IntStream.rangeClosed(from, to)
			.mapToObj(number -> KohaItem.builder()
				.itemId((long) number)
				.biblioId(Long.valueOf(BIBLIO_ID))
				.externalId("barcode-" + number)
				.homeLibraryId("BRANCH-" + (number % 60))
				.notForLoanStatus(0)
				.build())
			.toArray(KohaItem[]::new);
	}

	private static BibRecord bibRecord() {
		return BibRecord.builder()
			.id(randomUUID())
			.sourceRecordId(BIBLIO_ID)
			.build();
	}
}
