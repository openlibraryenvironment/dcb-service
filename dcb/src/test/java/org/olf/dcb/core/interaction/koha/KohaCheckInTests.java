package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CheckInItemCommand;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Koha's REST API has no check-in. Linking the returned virtual item into DCB's bundle host
 * item with force_checkin returns it (C4::Circulation::AddReturn), and unlinking leaves it as
 * it was but checked in (ADR 0002).
 */
@MockServerMicronautTest
class KohaCheckInTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-check-in";
	private static final String KOHA_HOST = "fake-koha-check-in";
	private static final String HOST_BARCODE = "DCB-BUNDLE-HOST";
	private static final String HOST_ITEM_ID = "900";
	private static final String VIRTUAL_ITEM_ID = "55";
	private static final String VIRTUAL_ITEM_BARCODE = "6565750674";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		koha.mockBundleHost(HOST_BARCODE, Long.parseLong(HOST_ITEM_ID));
		koha.mockItem(virtualItem());
	}

	@Test
	void shouldCheckTheVirtualItemInByBundlingItAndUnlinkingIt() {
		koha.mockAddToBundle(HOST_ITEM_ID, virtualItem());
		koha.mockRemoveFromBundle(HOST_ITEM_ID, VIRTUAL_ITEM_ID);

		assertThat(configuredClient().checkInItem(command()).block(), is("OK"));

		koha.verifyAddedToBundle(HOST_ITEM_ID,
			"{\"external_id\": \"%s\", \"force_checkin\": true, \"ignore_holds\": true}".formatted(VIRTUAL_ITEM_BARCODE));
		koha.verifyRemovedFromBundle(HOST_ITEM_ID, VIRTUAL_ITEM_ID);
	}

	@Test
	void shouldFinishAnEarlierAttemptThatBundledButDidNotUnlink() {
		// already_bundled: the return happened on the earlier attempt
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "already_bundled");
		koha.mockRemoveFromBundle(HOST_ITEM_ID, VIRTUAL_ITEM_ID);

		assertThat(configuredClient().checkInItem(command()).block(), is("OK"));

		koha.verifyRemovedFromBundle(HOST_ITEM_ID, VIRTUAL_ITEM_ID);
	}

	@Test
	void shouldTolerateALinkThatIsAlreadyGone() {
		koha.mockAddToBundle(HOST_ITEM_ID, virtualItem());
		koha.mockRemoveFromBundleGone(HOST_ITEM_ID, VIRTUAL_ITEM_ID);

		assertThat(configuredClient().checkInItem(command()).block(), is("OK"));
	}

	@Test
	void shouldNameTheReasonKohaRefusedTheReturn() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");

		StepVerifier.create(configuredClient().checkInItem(command()))
			.expectErrorSatisfies(error -> {
				final var problem = (ThrowableProblem) error;
				assertThat(problem.getTitle(), is("Koha would not check the item in"));
				assertThat(problem.getParameters().get("reason"), is("failed_checkin"));
			})
			.verify();
	}

	@Test
	void shouldSayTheBundleHostIsOnLoan() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "bundle_checked_out");

		StepVerifier.create(configuredClient().checkInItem(command()))
			.expectErrorSatisfies(error -> assertThat(
				String.valueOf(((ThrowableProblem) error).getParameters().get("checkInRemedy")),
				containsString(HOST_BARCODE)))
			.verify();
	}

	@Test
	void shouldRefuseWithoutABundleHostAndCallNothing() {
		final var client = clientWith(Map.of());

		StepVerifier.create(client.checkInItem(command()))
			.expectErrorSatisfies(error -> {
				final var problem = (ThrowableProblem) error;
				assertThat(problem.getTitle(), is("Koha check-in is not configured"));
				assertThat(String.valueOf(problem.getParameters().get("checkInRemedy")),
					containsString("bundle-host-barcode"));
			})
			.verify();

		koha.verifyNeverBundled();
	}

	@Test
	void shouldNameTheSettingWhenNoItemHasTheHostBarcode() {
		final var client = clientWith(Map.of("bundle-host-barcode", "NO-SUCH-BARCODE"));
		koha.mockNoItemWithBarcode("NO-SUCH-BARCODE");

		StepVerifier.create(client.checkInItem(command()))
			.expectErrorSatisfies(error -> assertThat(
				String.valueOf(((ThrowableProblem) error).getParameters().get("checkInRemedy")),
				containsString("bundle-host-barcode")))
			.verify();

		koha.verifyNeverBundled();
	}

	@Test
	void shouldReportTheBundleHostAtSetup() {
		koha.mockConfigurationLists(KohaLibrary.builder().libraryId("DCB-SHARING").pickupLocation(true).build());

		assertThat(bundleHostCheck(configuredClient()).result(), is(ConfigurationReport.CheckResult.PRESENT));
	}

	@Test
	void shouldReportMissingCheckInAtSetup() {
		koha.mockConfigurationLists(KohaLibrary.builder().libraryId("DCB-SHARING").pickupLocation(true).build());

		assertThat(bundleHostCheck(clientWith(Map.of())).result(),
			is(ConfigurationReport.CheckResult.NOT_CONFIGURED));
	}

	private HostLmsClient configuredClient() {
		return clientWith(Map.of("bundle-host-barcode", HOST_BARCODE));
	}

	private HostLmsClient clientWith(Map<String, Object> extraConfig) {
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST, extraConfig);

		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	private static ConfigurationReport.Check bundleHostCheck(HostLmsClient client) {
		return client.checkConfiguration().block().checks().stream()
			.filter(check -> check.setting().equals("bundle-host-barcode"))
			.findFirst()
			.orElseThrow();
	}

	private static CheckInItemCommand command() {
		return CheckInItemCommand.builder()
			.itemId(VIRTUAL_ITEM_ID)
			.itemBarcode(VIRTUAL_ITEM_BARCODE)
			.build();
	}

	private static KohaItem virtualItem() {
		return KohaItem.builder()
			.itemId(Long.valueOf(VIRTUAL_ITEM_ID))
			.biblioId(4001L)
			.externalId(VIRTUAL_ITEM_BARCODE)
			.homeLibraryId("BRANCH-N")
			.build();
	}
}
