package org.olf.dcb.preferences;

import io.micronaut.context.annotation.Context;
import lombok.extern.slf4j.Slf4j;

/** Says once at startup whether preferences are on. Never fails startup: the feature is not core. */
@Slf4j
@Context
public class PreferencesStartupCheck {
	public PreferencesStartupCheck(PreferencesConfig config) {
		if (config.isExplicitlyDisabled()) {
			log.info("Staff preferences are off by configuration (dcb.preferences.enabled=false)");
		}
		else if (config.isOn()) {
			log.info("Staff preferences are on");
		}
		else {
			log.warn("Staff preferences are off: DCB_PREFERENCES_SECRET is not set, so settings stay in "
				+ "each browser. Set dcb.preferences.enabled=false to silence this.");
		}
	}
}
