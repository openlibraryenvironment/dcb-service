package org.olf.dcb.core.api.serde;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for /preferences. None carries an owner: that is always the caller. */
public final class PreferenceInputs {
	private PreferenceInputs() {
	}

	/** The display settings, whole. A null field is "not chosen", which is not the default. */
	@Serdeable
	public record SaveDisplay(
		@Nullable @Size(max = 16) String themeMode,
		@Nullable @Size(max = 16) String textSize,
		@Nullable @Size(max = 16) String density,
		@Nullable @Size(max = 32) String fontName,
		@Nullable @Size(max = 16) String motion,
		@Nullable @Size(max = 16) String language) {
	}

	@Serdeable
	public record SaveView(
		@NotBlank @Size(max = 128) String name,
		@NotBlank @Size(max = 48) String viewType,
		@Nullable Map<String, Object> queryState) {
	}
}
