package org.olf.dcb.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;
import static org.olf.dcb.security.RoleNames.INTEROP_TESTER;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.PreferenceAvailability;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveDisplay;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveView;
import org.olf.dcb.core.api.serde.PreferencesView;
import org.olf.dcb.core.api.serde.SavedViewView;
import org.olf.dcb.security.AgencyClaims;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

/** The preferences API from outside. Two people throughout: each only ever reaches their own. */
@DcbTest
@TestInstance(PER_CLASS)
class AdminPreferenceApiTests {
	private static final String ALICE = "preferences-tests-alice";
	private static final String BOB = "preferences-tests-bob";
	private static final String NO_SUBJECT = "preferences-tests-no-subject";
	private static final String DISCOVERY = "preferences-tests-discovery";
	private static final String INTEROP = "preferences-tests-interop";

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ALICE, "alice", List.of(ADMINISTRATOR), Map.of("sub", "alice-subject-0001"));
		TestStaticTokenValidator.add(BOB, "bob", List.of(LIBRARY_READ_ONLY),
			Map.of("sub", "bob-subject-0002", AgencyClaims.CODE, List.of("PREF_AG")));
		TestStaticTokenValidator.add(NO_SUBJECT, "no-subject", List.of(ADMINISTRATOR), Map.of("preferred_username", "x"));
		TestStaticTokenValidator.add(DISCOVERY, "discovery", List.of(DISCOVERY_SERVICE), Map.of("sub", "discovery-0003"));
		TestStaticTokenValidator.add(INTEROP, "interop", List.of(INTEROP_TESTER), Map.of("sub", "interop-0004"));
	}

	@AfterAll
	void afterAll() {
		forget(ALICE);
		forget(BOB);
		List.of(ALICE, BOB, NO_SUBJECT, DISCOVERY, INTEROP).forEach(TestStaticTokenValidator::invalidateToken);
	}

	@BeforeEach
	void beforeEach() {
		forget(ALICE);
		forget(BOB);
	}

	@Test
	void availabilitySaysPreferencesAreOnAndGivesTheLimits() {
		final var availability = client.toBlocking().retrieve(
			HttpRequest.GET("/preferences/availability").bearerAuth(ALICE), PreferenceAvailability.class);

		assertThat(availability.available(), is(true));
		assertThat(availability.maxWatched(), is(200));
	}

	@Test
	void someoneWhoHasStoredNothingGetsNotFound() {
		assertThat(statusOf(HttpRequest.GET("/preferences"), ALICE), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void displaySettingsAreStoredWholeAndANullIsNotTheDefault() {
		put("/preferences/display", new SaveDisplay("highContrast", "large", null, "lexend", "reduced", "en-US"), ALICE);
		final var stored = put("/preferences/display", new SaveDisplay(null, "large", null, null, null, null), ALICE);

		assertThat(stored.themeMode(), is(nullValue()));
		assertThat(stored.textSize(), is("large"));
		assertThat(stored.fontName(), is(nullValue()));
		assertThat(statusOf(HttpRequest.PUT("/preferences/display",
			new SaveDisplay("purple", null, null, null, null, null)), ALICE), is(HttpStatus.BAD_REQUEST));
	}

	@Test
	void eachAppWritesOnlyItsOwnSlice() {
		put("/preferences/apps/admin", Map.of("sidebar", Map.of("open", false)), ALICE);
		final var stored = put("/preferences/apps/libraries", Map.of("clock", "service"), ALICE);

		assertThat(stored.appState().get("admin"), is(Map.of("sidebar", Map.of("open", false))));
		assertThat(stored.appState().get("libraries"), is(Map.of("clock", "service")));
		assertThat(statusOf(HttpRequest.PUT("/preferences/apps/symphony", Map.of()), ALICE), is(HttpStatus.BAD_REQUEST));
	}

	@Test
	void aGridWithABarcodeFilterIsRefusedAndTheReasonSaysWhy() {
		final var error = assertThrows(HttpClientResponseException.class, () -> put("/preferences/apps/libraries",
			Map.of("grids", Map.of("patronRequests", Map.of("filterModel", Map.of("items",
				List.of(Map.of("field", "patronBarcode", "operator", "equals", "value", "21000000000001")))))), ALICE));

		assertThat(error.getStatus(), is(HttpStatus.BAD_REQUEST));
		assertThat(error.getResponse().getBody(String.class).orElse(""), containsString("patronBarcode"));
	}

	@Test
	void aReadOnlyLibraryUserMayKeepTheirOwnSettings() {
		assertThat(put("/preferences/display", new SaveDisplay("dark", null, null, null, null, null), BOB).themeMode(),
			is("dark"));
	}

	@Test
	void savedFiltersBelongToTheirOwnerAlone() {
		final var view = post("/preferences/views", new SaveView("Overdue loans", "libraries.borrowing",
			Map.of("filterModel", Map.of("items", List.of(Map.of("field", "status", "operator", "is", "value", "LOANED"))))), ALICE);

		assertThat(views(ALICE), hasSize(1));
		assertThat(views(BOB), hasSize(0));
		assertThat(statusOf(HttpRequest.PUT("/preferences/views/" + view.id(),
			new SaveView("Mine now", "libraries.borrowing", Map.of())), BOB), is(HttpStatus.NOT_FOUND));
		client.toBlocking().exchange(HttpRequest.DELETE("/preferences/views/" + view.id()).bearerAuth(BOB));
		assertThat(views(ALICE), hasSize(1));
	}

	@Test
	void aSecondFilterWithTheSameNameOnTheSameGridIsAConflict() {
		post("/preferences/views", new SaveView("Overdue", "libraries.borrowing", Map.of()), ALICE);

		assertThat(statusOf(HttpRequest.POST("/preferences/views",
			new SaveView("overdue", "libraries.borrowing", Map.of())), ALICE), is(HttpStatus.CONFLICT));
	}

	@Test
	void forgettingRemovesSettingsAndSavedFilters() {
		put("/preferences/display", new SaveDisplay("dark", null, null, null, null, null), ALICE);
		post("/preferences/views", new SaveView("Overdue", "libraries.borrowing", Map.of()), ALICE);

		forget(ALICE);

		assertThat(statusOf(HttpRequest.GET("/preferences"), ALICE), is(HttpStatus.NOT_FOUND));
		assertThat(views(ALICE), hasSize(0));
	}

	@Test
	void aTokenWithNoSubjectStoresNothing() {
		assertThat(statusOf(HttpRequest.PUT("/preferences/display",
			new SaveDisplay("dark", null, null, null, null, null)), NO_SUBJECT), is(HttpStatus.BAD_REQUEST));
	}

	@Test
	void discoveryServicesAndInteropTestersAreRefused() {
		for (String token : List.of(DISCOVERY, INTEROP)) {
			assertThat(statusOf(HttpRequest.GET("/preferences/availability"), token),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
			assertThat(statusOf(HttpRequest.PUT("/preferences/watched/" + UUID.randomUUID(), Map.of()), token),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	private PreferencesView put(String uri, Object body, String token) {
		return client.toBlocking().retrieve(HttpRequest.PUT(uri, body).bearerAuth(token), PreferencesView.class);
	}

	private SavedViewView post(String uri, Object body, String token) {
		return client.toBlocking().retrieve(HttpRequest.POST(uri, body).bearerAuth(token), SavedViewView.class);
	}

	private List<SavedViewView> views(String token) {
		return client.toBlocking().retrieve(HttpRequest.GET("/preferences/views").bearerAuth(token),
			Argument.listOf(SavedViewView.class));
	}

	private HttpStatus statusOf(MutableHttpRequest<?> request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request.bearerAuth(token), String.class)).getStatus();
	}

	private void forget(String token) {
		client.toBlocking().exchange(HttpRequest.DELETE("/preferences").bearerAuth(token));
	}
}
