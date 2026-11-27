package org.olf.dcb.preferences;

import static org.olf.dcb.preferences.PreferenceVocabulary.APPS;
import static org.olf.dcb.preferences.PreferenceVocabulary.DENSITIES;
import static org.olf.dcb.preferences.PreferenceVocabulary.FIELD_KEY;
import static org.olf.dcb.preferences.PreferenceVocabulary.FONT_NAMES;
import static org.olf.dcb.preferences.PreferenceVocabulary.GRID_FIELDS;
import static org.olf.dcb.preferences.PreferenceVocabulary.GRID_KEYS;
import static org.olf.dcb.preferences.PreferenceVocabulary.LANGUAGES;
import static org.olf.dcb.preferences.PreferenceVocabulary.MAX_PAGE_SIZE;
import static org.olf.dcb.preferences.PreferenceVocabulary.MAX_PATRONS_IN_FILTER;
import static org.olf.dcb.preferences.PreferenceVocabulary.MIN_PAGE_SIZE;
import static org.olf.dcb.preferences.PreferenceVocabulary.MOTIONS;
import static org.olf.dcb.preferences.PreferenceVocabulary.PATRON_FIELD;
import static org.olf.dcb.preferences.PreferenceVocabulary.QUICK_FILTER_KEY;
import static org.olf.dcb.preferences.PreferenceVocabulary.TEXT_SIZES;
import static org.olf.dcb.preferences.PreferenceVocabulary.THEME_MODES;
import static org.olf.dcb.preferences.PreferenceVocabulary.VIEW_FIELDS;
import static org.olf.dcb.preferences.PreferenceVocabulary.describe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.core.api.serde.PreferenceInputs.SaveDisplay;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

/** Refuses with 400 rather than store something other than what was sent. */
@Singleton
public class PreferenceValidator {
	private final PreferencesConfig config;
	private final JsonMapper jsonMapper;

	public PreferenceValidator(PreferencesConfig config, JsonMapper jsonMapper) {
		this.config = config;
		this.jsonMapper = jsonMapper;
	}

	public SaveDisplay display(SaveDisplay input) {
		return new SaveDisplay(
			oneOf("themeMode", input.themeMode(), THEME_MODES),
			oneOf("textSize", input.textSize(), TEXT_SIZES),
			oneOf("density", input.density(), DENSITIES),
			oneOf("fontName", input.fontName(), FONT_NAMES),
			oneOf("motion", input.motion(), MOTIONS),
			oneOf("language", input.language(), LANGUAGES));
	}

	public String app(String app) {
		if (app == null || !APPS.contains(app)) {
			throw invalid("app must be one of: " + describe(APPS));
		}

		return app;
	}

	/** One app's slice, as JSON to store. Its grids are checked as a saved view's query is. */
	public String appSlice(String app, @Nullable Map<String, Object> slice) {
		final var state = slice != null ? slice : Map.<String, Object>of();
		rejectQuickFilter(state);

		if (state.get("grids") instanceof Map<?, ?> grids) {
			if (grids.size() > config.getMaxGrids()) {
				throw invalid("At most " + config.getMaxGrids() + " grids are remembered; drop the least recently used");
			}

			grids.forEach((gridId, entry) -> grid(app, String.valueOf(gridId), entry));
		}
		else if (state.containsKey("grids")) {
			throw invalid("grids must be an object keyed by grid id");
		}

		return serialise(state, config.getMaxAppStateBytes(), "appState");
	}

	public String viewType(String viewType) {
		if (viewType == null || !VIEW_FIELDS.containsKey(viewType)) {
			throw invalid("viewType must be one of: " + describe(VIEW_FIELDS.keySet()));
		}

		return viewType;
	}

	public Map<String, Object> queryState(String viewType, @Nullable Map<String, Object> queryState) {
		final var state = queryState != null ? queryState : Map.<String, Object>of();
		rejectQuickFilter(state);
		checkFields(state, VIEW_FIELDS.get(viewType), "A saved view for " + viewType);
		serialise(state, config.getMaxQueryStateBytes(), "queryState");
		return state;
	}

