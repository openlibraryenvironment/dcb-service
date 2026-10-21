package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Koha has no item status to write, so the workflow's status changes are acknowledged
 * without a call. Until this answered, every Koha borrower request failed at
 * HandleSupplierInTransit with a not-implemented error.
 */
@MockServerMicronautTest
class KohaUpdateItemStatusTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockServerClient mockServerClient;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@ParameterizedTest
	@EnumSource(CanonicalItemState.class)
	void shouldAcknowledgeEveryStateWithoutCallingKoha(CanonicalItemState state) {
		final var result = client.updateItemStatus(HostLmsItem.builder()
				.localId("55")
				.localRequestId("77")
				.build(), state)
			.block();

		assertThat(result, is("OK"));

		// Not even a token: nothing was sent
		mockServerClient.verifyZeroInteractions();
	}
}
