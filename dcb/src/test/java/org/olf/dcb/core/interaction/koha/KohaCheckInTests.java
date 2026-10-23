package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.HashMap;
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
import org.slf4j.LoggerFactory;
import org.zalando.problem.ThrowableProblem;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Koha's REST API has no check-in. Linking the returned virtual item into DCB's bundle host
 * item with force_checkin returns it (C4::Circulation::AddReturn), and unlinking leaves it as
 * it was but checked in.
 */
@MockServerMicronautTest
class KohaCheckInTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-check-in";
	private static final String KOHA_HOST = "fake-koha-check-in";
	private static final String HOST_BARCODE = "DCB-BUNDLE-HOST";
	private static final String HOST_ITEM_ID = "900";
	private static final String VIRTUAL_ITEM_ID = "55";
	private static final String VIRTUAL_ITEM_BARCODE = "6565750674";
	private static final String NCIP_TOKEN = "ncip-secret-27182";
	private static final String CHECKED_IN = "<NCIPMessage><CheckInItemResponse><ItemId><ItemIdentifierValue>"
		+ VIRTUAL_ITEM_BARCODE + "</ItemIdentifierValue></ItemId></CheckInItemResponse></NCIPMessage>";
	private static final String NOT_CHECKED_OUT = "<NCIPMessage><CheckInItemResponse><Problem>"
		+ "<ProblemType>Item Not Checked Out</ProblemType>"
		+ "<ProblemDetail>There is no record of the check out of the item.</ProblemDetail>"
		+ "</Problem></CheckInItemResponse></NCIPMessage>";

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

	@Test
	void shouldNotTryNcipWhenTheBundleRouteWorks() {
		koha.mockAddToBundle(HOST_ITEM_ID, virtualItem());
		koha.mockRemoveFromBundle(HOST_ITEM_ID, VIRTUAL_ITEM_ID);
		koha.mockNcipCheckIn(NCIP_TOKEN, CHECKED_IN);

		assertThat(clientWith(withNcipFallback(true)).checkInItem(command()).block(), is("OK"));

		koha.verifyNoNcipCall();
	}

	@Test
	void shouldCheckInOverNcipWhenTheBundleRouteFails() {
		// The barcode filter case: Koha cannot find the virtual item by the lender's barcode
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		koha.mockNcipCheckIn(NCIP_TOKEN, CHECKED_IN);

		assertThat(clientWith(withNcipFallback(true)).checkInItem(command()).block(), is("OK"));

		koha.verifyNcipCheckIn(NCIP_TOKEN, VIRTUAL_ITEM_BARCODE);
	}

	@Test
	void shouldCheckInOverNcipWhenNoBundleHostIsConfigured() {
		final var config = withNcipFallback(true);
		config.remove("bundle-host-barcode");
		koha.mockNcipCheckIn(NCIP_TOKEN, CHECKED_IN);

		assertThat(clientWith(config).checkInItem(command()).block(), is("OK"));

		koha.verifyNeverBundled();
		koha.verifyNcipCheckIn(NCIP_TOKEN, VIRTUAL_ITEM_BARCODE);
	}

	@Test
	void shouldNotFallBackUnlessTheHostLmsEnablesIt() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		koha.mockNcipCheckIn(NCIP_TOKEN, CHECKED_IN);

		StepVerifier.create(clientWith(withNcipFallback(false)).checkInItem(command()))
			.expectErrorSatisfies(error -> assertThat(((ThrowableProblem) error).getTitle(),
				is("Koha would not check the item in")))
			.verify();

		koha.verifyNoNcipCall();
	}

	@Test
	void shouldCountAnItemNcipSaysIsNotOnLoanAsCheckedIn() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		koha.mockNcipCheckIn(NCIP_TOKEN, NOT_CHECKED_OUT);

		assertThat(clientWith(withNcipFallback(true)).checkInItem(command()).block(), is("OK"));
	}

	@Test
	void shouldNameBothRoutesAndTheTokenWhenNcipRefusesIt() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		koha.mockNcipRefusesToken(NCIP_TOKEN);

		StepVerifier.create(clientWith(withNcipFallback(true)).checkInItem(command()))
			.expectErrorSatisfies(error -> {
				final var problem = (ThrowableProblem) error;
				assertThat(problem.getTitle(), is("Koha would not check the item in by either route"));
				assertThat(String.valueOf(problem.getParameters().get("reason")), containsString("failed_checkin"));
				assertThat(String.valueOf(problem.getParameters().get("reason")), containsString("HTTP 403"));
				assertThat(String.valueOf(problem.getParameters().get("checkInRemedy")), containsString("ncip-checkin-token"));
				assertThat(problem.toString(), not(containsString(NCIP_TOKEN)));
			})
			.verify();
	}

	@Test
	void shouldEscapeTheBarcodeItSendsOverNcip() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		koha.mockNcipCheckIn(NCIP_TOKEN, CHECKED_IN);

		clientWith(withNcipFallback(true)).checkInItem(command("A&B<1>")).block();

		koha.verifyNcipCheckIn(NCIP_TOKEN, "A&amp;B&lt;1&gt;");
	}

	@Test
	void shouldNotCountANamespacedNcipProblemAsACheckIn() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		koha.mockNcipCheckIn(NCIP_TOKEN, """
			<ns1:NCIPMessage xmlns:ns1="http://www.niso.org/2008/ncip"><ns1:CheckInItemResponse><ns1:Problem>
			<ns1:ProblemType>Unknown Item</ns1:ProblemType>
			<ns1:ProblemDetail>Item is not known.</ns1:ProblemDetail>
			</ns1:Problem></ns1:CheckInItemResponse></ns1:NCIPMessage>""");

		StepVerifier.create(clientWith(withNcipFallback(true)).checkInItem(command()))
			.expectErrorSatisfies(error -> assertThat(
				String.valueOf(((ThrowableProblem) error).getParameters().get("reason")),
				containsString("NCIP: Unknown Item: Item is not known.")))
			.verify();
	}

	@Test
	void shouldNotCountAPageThatIsNotNcipAsACheckIn() {
		koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
		// A proxy's sign-in page, answered with 200
		koha.mockNcipCheckIn(NCIP_TOKEN, "<html><body>Please sign in</body></html>");

		StepVerifier.create(clientWith(withNcipFallback(true)).checkInItem(command()))
			.expectErrorSatisfies(error -> assertThat(
				String.valueOf(((ThrowableProblem) error).getParameters().get("reason")),
				containsString("NCIP: Unexpected response")))
			.verify();
	}

	@Test
	void shouldNeverLogTheNcipTokenOrAddress() {
		final var logger = (Logger) LoggerFactory.getLogger("org.olf.dcb");
		final var captured = new ListAppender<ILoggingEvent>();
		final var previousLevel = logger.getLevel();
		captured.start();
		logger.setLevel(Level.TRACE);
		logger.addAppender(captured);

		try {
			koha.mockAddToBundleRefused(HOST_ITEM_ID, "failed_checkin");
			koha.mockNcipRefusesToken(NCIP_TOKEN);
			final var client = clientWith(withNcipFallback(true));

			client.checkInItem(command()).onErrorResume(error -> Mono.empty()).block();
			client.checkConfiguration().onErrorResume(error -> Mono.empty()).block();
		} finally {
			logger.detachAppender(captured);
			logger.setLevel(previousLevel);
		}

		final var logged = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(logged, not(hasItem(containsString(NCIP_TOKEN))));
		assertThat(logged, not(hasItem(containsString(MockKohaFixture.NCIP_PATH))));
	}

	@Test
	void shouldReportAnAnsweringNcipResponderAtSetup() {
		koha.mockConfigurationLists(KohaLibrary.builder().libraryId("DCB-SHARING").pickupLocation(true).build());
		koha.mockNcipCheckIn(NCIP_TOKEN, "<NCIPMessage><LookupVersionResponse/></NCIPMessage>");

		assertThat(check(clientWith(withNcipFallback(true)), "ncip-checkin-fallback").result(),
			is(ConfigurationReport.CheckResult.PRESENT));
	}

	@Test
	void shouldReportARefusedTokenAtSetup() {
		koha.mockConfigurationLists(KohaLibrary.builder().libraryId("DCB-SHARING").pickupLocation(true).build());
		koha.mockNcipRefusesToken(NCIP_TOKEN);

		assertThat(check(clientWith(withNcipFallback(true)), "ncip-checkin-fallback").result(),
			is(ConfigurationReport.CheckResult.MISSING));
	}

	private static Map<String, Object> withNcipFallback(boolean enabled) {
		final Map<String, Object> config = new HashMap<>();
		config.put("bundle-host-barcode", HOST_BARCODE);
		config.put("ncip-checkin-fallback", String.valueOf(enabled));
		config.put("ncip-checkin-url", "https://" + KOHA_HOST + MockKohaFixture.NCIP_PATH);
		config.put("ncip-checkin-token", NCIP_TOKEN);
		return config;
	}

	private static ConfigurationReport.Check check(HostLmsClient client, String setting) {
		return client.checkConfiguration().block().checks().stream()
			.filter(check -> check.setting().equals(setting))
			.findFirst()
			.orElseThrow();
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
		return command(VIRTUAL_ITEM_BARCODE);
	}

	private static CheckInItemCommand command(String barcode) {
		return CheckInItemCommand.builder()
			.itemId(VIRTUAL_ITEM_ID)
			.itemBarcode(barcode)
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
