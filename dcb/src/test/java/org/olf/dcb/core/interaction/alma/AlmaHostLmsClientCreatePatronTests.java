package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.UserIdentifier;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientCreatePatronTests {
	private AlmaApiClient almaApi;
	private HostLms hostLms;

	@BeforeEach
	void setUp() {
		hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");

		almaApi = mock(AlmaApiClient.class);
		when(almaApi.createUser(any())).thenReturn(Mono.just(AlmaUser.builder().primary_id("VP1").build()));
	}

	@Test
	void shouldPrefixTheBarcodeSoItCannotTakeAnIdentifierThisAlmasOwnUsersNeed() {
		final var identifiers = identifiersSentWhenCreating(Map.of());

		assertThat(identifiers, containsInAnyOrder("BARCODE=DCB-2100045", "INST_ID=77@HOME"));
	}

	@Test
	void shouldUseTheConfiguredPrefix() {
		final var identifiers = identifiersSentWhenCreating(
			Map.of("virtual-patron-barcode-prefix", "CONSORTIUM-"));

		assertThat(identifiers, containsInAnyOrder("BARCODE=CONSORTIUM-2100045", "INST_ID=77@HOME"));
	}

	@Test
	void shouldSendTheBareBarcodeWhenThePrefixIsTurnedOff() {
		final var identifiers = identifiersSentWhenCreating(
			Map.of("virtual-patron-barcode-prefix", ""));

		assertThat(identifiers, containsInAnyOrder("BARCODE=2100045", "INST_ID=77@HOME"));
	}

	private List<String> identifiersSentWhenCreating(Map<String, Object> config) {
		when(hostLms.getClientConfig()).thenReturn(config);

		client().createPatron(Patron.builder()
			.localBarcodes(List.of("2100045"))
			.uniqueIds(List.of("77@HOME"))
			.localPatronType("UNDRGRD")
			.build()).block();

		final var sent = ArgumentCaptor.forClass(AlmaUser.class);
		verify(almaApi).createUser(sent.capture());

		return sent.getValue().getIdentifiers().stream()
			.map(AlmaHostLmsClientCreatePatronTests::describe)
			.toList();
	}

	private static String describe(UserIdentifier identifier) {
		return identifier.getId_type().getValue() + "=" + identifier.getValue();
	}

	private AlmaHostLmsClient client() {
		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		final var consortiumService = mock(ConsortiumService.class);
		when(consortiumService.isEnabled(any())).thenReturn(Mono.just(false));

		return new AlmaHostLmsClient(
			hostLms,
			mock(HttpClient.class),
			clientFactory,
			mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConversionService.class),
			mock(LocationService.class),
			mock(HostLmsService.class),
			consortiumService);
	}
}
