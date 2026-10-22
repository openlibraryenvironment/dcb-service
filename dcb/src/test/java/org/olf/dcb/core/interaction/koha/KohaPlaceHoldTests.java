package org.olf.dcb.core.interaction.koha;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Which holds override a patron's own blocks. The supplier and pickup holds are for DCB's
 * virtual patron, whose expiry or debt must not stop a consortium loan; the borrower and
 * local holds are for a real patron, whose library's blocks stand.
 */
@MockServerMicronautTest
class KohaPlaceHoldTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldOverrideTheVirtualPatronsBlocksOnASupplierHold() {
		mockKohaFixture.mockPlaceHold(placedHold());

		final var request = client.placeHoldRequestAtSupplyingAgency(parameters()).block();

		assertThat(request.getLocalId(), is("501"));
		assertThat("An item-level hold is confirmed from the start",
			request.getLocalStatus(), is(HOLD_CONFIRMED));
		mockKohaFixture.verifyHoldPlacedOverridingPatronBlocks();
		mockKohaFixture.verifyHoldPlaced("{\"pickup_library_id\": \"DCB-SHARING\"}");
	}

	@Test
	void shouldOverrideTheVirtualPatronsBlocksOnAPickupHold() {
		mockKohaFixture.mockPlaceHold(placedHold());

		client.placeHoldRequestAtPickupAgency(parameters()).block();

		mockKohaFixture.verifyHoldPlacedOverridingPatronBlocks();
	}

	@Test
	void shouldLeaveARealPatronsBlocksStandingOnABorrowerHold() {
		mockKohaFixture.mockPlaceHold(placedHold());

		client.placeHoldRequestAtBorrowingAgency(parameters()).block();

		mockKohaFixture.verifyHoldPlacedWithoutOverride();
		mockKohaFixture.verifyHoldPlaced("{\"pickup_library_id\": \"BRANCH-N\"}");
	}

	@Test
	void shouldLeaveARealPatronsBlocksStandingOnALocalHold() {
		mockKohaFixture.mockPlaceHold(placedHold());

		client.placeHoldRequestAtLocalAgency(parameters()).block();

		mockKohaFixture.verifyHoldPlacedWithoutOverride();
	}

	@Test
	void shouldReportWhyKohaRefusedAHold() {
		// A refusal by circulation rule is not overridable, and the reason is what staff need
		mockKohaFixture.mockHoldRefused(403, "Hold not allowed");

		StepVerifier.create(client.placeHoldRequestAtBorrowingAgency(parameters()))
			.expectErrorSatisfies(error -> {
				assertThat(error, is(org.hamcrest.Matchers.instanceOf(ThrowableProblem.class)));
				assertThat(error.toString(), containsString("Hold not allowed"));
			})
			.verify();
	}

	@Test
	void shouldNameTheBranchRuleThatRefusedAHold() {
		// IndependentBranches without canreservefromotherbranches: the administrator's action,
		// not the patron's, so the problem says which preference and what to do
		mockKohaFixture.mockHoldRefused(403, "Hold cannot be placed. Reason: cannotReserveFromOtherBranches");

		StepVerifier.create(client.placeHoldRequestAtSupplyingAgency(parameters()))
			.expectErrorSatisfies(error -> {
				final var problem = (ThrowableProblem) error;
				assertThat(problem.getTitle(), is("Koha refused the hold"));
				assertThat(problem.getParameters().get("reason"), is("cannotReserveFromOtherBranches"));
				assertThat(String.valueOf(problem.getParameters().get("remedy")), containsString("IndependentBranches"));
				assertThat(String.valueOf(problem.getParameters().get("remedy")), containsString("AllowHoldPolicyOverride"));
			})
			.verify();
	}

	@Test
	void shouldNameThePatronBlockThatRefusedAHold() {
		mockKohaFixture.mockHoldRefused(409, "Hold cannot be placed. Reason: debt_limit");

		StepVerifier.create(client.placeHoldRequestAtBorrowingAgency(parameters()))
			.expectErrorSatisfies(error -> {
				final var problem = (ThrowableProblem) error;
				assertThat(problem.getParameters().get("reason"), is("debt_limit"));
				assertThat(String.valueOf(problem.getParameters().get("remedy")), containsString("owes"));
			})
			.verify();
	}

	private static KohaHoldResponse placedHold() {
		return KohaHoldResponse.builder()
			.holdId(501L)
			.patronId(12L)
			.biblioId(4001L)
			.itemId(55L)
			.pickupLibraryId("BRANCH-N")
			.build();
	}

	private static PlaceHoldRequestParameters parameters() {
		final var pickupLocation = Location.builder()
			.id(randomUUID())
			.code("PICKUP-NORTH")
			.name("North Branch")
			.localId("BRANCH-N")
			.build();

		return PlaceHoldRequestParameters.builder()
			.localPatronId("12")
			.localBibId("4001")
			.localItemId("55")
			.localItemBarcode("item-barcode")
			.pickupLocation(pickupLocation)
			.pickupLocationCode(pickupLocation.getId().toString())
			.activeWorkflow(STANDARD_WORKFLOW)
			.patronRequestId(randomUUID().toString())
			.build();
	}
}
