package org.olf.dcb.core.interaction.koha;

import io.micronaut.http.MediaType;
import org.olf.dcb.core.interaction.koha.dto.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

/**
 * Provides core HTTP operations and strongly-typed endpoint methods for Koha. Please refer to <a href="https://api.koha-community.org">...</a> for API documentation
 * Versions supported TBC - 25.11
 * <p>A lookup by an identifier ({@code cardnumber}, {@code userid}, {@code external_id}) passes
 * {@code _match=exact}, because Koha's default is a substring match, and decodes to an array:
 * a DTO extending {@code ArrayList} cannot be decoded.
 */
public interface KohaApiClient {

	// --- Core HTTP methods ---
	<T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams);
	<T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams, Map<String, String> headers); // Sometimes we need to pass Koha headers
	<T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams);
	<T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams,
		Map<String, String> headers, String contentType);
	<T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams,
		Map<String, String> headers);
	<T> Mono<T> put(String path, Object body, Class<T> responseType, Map<String, Object> queryParams);
	<T> Mono<T> patch(String path, Object body, Class<T> responseType, Map<String, Object> queryParams);
	Mono<Void> delete(String path, Map<String, Object> queryParams);

	default <T> Mono<T> get(String path, Class<T> responseType) {
		return get(path, responseType, Collections.emptyMap());
	}
	default <T> Mono<T> post(String path, Object body, Class<T> responseType) {
		return post(path, body, responseType, Collections.emptyMap());
	}
	default <T> Mono<T> put(String path, Object body, Class<T> responseType) {
		return put(path, body, responseType, Collections.emptyMap());
	}
	default Mono<Void> delete(String path) {
		return delete(path, Collections.emptyMap());
	}


	/* Patron operations **/

	/**
	 * Get details for a Koha patron.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/getPatron">Getting a patron in Koha</a>
	 * </p>
	 */
	default Mono<KohaPatron> getPatron(String patronId) {
		return get("/api/v1/patrons/" + patronId, KohaPatron.class);
	}

	/**
	 * Search patrons by userid. _match defaults to contains, so without exact a userid is
	 * matched by any other that contains it.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/listPatrons">Searching patrons in Koha</a>
	 * </p>	 */
	default Mono<KohaPatron[]> getPatronByUserId(String userid) {
		return get("/api/v1/patrons", KohaPatron[].class, Map.of("userid", userid, "_match", "exact"));
	}

	/**
	 * Search patrons by cardnumber.
	 * <p>
	 * API: GET /api/v1/patrons?cardnumber={cardnumber}
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/listPatrons">Searching patrons in Koha</a>
	 * </p>	 */
	default Mono<KohaPatron[]> getPatronByCardnumber(String cardnumber) {
		return get("/api/v1/patrons", KohaPatron[].class, Map.of("cardnumber", cardnumber, "_match", "exact"));
	}

	/**
	 * Create a new patron.
	 * Known as "add patron" in Koha.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/addPatron">Adding a patron in Koha</a>
	 * </p>
	 * <p>
	 * From 25.11 Koha refuses, with a 409, a patron matching one it holds on its
	 * PatronDuplicateMatchingAddFields (surname and first name by default) unless
	 * x-confirm-not-duplicate is sent. A virtual patron copies the borrower's names, or "DCB
	 * VPATRON" when there are none, so it would often match. 25.05 makes no such check.
	 */
	default Mono<KohaPatron> createPatron(KohaPatron patron) {
		return post("/api/v1/patrons", patron, KohaPatron.class, Collections.emptyMap(),
			Map.of("x-confirm-not-duplicate", "1"));
	}

	/**
	 * Delete a patron.
	 * Returns 204 on success.
	 * Can fail (409) for a few reasons. Notably a 409 indicates a conflict, which can be pending debts, checkouts etc.
	 * <p>
	 * Can also fail if a patron is protected. No virtual patron should ever be protected so if this occurs, something has gone wrong.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/deletePatron">Deleting a patron in Koha</a>
	 */
	default Mono<Void> deletePatron(String patronId) {
		return delete("/api/v1/patrons/" + patronId);
	}

	/**
	 * Update a patron in the Koha LMS.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/updatePatron">Updating a patron in Koha </a>
	 * @param patronId - ID of the patron in Koha
	 * @param patron - Koha patron object
	 * @return KohaPatron object - the updated patron
	 */
	default Mono<KohaPatron> updatePatron(String patronId, KohaPatron patron) {
		return put("/api/v1/patrons/" + patronId, patron, KohaPatron.class);
	}

	/**
	 * A patron's current loans with the item and its biblio embedded.
	 * API: GET /api/v1/patrons/{patron_id}/checkouts
	 */
	default Mono<KohaCheckout[]> getPatronCheckouts(String patronId, int maxRows) {
		return get("/api/v1/patrons/" + patronId + "/checkouts", KohaCheckout[].class,
			Map.of("_per_page", maxRows), Map.of("x-koha-embed", "item.biblio"));
	}

	/**
	 * A patron's balance with its outstanding lines. Needs updatecharges, which the API
	 * patron may not hold.
	 * API: GET /api/v1/patrons/{patron_id}/account
	 */
	default Mono<KohaPatronBalance> getPatronBalance(String patronId) {
		return get("/api/v1/patrons/" + patronId + "/account", KohaPatronBalance.class);
	}

	/**
	 * One system preference by name. Koha 26.05 and later, permission parameters:
	 * manage_sysprefs; an older Koha answers 404.
	 * API: GET /api/v1/sysprefs?variable={name}
	 */
	default Mono<KohaSystemPreference[]> getSystemPreference(String name) {
		return get("/api/v1/sysprefs", KohaSystemPreference[].class, Map.of("variable", name));
	}

	/** Authenticate a patron
	 * API docs: <a href="https://api.koha-community.org/#tag/patrons/operation/validateUserAndPassword">Validating a patron in Koha</a>
	 * @param patronPrinciple - patron identifier
	 * @param secret - whatever we're authenticating with (password etc)
	 */
	default Mono<KohaPatronAuthResponse> authenticatePatron(String patronPrinciple, String secret)
	{
		return post("/api/v1/auth/password/validation",
			Map.of("identifier", patronPrinciple, "password", secret),
			KohaPatronAuthResponse.class);
	}



	/* Bib operations **/


	/**
	 * Create a new bib record.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/biblios/operation/addBiblio">Add biblio</a>
	 * </p>
	 * API: POST /api/v1/biblios
	 * <p>
	 * Koha answers {"id": biblionumber}, and refuses a record matching one it holds unless
	 * x-confirm-not-duplicate is sent, which a virtual copy of a title always would.
	 */
	default Mono<KohaCreatedBiblio> createBibRecord(String bibXml) {
		// Note: Koha explicitly requires the 'application/marcxml+xml' content type for XML payloads
		return post("/api/v1/biblios", bibXml, KohaCreatedBiblio.class, Collections.emptyMap(),
			Map.of("x-confirm-not-duplicate", "1"), "application/marcxml+xml");
	}

	/**
	 * Delete a bib record.
	 * API: DELETE /api/v1/biblios/{biblio_id}
	 */
	default Mono<Void> deleteBib(String biblioId) {
		return delete("/api/v1/biblios/" + biblioId);
	}

	/* Item operations **/

	/**
	 * Create a new item under a bib.
	 * API: POST /api/v1/biblios/{biblio_id}/items
	 */
	default Mono<KohaItem> createItem(String biblioId, KohaItem item) {
		return post("/api/v1/biblios/" + biblioId + "/items", item, KohaItem.class);
	}


	/**
	 * Find an item by its internal Koha ID
	 * API: GET /api/v1/items/itemID
	 */
	default Mono<KohaItem> getItem(String itemId) {
		return get("/api/v1/items/" + itemId, KohaItem.class);
	}

	/**
	 * One item with its checkout, transfer and first hold. Only the per-biblio list offers
	 * those embeds, so the item is selected from it with Koha's q filter.
	 * API: GET /api/v1/biblios/{biblio_id}/items?q={"item_id":N}
	 */
	default Mono<KohaItem[]> getItemCirculation(String biblioId, String itemId) {
		return get("/api/v1/biblios/" + biblioId + "/items", KohaItem[].class,
			Map.of("q", itemFilter(itemId)), Map.of("x-koha-embed", CIRCULATION_EMBEDS));
	}

	String CIRCULATION_EMBEDS = "checkout,transfer,first_hold";

	static String itemFilter(String itemId) {
		return "{\"item_id\":" + itemId + "}";
	}

	/**
	 * The vocabularies a mapping is built from. Each returns a bare array rather than a
	 * wrapped list, which is what the Koha REST spec declares for all three. '_per_page=-1'
	 * because Koha otherwise returns its default page of 20, and every value past it would
	 * be reported MISSING.
	 * API: <a href="https://api.koha-community.org/#tag/item_types">item types</a>
	 */
	default Mono<KohaItemType[]> getItemTypes() {
		return get("/api/v1/item_types", KohaItemType[].class, Map.of("_per_page", -1));
	}

	/** API: GET /api/v1/patron_categories */
	default Mono<KohaPatronCategory[]> getPatronCategories() {
		return get("/api/v1/patron_categories", KohaPatronCategory[].class, Map.of("_per_page", -1));
	}

	/**
	 * One library: an authenticated read that needs a token and the catalogue permission.
	 * <p>
	 * API: GET /api/v1/libraries?_per_page=1
	 */
	default Mono<KohaLibrary[]> getOneLibrary() {
		return get("/api/v1/libraries", KohaLibrary[].class, Map.of("_per_page", 1));
	}

	/** API: GET /api/v1/libraries */
	default Mono<KohaLibrary[]> getLibraries() {
		return get("/api/v1/libraries", KohaLibrary[].class, Map.of("_per_page", -1));
	}



	/**
	 * Search for items by barcode (external_id).
	 * API: GET /api/v1/items?external_id={barcode}
	 */
	default Mono<KohaItem[]> getItemByBarcode(String barcode) {
		return get("/api/v1/items", KohaItem[].class, Map.of("external_id", barcode, "_match", "exact"));
	}

	/**
	 * A hold Koha is still working on, read through the list. No Koha has GET /holds/{hold_id}:
	 * its spec defines only PUT, PATCH and DELETE there (24.11 to main), and a GET answers an
	 * HTML 404.
	 * API: GET /api/v1/holds?hold_id={hold_id}&old=false
	 */
	default Mono<KohaHoldResponse[]> getCurrentHold(String holdId) {
		return get("/api/v1/holds", KohaHoldResponse[].class, Map.of("hold_id", holdId, "old", false));
	}

	/**
	 * A hold Koha has finished with, filled or cancelled, which only the list reports.
	 * API: GET /api/v1/holds?hold_id={hold_id}&old=true
	 */
	default Mono<KohaHoldResponse[]> getOldHold(String holdId) {
		return get("/api/v1/holds", KohaHoldResponse[].class, Map.of("hold_id", holdId, "old", true));
	}

	/**
	 * Get items for a biblio, with the checkout, transfer and first hold that say whether each
	 * can be lent.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/biblios/operation/getBiblioItems">Getting items for a biblio</a>
	 * </p>
	 */
	default Mono<KohaItem[]> getItemsForBiblio(String biblioId, int page, int perPage) {
		return get("/api/v1/biblios/" + biblioId + "/items", KohaItem[].class,
			Map.of("_page", page, "_per_page", perPage), Map.of("x-koha-embed", CIRCULATION_EMBEDS));
	}

	/**
	 * Links an item into a bundle. With force_checkin Koha first returns the item through
	 * C4::Circulation::AddReturn, at the API patron's branch; ignore_holds keeps a hold on the
	 * item from rolling that return back. The item is found by its barcode.
	 * API: POST /api/v1/items/{item_id}/bundled_items
	 */
	default Mono<KohaItem> addToBundle(String hostItemId, String itemBarcode) {
		return post("/api/v1/items/" + hostItemId + "/bundled_items",
			Map.of("external_id", itemBarcode, "force_checkin", true, "ignore_holds", true), KohaItem.class);
	}

	/**
	 * Unlinks a bundled item; Koha resets its not_for_loan_status to 0.
	 * API: DELETE /api/v1/items/{item_id}/bundled_items/{bundled_item_id}
	 */
	default Mono<Void> removeFromBundle(String hostItemId, String itemId) {
		return delete("/api/v1/items/" + hostItemId + "/bundled_items/" + itemId);
	}

	/**
	 * A page of the biblio's items as an anonymous OPAC user sees them: Koha leaves out what
	 * OpacHiddenItems and hidelostitems hide. Sent without a token, because Koha skips the rules
	 * for a caller whose category is in OpacHiddenItemsExceptions.
	 * API: GET /api/v1/public/biblios/{biblio_id}/items
	 */
	default Mono<KohaItem[]> getOpacVisibleItemsForBiblio(String biblioId, int page, int perPage) {
		return get("/api/v1/public/biblios/" + biblioId + "/items", KohaItem[].class,
			Map.of("_page", page, "_per_page", perPage));
	}

	/** Answers only when RESTPublicAPI and RESTPublicAnonymousRequests are both on. */
	default Mono<KohaLibrary[]> getOnePublicLibrary() {
		return get("/api/v1/public/libraries", KohaLibrary[].class, Map.of("_per_page", 1));
	}

	/**
	 * Delete an item.
	 * API: DELETE /api/v1/items/{item_id}
	 */
	default Mono<Void> deleteItem(String itemId) {
		return delete("/api/v1/items/" + itemId);
	}

	/**
	 * Update an item.
	 * API: PUT /api/v1/biblios/{biblio_id}/items/{item_id}
	 */
	default Mono<KohaItem> updateItem(String biblioId, String itemId, KohaItem item) {
		return put("/api/v1/biblios/" + biblioId + "/items/" + itemId, item, KohaItem.class);
	}

	/**
	 * Get active holds for an item.
	 * Filters out old (completed/cancelled) holds using the 'old=false' query parameter. This is used for the DCB hold count.
	 * We have something similar in Polaris, as we cannot trust the holdsCount variable to give us active holds.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/holds/operation/listHolds">List holds</a>
	 * </p>
	 */
	default Mono<Object[]> getActiveHoldsForItem(String itemId) {
		return get("/api/v1/holds", Object[].class, Map.of("item_id", itemId, "old", false));
	}

	/**
	 * Get active holds for a patron, for comparison against an agency's hold limit.
	 * Filters out old (completed/cancelled) holds using the 'old=false' query parameter.
	 * '_per_page=-1' disables pagination, otherwise Koha caps the response at its
	 * default page size (20) and the count would be wrong for exactly the patrons
	 * this exists to catch.
	 * <p>
	 * API docs: <a href="https://api.koha-community.org/#tag/holds/operation/listHolds">List holds</a>
	 * </p>
	 */
	default Mono<Object[]> getActiveHoldsForPatron(String patronId) {
		return get("/api/v1/holds", Object[].class,
			Map.of("patron_id", patronId, "old", false, "_per_page", -1));
	}


	/* Holds and checkouts **/

	/**
	 * Place a hold request.
	 * API: <a href="https://api.koha-community.org/#tag/holds/operation/addHold">...</a>
	 * Returns 201 on success, others non-successful (see API docs)
	 */
	default Mono<KohaHoldResponse> placeHoldRequest(KohaHoldRequest request) {
		return post("/api/v1/holds", request, KohaHoldResponse.class);
	}

	/**
	 * A hold placed past the patron's own blocks (expiry, debt, restriction, hold limit,
	 * lost card, bad address). Circulation rules still apply.
	 * API: POST /api/v1/holds with x-koha-override: any
	 */
	default Mono<KohaHoldResponse> placeHoldRequestOverridingPatronBlocks(KohaHoldRequest request) {
		return post("/api/v1/holds", request, KohaHoldResponse.class, Collections.emptyMap(),
			Map.of("x-koha-override", "any"));
	}

	/**
	 * Create a checkout.
	 * API: POST /api/v1/checkouts
	 */
	default Mono<KohaCheckout> checkoutItem(KohaCheckoutRequest request) {
		return post("/api/v1/checkouts", request, KohaCheckout.class);
	}

	/**
	 * The same checkout, answering the confirmations Koha asked for with the token the
	 * availability read issued.
	 * API: POST /api/v1/checkouts?confirmation={token}
	 */
	default Mono<KohaCheckout> checkoutItem(KohaCheckoutRequest request, String confirmationToken) {
		return post("/api/v1/checkouts", request, KohaCheckout.class, Map.of("confirmation", confirmationToken));
	}

	/**
	 * What Koha would block, ask staff to confirm, or warn about before this checkout.
	 * API: GET /api/v1/checkouts/availability?patron_id={id}&item_id={id}
	 */
	default Mono<KohaCheckoutAvailability> getCheckoutAvailability(String patronId, String itemId) {
		return get("/api/v1/checkouts/availability", KohaCheckoutAvailability.class,
			Map.of("patron_id", patronId, "item_id", itemId));
	}

	/**
	 * Cancel/Delete a hold request.
	 * API: DELETE /api/v1/holds/{hold_id}
	 */
	default Mono<Void> deleteHold(String holdId) {
		return delete("/api/v1/holds/" + holdId);
	}
	/**
	 * Renew a checkout.
	 * API: POST /api/v1/checkouts/{checkout_id}/renewals
	 */
	default Mono<KohaCheckout> renewCheckout(String checkoutId) {
		return post("/api/v1/checkouts/" + checkoutId + "/renewals", Map.of(), KohaCheckout.class);
	}

	/**
	 * The current (not checked in) checkouts for an item. An item on loan has exactly one.
	 * <p>
	 * API: GET /api/v1/checkouts?q={"item_id":&lt;item_id&gt;}
	 * <p>
	 * Koha's list endpoints take their filter as a JSON query in the q parameter; item_id is an
	 * integer in the checkout schema, so it is sent unquoted.
	 */
	default Mono<KohaCheckout[]> getCheckoutsForItem(String itemId) {
		return get("/api/v1/checkouts", KohaCheckout[].class,
			Map.of("q", "{\"item_id\":%s}".formatted(itemId)));
	}

	/**
	 * Ask Koha whether a checkout can be renewed, and why not.
	 * <p>
	 * API: GET /api/v1/checkouts/{checkout_id}/allows_renewal
	 * <p>
	 * Renewal eligibility comes from circulation rules and system preferences that live in the
	 * Koha instance, so this endpoint is the only authority on whether a renewal will be refused.
	 */
	default Mono<KohaRenewability> allowsRenewal(String checkoutId) {
		return get("/api/v1/checkouts/" + checkoutId + "/allows_renewal", KohaRenewability.class);
	}



}