	private void grid(String app, String gridId, Object entry) {
		if (!(entry instanceof Map<?, ?> model)) {
			throw invalid("Grid " + gridId + " must be an object");
		}

		final var unknown = model.keySet().stream().map(String::valueOf).filter(key -> !GRID_KEYS.contains(key)).sorted().toList();
		if (!unknown.isEmpty()) {
			throw invalid("Grid " + gridId + " carries " + unknown + "; a grid remembers only " + describe(GRID_KEYS));
		}

		final var pageSize = model.get("pageSize");
		if (pageSize != null && !(pageSize instanceof Number size
			&& size.intValue() >= MIN_PAGE_SIZE && size.intValue() <= MAX_PAGE_SIZE)) {
			throw invalid("Grid " + gridId + " pageSize must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
		}

		checkFields(model.get("filterModel"), GRID_FIELDS.get(app), "Grid " + gridId);
		checkFields(model.get("sortModel"), GRID_FIELDS.get(app), "Grid " + gridId);
	}

	private void checkFields(Object node, Set<String> permitted, String what) {
		if (node instanceof Map<?, ?> map) {
			if (map.get(FIELD_KEY) instanceof String field) {
				if (!permitted.contains(field)) {
					throw invalid(what + " cannot remember a filter or sort on '" + field
						+ "'. A patron is remembered as '" + PATRON_FIELD + "' with their ids; text people wrote is not remembered.");
				}

				if (PATRON_FIELD.equals(field)) {
					patronIds(map.get("value"), what);
				}
			}

			map.values().forEach(value -> checkFields(value, permitted, what));
		}
		else if (node instanceof Collection<?> collection) {
			collection.forEach(element -> checkFields(element, permitted, what));
		}
	}

	private static void patronIds(Object value, String what) {
		final List<?> ids = value instanceof Collection<?> many ? List.copyOf(many) : value == null ? List.of() : List.of(value);

		if (ids.size() > MAX_PATRONS_IN_FILTER) {
			throw invalid(what + " remembers at most " + MAX_PATRONS_IN_FILTER + " patrons in one filter");
		}

		for (Object id : ids) {
			try {
				UUID.fromString(String.valueOf(id));
			}
			catch (IllegalArgumentException e) {
				throw invalid(what + " remembers a patron only by their id, never a barcode");
			}
		}
	}

	private static void rejectQuickFilter(Object node) {
		if (node instanceof Map<?, ?> map) {
			final var quick = map.get(QUICK_FILTER_KEY);
			if (quick != null && !(quick instanceof Collection<?> values && values.isEmpty())) {
				throw invalid("Quick filter text is not remembered: it can hold anything, a barcode included");
			}

			map.values().forEach(PreferenceValidator::rejectQuickFilter);
		}
		else if (node instanceof Collection<?> collection) {
			collection.forEach(PreferenceValidator::rejectQuickFilter);
		}
	}

	@Nullable
	private static String oneOf(String name, @Nullable String value, Set<String> allowed) {
		if (value == null || value.isBlank()) {
			return null;
		}

		if (!allowed.contains(value)) {
			throw invalid(name + " must be one of: " + describe(allowed) + ", or absent");
		}

		return value;
	}

	private String serialise(Map<String, Object> value, int maxBytes, String field) {
		final byte[] json;

		try {
			json = jsonMapper.writeValueAsBytes(value);
		}
		catch (IOException e) {
			throw invalid(field + " could not be written as JSON");
		}

		if (json.length > maxBytes) {
			throw invalid(field + " is " + json.length + " bytes, over the " + maxBytes + " byte limit");
		}

		return new String(json, StandardCharsets.UTF_8);
	}

	private static PreferenceException invalid(String message) {
		return PreferenceException.invalid(message);
	}
}
