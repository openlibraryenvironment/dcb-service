package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsRenewal;
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
import services.k_int.interaction.alma.types.items.AlmaItemLoan;
import services.k_int.interaction.alma.types.items.AlmaItemLoans;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientRenewTests {
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
	void shouldRenewALoanBeyondTheFirstPageOfLoans() {
		when(almaApi.retrieveUserLoansPage("patron-id", 0))
			.thenReturn(Mono.just(loans(IntStream.range(0, 100))));
		when(almaApi.retrieveUserLoansPage("patron-id", 100))
			.thenReturn(Mono.just(loans(IntStream.range(100, 130))));
		when(almaApi.renewLoan("patron-id", "loan-121"))
			.thenReturn(Mono.just(AlmaItemLoan.builder().loanId("loan-121").build()));

		PublisherUtils.singleValueFrom(sut.renew(renewal("item-121")));

		verify(almaApi).renewLoan("patron-id", "loan-121");
	}

	@Test
	void shouldReportAMissingLoanWhenThePatronHasNoLoans() {
		when(almaApi.retrieveUserLoansPage("patron-id", 0))
			.thenReturn(Mono.just(AlmaItemLoans.builder().recordCount(0).build()));

		final var error = assertThrows(IllegalStateException.class,
			() -> sut.renew(renewal("item-121")).block());

		assertThat(error.getMessage(), containsString("Could not find a matching loan"));
	}

	private static AlmaItemLoans loans(IntStream numbers) {
		final List<AlmaItemLoan> loans = numbers
			.mapToObj(n -> AlmaItemLoan.builder()
				.loanId("loan-" + n)
				.itemId("item-" + n)
				.build())
			.toList();

		return AlmaItemLoans.builder().recordCount(130).loans(loans).build();
	}

	private static HostLmsRenewal renewal(String itemId) {
		return HostLmsRenewal.builder()
			.localPatronId("patron-id")
			.localItemId(itemId)
			.build();
	}
}
