package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.AlmaUserBlock;
import services.k_int.interaction.alma.types.CodeValuePair;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientUpdatePatronTests {
	private static final String PATH = "/almaws/v1/users/patron-id";
	private static final Map<String, Object> OVERRIDE_USER_GROUP = Map.of("override", "user_group");

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
			mock(HttpClient.class),
			clientFactory,
			mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConversionService.class),
			mock(LocationService.class),
			mock(HostLmsService.class),
			mock(ConsortiumService.class));
	}

	@Test
	void shouldChangeOnlyTheUserGroupAndNameItInOverride() {
		final var existing = AlmaUser.builder()
			.primary_id("patron-id")
			.first_name("Test")
			.last_name("Patron")
			.user_group(CodeValuePair.builder().value("UNDRGRD").build())
			.expirationDate("2027-01-31Z")
			.user_blocks(List.of(AlmaUserBlock.builder().block_status("ACTIVE").build()))
			.build();

		when(almaApi.getUserDetails("patron-id")).thenReturn(Mono.just(existing));
		when(almaApi.put(eq(PATH), any(), eq(AlmaUser.class), eq(OVERRIDE_USER_GROUP)))
			.thenReturn(Mono.just(existing));

		PublisherUtils.singleValueFrom(sut.updatePatron("patron-id", "GRAD"));

		final var body = ArgumentCaptor.forClass(Object.class);
		verify(almaApi).put(eq(PATH), body.capture(), eq(AlmaUser.class), eq(OVERRIDE_USER_GROUP));

		final var sent = (AlmaUser) body.getValue();

		assertThat(sent.getUser_group().getValue(), is("GRAD"));
		assertThat(sent.getExpirationDate(), is("2027-01-31Z"));
		assertThat(sent.getUser_blocks(), hasSize(1));
		assertThat(sent.getFirst_name(), is("Test"));
	}
}
