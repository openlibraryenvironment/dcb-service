package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorList;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;

/**
 * What a supplier hold says when Alma refuses it.
 * <p>
 * 401129 names neither the pickup library nor the user group, which are the two things that
 * decide it, and an operator reading "No items can fulfill the submitted request" against an
 * item that is on the shelf has nowhere to go next.
 */
@TestInstance(PER_CLASS)
class AlmaSupplierHoldTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of("sharing-library-code", "RES_SHARE"));

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		client = new AlmaHostLmsClient(hostLms, mock(HttpClient.class), clientFactory,
			mock(ReferenceValueMappingService.class), mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), mock(ConversionService.class),
			mock(LocationService.class), mock(HostLmsService.class), mock(ConsortiumService.class));
	}

	@Test
	void shouldSayWhichPickupLibraryAlmaCouldNotFulfilFor() {
		refuseWith("401129", "No items can fulfill the submitted request.");

		final var error = placeSupplierHold();

		assertThat(error, instanceOf(AlmaHostLmsClientException.class));
		assertThat(error.getMessage(), allOf(
			containsString("item-1"),
			containsString("RES_SHARE"),
			containsString("pickup location"),
			containsString("Request term of use")));
	}

	@Test
	void shouldKeepAlmasAnswerAsTheCause() {
		refuseWith("401129", "No items can fulfill the submitted request.");

		// Reactor stack traces are useless on their own; the Alma codes have to survive
		assertThat(placeSupplierHold().getCause(), instanceOf(AlmaApiException.class));
	}

	@Test
	void shouldLeaveAnyOtherAlmaFailureAlone() {
		refuseWith("401652", "General Error");

		assertThat("Only 401129 is explained; the rest must not be reshaped",
			placeSupplierHold(), instanceOf(AlmaApiException.class));
	}

	private void refuseWith(String code, String message) {
		when(almaApi.retrieveUserHoldRequestsPage(anyString(), anyInt()))
			.thenReturn(Mono.empty());

		when(almaApi.createUserRequest(anyString(), anyString(), any()))
			.thenReturn(Mono.error(almaError(code, message)));
	}

	private Throwable placeSupplierHold() {
		return client.placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters.builder()
				.localPatronId("patron-1")
				.localItemId("item-1")
				.pickupLocation(Location.builder().code("PICKUP").localId("PICKUP").build())
				.activeWorkflow("RET-STD")
				.patronRequestId("pr-1")
				.build())
			.map(request -> (Throwable) new IllegalStateException("Alma refused, but the hold succeeded"))
			.onErrorResume(error -> Mono.just(error))
			.block();
	}

	private static AlmaApiException almaError(String code, String message) {
		final var error = new AlmaError();
		error.setErrorCode(code);
		error.setErrorMessage(message);

		final var errorList = new AlmaErrorList();
		errorList.setError(List.of(error));

		final var response = new AlmaErrorResponse();
		response.setErrorsExist(true);
		response.setErrorList(errorList);

		return new AlmaApiException("POST", "/almaws/v1/users/patron-1/requests", 400, response);
	}
}
