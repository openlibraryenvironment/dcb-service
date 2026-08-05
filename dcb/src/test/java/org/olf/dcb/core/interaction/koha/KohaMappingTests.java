package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronsList;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;

/**
 * How Koha records become DCB records.
 * <p>
 * On a shared Koha the branch is the only thing that tells one participating
 * library from another, so anything that loses it collapses every co-tenant onto
 * one agency.
 */
@TestInstance(PER_CLASS)
class KohaMappingTests {
	private KohaApiClient apiClient;
	private KohaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("KOHA");
		when(hostLms.getClientConfig()).thenReturn(Map.<String, Object>of(
			"api-url", "https://koha.example.com",
			"client_id", "any-id",
			"client_secret", "any-secret"));

		apiClient = mock(KohaApiClient.class);

		final var clientFactory = mock(KohaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(apiClient);

		client = new KohaHostLmsClient(hostLms,
			mock(ReferenceValueMappingService.class), clientFactory,
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class));
	}

	@Test
	void shouldMapThePatronsHomeLibrary() {
		// library_id was modelled and used when creating a patron, but never read back.
		// Every Koha patron therefore arrived with no home library code, fell through to
		// the default agency, and on a 60-library Koha every one of them belonged to
		// whichever library happened to be configured there.
		givenPatron(KohaPatron.builder()
			.patronId(1234L)
			.cardnumber("6747664")
			.firstname("Ada")
			.surname("Lovelace")
			.categoryId("ADULT")
			.libraryId("BRANCH-NORTH")
			.build());

		final var patron = client.getPatronByIdentifier("6747664").block();

		assertThat(patron.getLocalHomeLibraryCode(), is("BRANCH-NORTH"));
	}

	@Test
	void shouldTolerateAPatronWithNoHomeLibrary() {
		givenPatron(KohaPatron.builder()
			.patronId(1234L)
			.cardnumber("6747664")
			.firstname("Ada")
			.surname("Lovelace")
			.categoryId("ADULT")
			.build());

		final var patron = client.getPatronByIdentifier("6747664").block();

		assertThat(patron.getLocalHomeLibraryCode(), is((String) null));
		assertThat(patron.getLocalPatronType(), is("ADULT"));
	}

	private void givenPatron(KohaPatron kohaPatron) {
		final var list = new KohaPatronsList();
		list.add(kohaPatron);

		when(apiClient.getPatronByCardnumber(kohaPatron.getCardnumber()))
			.thenReturn(Mono.just(list));
	}
}
