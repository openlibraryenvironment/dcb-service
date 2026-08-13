package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDate;

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
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.CodeValuePair;

/**
 * The preflight check can only reject an expired patron if the client actually
 * maps Alma's expiry_date onto Patron.expiryDate.
 */
@TestInstance(PER_CLASS)
class AlmaHostLmsClientPatronExpiryTests {

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
	void shouldMapAlmaExpiryDateOntoPatron() {
		// Arrange
		whenUserDetailsReturns(almaUser("2026-04-29Z", "ACTIVE"));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getExpiryDate(),
			is(Timestamp.valueOf(LocalDate.of(2026, 4, 29).atStartOfDay())));
	}

	@Test
	void shouldMapAlmaExpiryDateWhenSentAsAFullInstant() {
		// Arrange
		whenUserDetailsReturns(almaUser("2026-04-29T00:00:00Z", "ACTIVE"));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getExpiryDate(),
			is(Timestamp.valueOf(LocalDate.of(2026, 4, 29).atStartOfDay())));
	}

	@Test
	void shouldMapPatronWithoutExpiryDateWhenAlmaSendsAnUnparseableDate() {
		// Arrange
		whenUserDetailsReturns(almaUser("04/29/2026", "ACTIVE"));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getExpiryDate(), is(nullValue()));
	}

	@Test
	void shouldMapPatronWithoutExpiryDateWhenAlmaSendsNone() {
		// Arrange
		whenUserDetailsReturns(almaUser(null, "ACTIVE"));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getExpiryDate(), is(nullValue()));
	}

	@Test
	void shouldMarkAnInactiveAlmaPatronAsInactive() {
		// Arrange
		whenUserDetailsReturns(almaUser("2026-04-29Z", "INACTIVE"));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getIsActive(), is(false));
		assertThat(patron.getIsDeleted(), is(false));
	}

	@Test
	void shouldMarkADeletedAlmaPatronAsDeleted() {
		// Arrange
		whenUserDetailsReturns(almaUser("2026-04-29Z", "DELETED"));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getIsDeleted(), is(true));
		assertThat(patron.getIsActive(), is(false));
	}

	@Test
	void shouldTreatAnAlmaPatronWithNoStatusAsActive() {
		// Arrange
		whenUserDetailsReturns(almaUser("2026-04-29Z", null));

		// Act
		final var patron = PublisherUtils.singleValueFrom(sut.getPatronByIdentifier("patron-id"));

		// Assert
		assertThat(patron.getIsActive(), is(true));
		assertThat(patron.getIsDeleted(), is(false));
	}

	private void whenUserDetailsReturns(AlmaUser almaUser) {
		when(almaApi.getUserDetails("patron-id")).thenReturn(Mono.just(almaUser));
	}

	private static AlmaUser almaUser(String expiryDate, String status) {
		return AlmaUser.builder()
			.primary_id("patron-id")
			.first_name("Test")
			.last_name("Patron")
			.user_group(CodeValuePair.builder().value("UNDRGRD").build())
			.status(status != null ? CodeValuePair.builder().value(status).build() : null)
			.expirationDate(expiryDate)
			.build();
	}
}
