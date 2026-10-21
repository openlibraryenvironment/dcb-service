package org.olf.dcb.core.interaction.koha;

import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.MockServerCommonResponses.created;
import static org.olf.dcb.test.MockServerCommonResponses.notFound;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.MockServerCommonResponses.serverError;

import java.util.Map;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.HttpRequest;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.test.MockServer;
import org.olf.dcb.test.MockServerCommonRequests;

/**
 * A stand-in Koha REST API.
 * <p>
 * Requests are matched on method, path and host but deliberately not on the
 * Authorization header: Koha is OAuth2 client-credentials, so the bearer token is
 * minted at runtime and cached, and matching on it would only pin a value the test
 * has no reason to know.
 */
public class MockKohaFixture {
	private static final String TOKEN_PATH = "/api/v1/oauth/token";
	private static final String HOLDS_PATH = "/api/v1/holds";

	private final MockServerClient client;
	private final MockServerCommonRequests commonRequests;
	private final MockServer mockServer;

	public MockKohaFixture(MockServerClient mockServerClient, String host) {
		this.client = mockServerClient;
		this.commonRequests = new MockServerCommonRequests(host);
		this.mockServer = new MockServer(mockServerClient, commonRequests);

		mockOauthToken();
	}

	/**
	 * Every non-public Koha call fetches a token first, so this is unconditional rather
	 * than something each test has to remember.
	 */
	private void mockOauthToken() {
		mockServer.mock(commonRequests.post(TOKEN_PATH), okJson(Map.of(
			"access_token", "koha-access-token",
			"token_type", "Bearer",
			// Cached until 60 seconds before expiry, so this has to exceed 60 or the
			// token is stale the moment it arrives
			"expires_in", 3600)));
	}

	public void mockItemsForBiblio(String biblioId, KohaItem... items) {
		mockServer.mock(commonRequests.get(itemsForBiblioPath(biblioId)), okJson(items));
	}

	/**
	 * No active holds, which is what getItems asks for per item to derive the hold
	 * count. Matched on the path alone so one expectation covers every item.
	 */
	public void mockNoActiveHolds() {
		mockServer.mock(commonRequests.get(HOLDS_PATH), okJson(new Object[0]));
	}

	/** The plain single-item read, which carries no circulation embeds. */
	public void mockItem(KohaItem item) {
		mockServer.mock(commonRequests.get("/api/v1/items/" + item.getItemId()), okJson(item));
	}

	/**
	 * The per-biblio list filtered to one item, with checkout, transfer and first_hold
	 * embedded: the only read that answers what the item is doing.
	 */
	public void mockItemCirculation(String biblioId, String itemId, KohaItem... items) {
		mockServer.mock(itemCirculationRequest(biblioId, itemId), okJson(items));
	}

	public void verifyItemCirculationRead(String biblioId, String itemId) {
		client.verify(itemCirculationRequest(biblioId, itemId));
	}

	private HttpRequest itemCirculationRequest(String biblioId, String itemId) {
		return commonRequests.get(itemsForBiblioPath(biblioId), "q", KohaApiClient.itemFilter(itemId))
			.withHeader("x-koha-embed", KohaApiClient.CIRCULATION_EMBEDS);
	}

	public void mockCreateItem(String biblioId, KohaItem createdItem) {
		mockServer.mock(commonRequests.post(itemsForBiblioPath(biblioId)), created(createdItem));
	}

	public void mockPlaceHold(KohaHoldResponse response) {
		mockServer.mock(commonRequests.post(HOLDS_PATH), created(response));
	}

	public void mockHold(KohaHoldResponse hold) {
		mockServer.mock(commonRequests.get(holdPath(String.valueOf(hold.getHoldId()))), okJson(hold));
	}

	/** Koha's answer for a hold it has moved to old_reserves, or never had. */
	public void mockHoldNotFound(String holdId) {
		mockServer.mock(commonRequests.get(holdPath(holdId)),
			notFound(Map.of("error", "Hold not found")));
	}

	public void mockHoldServerError(String holdId) {
		mockServer.mock(commonRequests.get(holdPath(holdId)), serverError());
	}

	/** The old-holds list, which is the only place a filled or cancelled hold still appears. */
	public void mockOldHold(String holdId, KohaHoldResponse... oldHolds) {
		mockServer.mock(oldHoldRequest(holdId), okJson(oldHolds));
	}

	public void verifyOldHoldNeverRead(String holdId) {
		mockServer.verifyNever(oldHoldRequest(holdId));
	}

	private HttpRequest oldHoldRequest(String holdId) {
		return commonRequests.get(HOLDS_PATH, "hold_id", holdId)
			.withQueryStringParameter("old", "true");
	}

	private static String holdPath(String holdId) {
		return "/api/v1/holds/%s".formatted(holdId);
	}

	/**
	 * @param onlyTheseFields JSON the request body has to contain - other fields are
	 * ignored, so a test can assert the one value it is about
	 */
	public void verifyCreateItem(String biblioId, String onlyTheseFields) {
		client.verify(commonRequests.post(itemsForBiblioPath(biblioId))
			.withBody(json(onlyTheseFields, MatchType.ONLY_MATCHING_FIELDS)));
	}

	public void verifyHoldPlaced(String onlyTheseFields) {
		client.verify(commonRequests.post(HOLDS_PATH)
			.withBody(json(onlyTheseFields, MatchType.ONLY_MATCHING_FIELDS)));
	}

	private static String itemsForBiblioPath(String biblioId) {
		return "/api/v1/biblios/%s/items".formatted(biblioId);
	}
}
