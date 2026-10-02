package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.UserIdentifier;
import services.k_int.interaction.alma.types.WithAttr;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientPatronIdentifierTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		sut = new AlmaHostLmsClient(
			hostLms,
			clientFactory,
			mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConsortiumService.class));
	}

	@Test
	void shouldReportBarcodeIdentifiersAsThePatronsBarcodes() {
		whenUserDetailsReturn(user(CodeValuePair.builder().value("UNDRGRD").build(), List.of(
			identifier("BARCODE", "2100045"),
			identifier("INST_ID", "university-77"))));

		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByLocalId("SIS123"));

		assertThat(patron.getLocalBarcodes(), contains("2100045"));
		assertThat(patron.getLocalId(), contains("SIS123"));
	}

	@Test
	void shouldFallBackToThePrimaryIdWhenThereIsNoBarcodeIdentifier() {
		whenUserDetailsReturn(user(CodeValuePair.builder().value("UNDRGRD").build(), List.of(
			identifier("INST_ID", "university-77"))));

		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByLocalId("SIS123"));

		assertThat(patron.getLocalBarcodes(), contains("SIS123"));
	}

	@Test
	void shouldMapAUserWithNoUserGroup() {
		whenUserDetailsReturn(user(null, null));

		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByLocalId("SIS123"));

		assertThat(patron.getLocalPatronType(), is(nullValue()));
	}

	@Test
	void shouldReportTheCampusAsThePatronsHomeLibrary() {
		whenUserDetailsReturn(AlmaUser.builder()
			.primary_id("SIS123")
			.first_name("Test")
			.last_name("Patron")
			.campus_code(CodeValuePair.builder().value("NORTH").build())
			.build());

		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByLocalId("SIS123"));

		assertThat(patron.getLocalHomeLibraryCode(), is("NORTH"));
	}

	@Test
	void shouldLeaveTheHomeLibraryUnsetForAUserWithNoCampus() {
		whenUserDetailsReturn(user(null, null));

		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByLocalId("SIS123"));

		assertThat(patron.getLocalHomeLibraryCode(), is(nullValue()));
	}

	private void whenUserDetailsReturn(AlmaUser user) {
		when(almaApi.getUserDetails("SIS123")).thenReturn(Mono.just(user));
	}

	private static AlmaUser user(CodeValuePair userGroup, List<UserIdentifier> identifiers) {
		return AlmaUser.builder()
			.primary_id("SIS123")
			.first_name("Test")
			.last_name("Patron")
			.user_group(userGroup)
			.identifiers(identifiers)
			.build();
	}

	private static UserIdentifier identifier(String type, String value) {
		return UserIdentifier.builder()
			.id_type(WithAttr.builder().value(type).build())
			.value(value)
			.build();
	}
}
