package services.k_int.interaction.alma;

import io.micronaut.http.MediaType;
import org.olf.dcb.core.interaction.alma.AlmaHostLmsClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.AlmaUserList;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.holdings.AlmaHoldings;
import services.k_int.interaction.alma.types.items.*;
import services.k_int.interaction.alma.types.userRequest.AlmaRequest;
import services.k_int.interaction.alma.types.userRequest.AlmaRequestResponse;

import java.util.Collections;
import java.util.Map;

/**
 * Provides core HTTP operations and strongly-typed endpoint methods with manual path definitions.
 */
public interface AlmaApiClient {

	// --- Core HTTP methods ---
	<T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams);
	<T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams);
	<T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams, String contentType);
	<T> Mono<T> put(String path, Object body, Class<T> responseType, Map<String, Object> queryParams);
	Mono<Void> delete(String path, Map<String, Object> queryParams);

	// --- Overloads (no queryParams) ---
	default <T> Mono<T> get(String path, Class<T> responseType) {
		return get(path, responseType, Collections.emptyMap());
	}
	default <T> Mono<T> post(String path, Object body, Class<T> responseType) {
		return post(path, body, responseType, Collections.emptyMap());
	}
	default <T> Mono<T> postXml(String path, Object body, Class<T> responseType) {
		return post(path, body, responseType, Collections.emptyMap(), MediaType.APPLICATION_XML);
	}
	default <T> Mono<T> put(String path, Object body, Class<T> responseType) {
		return put(path, body, responseType, Collections.emptyMap());
	}
	default Mono<Void> delete(String path) {
		return delete(path, Collections.emptyMap());
	}

	/**
	 * List all users.
	 * <p>
	 * API: GET /almaws/v1/users
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/R0VUIC9hbG1hd3MvdjEvdXNlcnM=/
	 */
	default Mono<AlmaUserList> retrieveUsers() {
		return get("/almaws/v1/users", AlmaUserList.class);
	}

	/**
	 * Authenticate or refresh a user.
	 * <p>
	 * API: GET /almaws/v1/users/{user_id}?password={password}
	 * Docs: <a href="https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJzL3t1c2VyX2lkfQ==/">Alma API Doc</a>
	 */
	default Mono<AlmaUser> authenticateOrRefreshUser(String user_id, String password) {
		return get("/almaws/v1/users/"  + user_id, AlmaUser.class, Map.of("password", password));
	}

	/**
	 * Search users by external ID.
	 * <p>
	 * API: GET /almaws/v1/users?q=external_id~{externalId}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/R0VUIC9hbG1hd3MvdjEvdXNlcnM=/
	 */
	default Mono<AlmaUserList> getUsersByExternalId(String externalId) {
		return get("/almaws/v1/users", AlmaUserList.class, Map.of("q", "external_id~" + externalId));
	}

	/**
	 * Get user details.
	 * <p>
	 * API: GET /almaws/v1/users/{user_id}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/R0VUIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9/
	 */
	default Mono<AlmaUser> getUserDetails(String user_id) {
		return get("/almaws/v1/users/" + user_id, AlmaUser.class);
	}

	/**
	 * Create a new user.
	 * <p>
	 * API: POST /almaws/v1/users
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJz/
	 */
	default Mono<AlmaUser> createUser(AlmaUser user) {
		return post("/almaws/v1/users", user, AlmaUser.class);
	}

	/**
	 * Update existing user details.
	 * <p>
	 * API: PUT /almaws/v1/users/{userId}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJz/
	 */
	default Mono<AlmaUser> updateUserDetails(String userId, AlmaUser patron) {
		return put("/almaws/v1/users/" + userId, patron, AlmaUser.class);
	}

	/**
	 * Delete a user.
	 * <p>
	 * API: DELETE /almaws/v1/users/{user_id}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/REVMRVRFIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9/
	 */
	default Mono<String> deleteUser(String user_id) {
		return delete("/almaws/v1/users/" + user_id)
			.thenReturn("User deleted");
	}

	/**
	 * Delete a bib record.
	 * <p>
	 * API: DELETE /almaws/v1/bibs/{mms_id}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/REVMRVRFIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfQ==/
	 */
	default Mono<String> deleteBibRecord(String mms_id) {
		return delete("/almaws/v1/bibs/" + mms_id, Map.of("override", true))
			.thenReturn("Bib deleted");
	}

	/**
	 * Delete an item within a holding.
	 * <p>
	 * API: DELETE /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/REVMRVRFIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfS9ob2xkaW5ncy97aG9sZGluZ19pZH0vaXRlbXMve2l0ZW1fcGlkfQ==/
	 */
	default Mono<String> withdrawItem(String mms_id, String holding_id, String item_pid) {
		final String path = "/almaws/v1/bibs/" + mms_id + "/holdings/" + holding_id + "/items/" + item_pid;
		return delete(path, Map.of("override", true))
			.thenReturn("Item deleted");
	}

	/**
	 * Delete Holdings Record.
	 * <p>
	 * API: DELETE /almaws/v1/bibs/{mms_id}/holdings/{holding_id}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/REVMRVRFIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfS9ob2xkaW5ncy97aG9sZGluZ19pZH0=/
	 */
	default Mono<String> deleteHoldingsRecord(String mms_id, String holding_id) {
		return delete("/almaws/v1/bibs/" + mms_id + "/holdings/" + holding_id, Map.of("override", true))
			.thenReturn("Holding deleted");
	}

	/**
	 * Retrieve all holdings for a bib.
	 * <p>
	 * API: GET /almaws/v1/bibs/{mms_id}/holdings
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/R0VUIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfS9ob2xkaW5ncw==/
	 */
	default Mono<AlmaHoldings> retrieveHoldingsList(String mms_id) {
		return get("/almaws/v1/bibs/" + mms_id + "/holdings", AlmaHoldings.class);
	}

	/**
	 * Retrieve items for a specific holding.
	 * <p>
	 * API: GET /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/R0VUIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfS9ob2xkaW5ncy97aG9sZGluZ19pZH0vaXRlbXM=/
	 */
	default Mono<AlmaItems> retrieveItemsList(String mms_id, String holding_id) {
		return get("/almaws/v1/bibs/" + mms_id + "/holdings/" + holding_id + "/items", AlmaItems.class);
	}

	/**
	 * Retrieve Item and label printing information.
	 * <p>
	 * API: GET /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/R0VUIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfS9ob2xkaW5ncy97aG9sZGluZ19pZH0vaXRlbXMve2l0ZW1fcGlkfQ==/
	 */
	default Mono<AlmaItem> retrieveItem(String mms_id, String holding_id, String item_pid) {
		return get("/almaws/v1/bibs/" + mms_id + "/holdings/" + holding_id + "/items/" + item_pid, AlmaItem.class);
	}

	/**
	 * 	This appears to be possible but is not documented. Use only if no other option
	 */

	default Mono<AlmaItem> retrieveItemBarcodeOnly(String item_barcode) {
		return get("/almaws/v1/items", AlmaItem.class, Map.of("item_barcode", item_barcode));
	}

	/**
	 * Place a hold request for a user.
	 * <p>
	 * API: POST /almaws/v1/users/{user_id}/requests?item_pid={item_pid}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJzL3t1c2VyX2lkfS9yZXF1ZXN0cw==/
	 */
	default Mono<AlmaRequestResponse> createUserRequest(String user_id, String item_pid, AlmaRequest almaRequest) {
		return post("/almaws/v1/users/" + user_id + "/requests", almaRequest,
			AlmaRequestResponse.class, Map.of("item_pid", item_pid));
	}

	/**
	 * Retrieve a specific user request.
	 * <p>
	 * API: GET /almaws/v1/users/{user_id}/requests/{request_id}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/R0VUIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9L3JlcXVlc3RzL3tyZXF1ZXN0X2lkfQ==/
	 */
	default Mono<AlmaRequestResponse> retrieveUserRequest(String user_id, String request_id) {
		return get("/almaws/v1/users/" + user_id + "/requests/" + request_id, AlmaRequestResponse.class);
	}

	/**
	 * Delete user request.
	 * <p>
	 * API: DELETE /almaws/v1/users/{user_id}/requests/{request_id}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/REVMRVRFIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9L3JlcXVlc3RzL3tyZXF1ZXN0X2lkfQ==/
	 */
	default Mono<String> cancelUserRequest(String user_id, String request_id) {
		final String path = "/almaws/v1/users/" + user_id + "/requests/" + request_id;
		return delete(path, Map.of("override", true))
			.thenReturn("Request deleted");
	}

	/**
	 * Fetches all loans for a specific user.
	 * <p>
	 * API: GET /almaws/v1/users/{user_id}/loans
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/R0VUIC9hbG1hd3MvdjEvdXNlcnMve3VzZXJfaWR9L2xvYW5z/
	 */
	default Mono<AlmaItemLoans> retrieveUserLoans(String user_id) {
		return get("/almaws/v1/users/" + user_id + "/loans", AlmaItemLoans.class);
	}

	/**
	 * Create a loan for a user.
	 * <p>
	 * API: POST /almaws/v1/users/{user_id}/loans?item_pid={item_pid}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJzL3t1c2VyX2lkfS9sb2Fucw==/
	 */
	default Mono<AlmaItemLoanResponse> createUserLoan(String user_id, String item_pid, AlmaItemLoan loan) {
		return post("/almaws/v1/users/" + user_id + "/loans", loan,
			AlmaItemLoanResponse.class, Map.of("item_pid", item_pid));
	}

	/**
	 * Renew a loan.
	 * <p>
	 * API: POST /almaws/v1/users/{user_id}/loans/{loan_id}?op=renew
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJzL3t1c2VyX2lkfS9sb2Fucy97bG9hbl9pZH0=/
	 */
	default Mono<AlmaItemLoan> renewLoan(String user_id, String loan_id) {
		return post("/almaws/v1/users/" + user_id + "/loans/" + loan_id,
			null, AlmaItemLoan.class, Map.of("op", "renew"));
	}

	/**
	 * List all libraries.
	 * <p>
	 * API: GET /almaws/v1/conf/libraries
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/conf/R0VUIC9hbG1hd3MvdjEvY29uZi9saWJyYXJpZXM=/
	 */
	default Mono<AlmaLibrariesResponse> retrieveLibraries() {
		return get("/almaws/v1/conf/libraries", AlmaLibrariesResponse.class);
	}

	/**
	 * List locations for a library.
	 * <p>
	 * API: GET /almaws/v1/conf/libraries/{libraryCode}/locations
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/libraries/
	 */
	default Mono<AlmaLocationResponse> retrieveLocations(String libraryCode) {
		return get("/almaws/v1/conf/libraries/" + libraryCode + "/locations", AlmaLocationResponse.class);
	}

	/**
	 * Create a new bib record.
	 * <p>
	 * API: POST /almaws/v1/bibs
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/UE9TVCAvYWxtYXdzL3YxL2JpYnM=/
	 */
	default Mono<AlmaBib> createBibRecord(String bibXml) {
		return postXml("/almaws/v1/bibs", bibXml, AlmaBib.class);
	}

	/**
	 * Create a new holding under a bib.
	 * <p>
	 * API: POST /almaws/v1/bibs/{mms_id}/holdings
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/UE9TVCAvYWxtYXdzL3YxL2JpYnMve21tc19pZH0vaG9sZGluZ3M=/
	 */
	default Mono<AlmaHolding> createHoldingRecord(String mms_id, String holdingXml) {
		return postXml("/almaws/v1/bibs/" + mms_id + "/holdings", holdingXml, AlmaHolding.class);
	}

	/**
	 * Create a new item under a holding.
	 * <p>
	 * API: POST /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/UE9TVCAvYWxtYXdzL3YxL2JpYnMve21tc19pZH0vaG9sZGluZ3Mve2hvbGRpbmdfaWR9L2l0ZW1z/
	 */
	default Mono<AlmaItem> createItem(String mms_id, String holding_id, AlmaItem item) {
		return post("/almaws/v1/bibs/" + mms_id + "/holdings/" + holding_id + "/items", item, AlmaItem.class);
	}

	/**
	 * Update Item information.
	 * <p>
	 * API: PUT /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/UFVUIC9hbG1hd3MvdjEvYmlicy97bW1zX2lkfS9ob2xkaW5ncy97aG9sZGluZ19pZH0vaXRlbXMve2l0ZW1fcGlkfQ==/
	 */
	default Mono<AlmaItem> updateItem(String mms_id, String holding_id, String item_pid, AlmaItem item) {
		return put("/almaws/v1/bibs/" + mms_id + "/holdings/" + holding_id + "/items/" + item_pid, item, AlmaItem.class);
	}

	/**
	 * Test connectivity.
	 * <p>
	 * API: GET /almaws/v1/conf/test
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/conf/R0VUIC9hbG1hd3MvdjEvY29uZi90ZXN0/
	 */
	default Mono<String> test() {
		return get("/almaws/v1/conf/test", String.class);
	}

	/**
	 * Scan-in operation on item.
	 * <p>
	 * API: POST /almaws/v1/bibs/{mms_id}/holdings/{holding_id}/items/{item_pid}
	 * Docs: https://developers.exlibrisgroup.com/alma/apis/docs/bibs/UE9TVCAvYWxtYXdzL3YxL2JpYnMve21tc19pZH0vaG9sZGluZ3Mve2hvbGRpbmdfaWR9L2l0ZW1zL3tpdGVtX3BpZH0=/
	 */
	default Mono<AlmaItem> scanIn(AlmaHostLmsClient.ScanInQuery query) {
		return post(
			"/almaws/v1/bibs/" + query.mms_id() + "/holdings/" + query.holding_id() + "/items/" + query.item_pid(),
			null,
			AlmaItem.class,
			Map.of("op", "scan", "library", query.library(), "circ_desk", query.circ_desk()));
	}
}
