package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Finalisation deletes the virtual item and record. An NCIP responder set to delete an item on
 * check-in may have done it already: gone is the outcome finalisation wanted.
 */
@MockServerMicronautTest
class KohaDeleteVirtualRecordsTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-delete-virtual";
	private static final String KOHA_HOST = "fake-koha-delete-virtual";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldCountAVirtualItemAlreadyDeletedAsDeleted() {
		koha.mockDeleteItemGone("55");

		assertThat(client.deleteItem(DeleteCommand.builder().itemId("55").build()).block(), is("OK"));
	}

	@Test
	void shouldCountAVirtualRecordAlreadyDeletedAsDeleted() {
		koha.mockDeleteBibGone("4001");

		assertThat(client.deleteBib("4001").block(), is("OK"));
	}
}
