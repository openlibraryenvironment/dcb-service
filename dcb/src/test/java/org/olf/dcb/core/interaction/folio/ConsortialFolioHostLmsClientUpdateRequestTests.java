package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.ProblemMatchers.hasTitle;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem;
import org.olf.dcb.core.interaction.shared.MissingParameterException;
import org.olf.dcb.core.interaction.shared.NoItemTypeMappingFoundException;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientUpdateRequestTests {
	private static final String HOST_LMS_CODE = "folio-update-request-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockFolioFixture mockFolioFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);
	}

	@Test
	void shouldUpdateRequestSuccessfully() {
		// Arrange
    referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			HOST_LMS_CODE, "canonical-item-type", "local-item-type");

		final String transactionId = UUID.randomUUID().toString();

		mockFolioFixture.mockUpdateTransaction(transactionId);

		// Act
		final var newLocalItemBarcode = "62543635";

		// This is intended to mimic what the broader workflow code will pass
		// to this method when
		final var newSupplyingAgencyCode = "new-supplying-agency-code";

		final var localRequest = LocalRequest.builder()
			.localId(transactionId)
			.requestedItemId("362564553")
			.requestedItemBarcode(newLocalItemBarcode)
			.supplyingHostLmsCode("new-supplying-host-lms-code")
			.supplyingAgencyCode(newSupplyingAgencyCode)
			.canonicalItemType("canonical-item-type")
			.build();

		final var passedBackLocalRequest = updatePatronRequest(localRequest);

		// Assert
		assertThat("Should be same local request as provided",
			passedBackLocalRequest, is(localRequest));

		// Check the data sent to the API was correct
		mockFolioFixture.verifyUpdateTransaction(transactionId,
			UpdateTransactionRequest.builder()
				.item(UpdateTransactionRequest.Item.builder()
					.barcode(newLocalItemBarcode)
					.materialType("local-item-type")
					.lendingLibraryCode(newSupplyingAgencyCode)
					.build())
				.build());
	}

	@Test
	void shouldFailWhenTransactionCreationReturnsNotFoundError() {
		// Arrange
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			HOST_LMS_CODE, "canonical-item-type", "local-item-type");

		final var transactionId = UUID.randomUUID().toString();

		mockFolioFixture.mockUpdateTransaction(transactionId, response()
			.withStatusCode(404)
			.withBody(json(ConsortialFolioHostLmsClient.ValidationError.builder()
				.errors(List.of(ConsortialFolioHostLmsClient.ValidationError.Error.builder()
					.message("Transaction could not be found")
					.type("-1")
					.code("NOT_FOUND_ERROR")
					.build()))
				.build())));

		// Act
		final var localRequest = LocalRequest.builder()
			.localId(transactionId)
			.requestedItemId("127576464")
			.requestedItemBarcode("264635675")
			.supplyingHostLmsCode("new-supplying-host-lms-code")
			.supplyingAgencyCode("new-supplying-agency-code")
			.canonicalItemType("canonical-item-type")
			.build();

		final var error = assertThrows(UnexpectedHttpResponseProblem.class,
			() -> updatePatronRequest(localRequest));

		// Assert
		assertThat(error, allOf(
			hasTitle("Unexpected response from Host LMS: \"%s\"".formatted(HOST_LMS_CODE)),
			hasResponseStatusCode(404)
		));
	}

	@Test
	void shouldFailWhenItemBarcodeIsNull() {
		// Arrange
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			HOST_LMS_CODE, "canonical-item-type", "local-item-type");

		final String transactionId = UUID.randomUUID().toString();

		// Act
		final var localRequest = LocalRequest.builder()
			.localId(transactionId)
			.requestedItemId("78275266")
			.requestedItemBarcode(null)
			.supplyingHostLmsCode("new-supplying-host-lms-code")
			.supplyingAgencyCode("new-supplying-agency-code")
			.canonicalItemType("canonical-item-type")
			.build();

		final var error = assertThrows(MissingParameterException.class,
			() -> updatePatronRequest(localRequest));

		// Assert
		assertThat(error, allOf(
			hasMessage("requested item barcode is missing.")
		));

		mockFolioFixture.verifyNoUpdateTransaction(transactionId);
	}

	@Test
	void shouldFailWhenItemTypeCannotBeMappedToLocalValue() {
		// Arrange
		final String transactionId = UUID.randomUUID().toString();

		mockFolioFixture.mockUpdateTransaction(transactionId);

		// Act
		final var unmappedItemType = "unmapped-canonical-item-type";

		final var localRequest = LocalRequest.builder()
			.localId(transactionId)
			.requestedItemId("21757253")
			.requestedItemBarcode("4665353")
			.supplyingHostLmsCode("new-supplying-host-lms-code")
			.supplyingAgencyCode("new-supplying-agency-code")
			.canonicalItemType(unmappedItemType)
			.build();

		final var error = assertThrows(NoItemTypeMappingFoundException.class,
			() -> updatePatronRequest(localRequest));

		// Assert
		assertThat(error, allOf(
			hasMessage("Unable to map canonical item type \"%s\" to a item type on Host LMS: \"%s\""
				.formatted(unmappedItemType, HOST_LMS_CODE))
		));

		mockFolioFixture.verifyNoUpdateTransaction(transactionId);
	}

	private LocalRequest updatePatronRequest(LocalRequest localRequest) {
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		return singleValueFrom(client.updateHoldRequest(localRequest));
	}
}
