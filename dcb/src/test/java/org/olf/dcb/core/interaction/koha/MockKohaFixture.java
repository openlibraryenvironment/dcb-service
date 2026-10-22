package org.olf.dcb.core.interaction.koha;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.MockServerCommonResponses.created;
import static org.olf.dcb.test.MockServerCommonResponses.noContent;
import static org.olf.dcb.test.MockServerCommonResponses.notFound;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.MockServerCommonResponses.serverError;

import java.util.Map;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.interaction.koha.dto.KohaBiblio;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronBalance;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronAuthResponse;
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
	private static final String PATRONS_PATH = "/api/v1/patrons";
	private static final String PASSWORD_VALIDATION_PATH = "/api/v1/auth/password/validation";

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

	public void mockPatron(KohaPatron patron) {
		mockServer.mock(commonRequests.get(patronPath(String.valueOf(patron.getPatronId()))), okJson(patron));
	}

	public void mockPatronNotFound(String patronId) {
		mockServer.mock(commonRequests.get(patronPath(patronId)),
			notFound(Map.of("error", "Patron not found")));
	}

	/** The search findVirtualPatron and the name profile use; matched on the exact card number. */
	public void mockPatronsByCardnumber(String cardnumber, KohaPatron... patrons) {
		mockServer.mock(commonRequests.get(PATRONS_PATH, "cardnumber", cardnumber)
			.withQueryStringParameter("_match", "exact"), okJson(patrons));
	}

	/** Koha's answer to a right password: the patron it belongs to. */
	public void mockPasswordAccepted(String cardnumber, String password, KohaPatronAuthResponse response) {
		mockServer.mock(passwordValidation(cardnumber, password), created(response));
	}

	/** 401 for a wrong password, 400 for an identifier Koha does not know. */
	public void mockPasswordRefused(String cardnumber, String password, int status) {
		mockServer.mock(passwordValidation(cardnumber, password),
			response().withStatusCode(status).withBody(json(Map.of("error", "Validation failed"))));
	}

	private HttpRequest passwordValidation(String cardnumber, String password) {
		return commonRequests.post(PASSWORD_VALIDATION_PATH)
			.withBody(json(Map.of("identifier", cardnumber, "password", password)));
	}

	public void mockPatronCheckouts(String patronId, KohaCheckout... checkouts) {
		mockServer.mock(commonRequests.get(patronPath(patronId) + "/checkouts")
			.withHeader("x-koha-embed", "item.biblio"), okJson(checkouts));
	}

	public void mockPatronBalance(String patronId, KohaPatronBalance balance) {
		mockServer.mock(commonRequests.get(patronPath(patronId) + "/account"), okJson(balance));
	}

	/** Koha's answer when the API patron lacks updatecharges. */
	public void mockPatronBalanceForbidden(String patronId) {
		mockServer.mock(commonRequests.get(patronPath(patronId) + "/account"),
			response().withStatusCode(403).withBody(json(Map.of("error", "Authorization failure"))));
	}

	private static String patronPath(String patronId) {
		return "/api/v1/patrons/%s".formatted(patronId);
	}

	public void mockCreateBib(KohaBiblio created) {
		mockServer.mock(commonRequests.post("/api/v1/biblios"), okJson(created));
	}

	/** The virtual item's state as the request progresses: the latest answer replaces the last. */
	public void replaceItemCirculation(String biblioId, String itemId, KohaItem... items) {
		mockServer.replaceMock(itemCirculationRequest(biblioId, itemId), okJson(items));
	}

	public void mockDeleteHold(String holdId) {
		mockServer.mock(commonRequests.delete(holdPath(holdId)), noContent());
	}

	public void mockDeleteItem(String itemId) {
		mockServer.mock(commonRequests.delete("/api/v1/items/" + itemId), noContent());
	}

	public void mockDeleteBib(String biblioId) {
		mockServer.mock(commonRequests.delete("/api/v1/biblios/" + biblioId), noContent());
	}

	/** The three lists the configuration check reads, and a Koha older than 26.05 for preferences. */
	public void mockConfigurationLists(KohaLibrary... libraries) {
		mockServer.mock(commonRequests.get("/api/v1/item_types"), okJson(new Object[0]));
		mockServer.mock(commonRequests.get("/api/v1/patron_categories"), okJson(new Object[0]));
		mockServer.mock(commonRequests.get("/api/v1/libraries"), okJson(libraries));
		mockServer.mock(commonRequests.get("/api/v1/sysprefs"), notFound(Map.of("error", "Not found")));
	}

	/** A local request places a hold on the real item and nothing else. */
	public void verifyNoVirtualRecordsCreated() {
		mockServer.verifyNever(commonRequests.post("/api/v1/biblios"));
		mockServer.verifyNever(commonRequests.post("/api/v1/biblios/[0-9]+/items"));
	}

	public void verifyHoldDeleted(String holdId) {
		client.verify(commonRequests.delete(holdPath(holdId)));
	}

	public void verifyItemDeleted(String itemId) {
		client.verify(commonRequests.delete("/api/v1/items/" + itemId));
	}

	public void verifyBibDeleted(String biblioId) {
		client.verify(commonRequests.delete("/api/v1/biblios/" + biblioId));
	}

	/** Koha's answer when no item carries the barcode: an empty list, not a 404. */
	public void mockNoItemWithBarcode(String barcode) {
		mockServer.mock(commonRequests.get("/api/v1/items", "external_id", barcode), okJson(new KohaItem[0]));
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

	public void mockUpdateItem(String biblioId, String itemId, KohaItem updated) {
		mockServer.mock(commonRequests.put(itemPath(biblioId, itemId)), okJson(updated));
	}

	/**
	 * @param onlyTheseFields JSON the body has to contain; other fields are ignored
	 */
	public void verifyItemUpdated(String biblioId, String itemId, String onlyTheseFields) {
		client.verify(commonRequests.put(itemPath(biblioId, itemId))
			.withBody(json(onlyTheseFields, MatchType.ONLY_MATCHING_FIELDS)));
	}

	private static String itemPath(String biblioId, String itemId) {
		return "/api/v1/biblios/%s/items/%s".formatted(biblioId, itemId);
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

	/** Koha's refusal of a hold its rules do not allow: 403 with the reason. */
	public void mockHoldRefused(int status, String reason) {
		mockServer.mock(commonRequests.post(HOLDS_PATH),
			response().withStatusCode(status).withBody(json(Map.of("error", reason))));
	}

	public void verifyHoldPlacedOverridingPatronBlocks() {
		client.verify(commonRequests.post(HOLDS_PATH).withHeader("x-koha-override", "any"));
	}

	public void verifyHoldPlacedWithoutOverride() {
		client.verify(commonRequests.post(HOLDS_PATH).withHeader(NottableString.not("x-koha-override")));
	}

	/** One page of a biblio's items, as the paged read asks for it. */
	public void mockItemsPage(String biblioId, int page, KohaItem... items) {
		mockServer.mock(commonRequests.get(itemsForBiblioPath(biblioId), "_page", String.valueOf(page))
			.withHeader("x-koha-embed", "checkout"), okJson(items));
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
