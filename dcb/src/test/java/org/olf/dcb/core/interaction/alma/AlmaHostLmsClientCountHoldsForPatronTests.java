package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientCountHoldsForPatronTests {
	private static final String LOCAL_PATRON_ID = "5493275";

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
			mock(ConsortiumService.class)
		);
	}

	@Test
	void shouldUseTotalRecordCountAsHoldCount() {
		// total_record_count spans every page, so it must be preferred over the
		// size of the (possibly truncated) requests list

		// Arrange
		when(almaApi.retrieveUserHoldRequests(LOCAL_PATRON_ID))
			.thenReturn(Mono.just(AlmaRequests.builder()
				.recordCount(15)
				.requests(List.of())
				.build()));

		// Act
		final var count = PublisherUtils.singleValueFrom(
			sut.countHoldsForPatron(LOCAL_PATRON_ID));

		// Assert
		assertThat(count, is(15));
	}

	@Test
	void shouldReturnEmptyHoldCountWhenRequestsCannotBeFetched() {
		// An unknown count is not a count of zero - the preflight check treats
		// empty as "cannot judge" rather than "under the limit"

		// Arrange
		when(almaApi.retrieveUserHoldRequests(LOCAL_PATRON_ID))
			.thenReturn(Mono.error(new RuntimeException("Alma is unavailable")));

		// Act
		final var count = PublisherUtils.singleValueFrom(
			sut.countHoldsForPatron(LOCAL_PATRON_ID));

		// Assert
		assertThat(count, is(nullValue()));
	}

	@Test
	void shouldReturnEmptyHoldCountWhenResponseHasNoTotalRecordCount() {
		// Arrange
		when(almaApi.retrieveUserHoldRequests(LOCAL_PATRON_ID))
			.thenReturn(Mono.just(AlmaRequests.builder()
				.recordCount(null)
				.requests(List.of())
				.build()));

		// Act
		final var count = PublisherUtils.singleValueFrom(
			sut.countHoldsForPatron(LOCAL_PATRON_ID));

		// Assert
		assertThat(count, is(nullValue()));
	}
}
