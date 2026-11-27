package org.olf.dcb.preferences;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveDisplay;

import io.micronaut.json.JsonMapper;

/** What may be stored: display values from fixed vocabularies, and grids that hold no patron data. */
class PreferenceValidatorTests {
	private PreferenceValidator validator;

	@BeforeEach
	void setUp() {
		final var config = new PreferencesConfig();
		config.setSecret("irrelevant-here");
		config.setMaxGrids(3);
		validator = new PreferenceValidator(config, JsonMapper.createDefault());
	}

	@Test
	void displayValuesComeFromTheVocabulariesAndBlankIsNotChosen() {
		final var display = validator.display(new SaveDisplay("highContrast", "largest", "compact",
			"atkinsonHyperlegible", "reduced", " "));

		assertThat(display.themeMode(), is("highContrast"));
		assertThat(display.language(), is(nullValue()));
		assertThrows(PreferenceException.class,
			() -> validator.display(new SaveDisplay("purple", null, null, null, null, null)));
	}

	@Test
	void aGridRemembersFiltersSortColumnsAndPageSizeOnly() {
		assertDoesNotThrow(() -> validator.appSlice("libraries", slice(grid(
			"filterModel", filter("status", "is", "LOANED"),
			"sortModel", List.of(Map.of("field", "dateCreated", "sort", "desc")),
			"columnVisibilityModel", Map.of("itemBarcode", false),
			"pageSize", 50))));

		assertThat(refusal("libraries", slice(grid("page", 3))), containsString("remembers only"));
		assertThat(refusal("libraries", slice(grid("pageSize", 200))), containsString("pageSize"));
	}

	@Test
	void aPatronIsRememberedByIdNeverByBarcode() {
		assertThat(refusal("libraries", slice(grid("filterModel", filter("patronBarcode", "equals", "21000000000001")))),
			containsString("patronBarcode"));
		assertThat(refusal("libraries", slice(grid("filterModel", filter("patron", "isAnyOf", List.of("21000000000001"))))),
			containsString("never a barcode"));
		assertDoesNotThrow(() -> validator.appSlice("libraries",
			slice(grid("filterModel", filter("patron", "isAnyOf", List.of(UUID.randomUUID().toString()))))));
	}

	@Test
	void textPeopleWroteAndQuickFilterTextAreNotRemembered() {
		assertThat(refusal("admin", slice(grid("filterModel", filter("requesterNote", "contains", "my sister")))),
			containsString("requesterNote"));
		assertThat(refusal("admin", slice(grid("filterModel",
			Map.of("items", List.of(), "quickFilterValues", List.of("2100000"))))), containsString("Quick filter"));
	}

	@Test
	void aSliceHoldsABoundedNumberOfGrids() {
		final Map<String, Object> grids = new HashMap<>();
		for (int index = 0; index < 4; index++) {
			grids.put("grid" + index, Map.of("pageSize", 25));
		}

		assertThat(refusal("libraries", Map.of("grids", grids)), containsString("At most 3"));
	}

	@Test
	void aSavedFilterIsOfferedOnTheRequestGridsOnly() {
		assertDoesNotThrow(() -> validator.viewType("libraries.borrowing"));
		assertThrows(PreferenceException.class, () -> validator.viewType("libraries.mappings"));
		assertThrows(PreferenceException.class, () -> validator.queryState("libraries.supplying",
			filter("patronBarcode", "equals", "2100000")));
	}

	@Test
	void anUnknownAppIsRefused() {
		assertThrows(PreferenceException.class, () -> validator.app("symphony"));
	}

	private String refusal(String app, Map<String, Object> slice) {
		return assertThrows(PreferenceException.class, () -> validator.appSlice(app, slice)).getMessage();
	}

	private static Map<String, Object> slice(Map<String, Object> grid) {
		return Map.of("grids", Map.of("patronRequests", grid));
	}

	private static Map<String, Object> grid(Object... keysAndValues) {
		final Map<String, Object> grid = new HashMap<>();
		for (int index = 0; index < keysAndValues.length; index += 2) {
			grid.put((String) keysAndValues[index], keysAndValues[index + 1]);
		}
		return grid;
	}

	private static Map<String, Object> filter(String field, String operator, Object value) {
		return Map.of("items", List.of(Map.of("field", field, "operator", operator, "value", value)));
	}
}
