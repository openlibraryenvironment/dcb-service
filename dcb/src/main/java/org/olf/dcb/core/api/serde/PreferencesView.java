package org.olf.dcb.core.api.serde;

import java.time.Instant;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** The caller's stored preferences. A null display field follows the device or the deployment. */
@Serdeable
public record PreferencesView(
	@Nullable String themeMode,
	@Nullable String textSize,
	@Nullable String density,
	@Nullable String fontName,
	@Nullable String motion,
	@Nullable String language,
	Map<String, Object> appState,
	Instant dateUpdated) {
}
