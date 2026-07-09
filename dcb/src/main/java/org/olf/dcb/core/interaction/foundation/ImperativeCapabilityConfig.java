package org.olf.dcb.core.interaction.foundation;

import java.util.Map;

import org.olf.dcb.core.model.HostLms;

/**
 * Reads the nested {@code capabilities.imperative} block from a host's
 * clientConfig - the Foundation protocol-composition scope of the unified
 * capability schema:
 *
 * <pre>
 * capabilities:
 *   borrowing-agency-request: { strategy: imperative }   # outer lifecycle scope (LifecycleCapabilityResolver)
 *   imperative:                                           # inner Foundation scope (this helper)
 *     base-protocol: NCIP
 *     ncip-endpoint-url: https://host/ncip
 *     overrides: { renew: EvergreenExampleCustomOverride }
 * </pre>
 *
 * Settings fall back to the top level of clientConfig for backward
 * compatibility with pre-unification host configuration.
 */
public final class ImperativeCapabilityConfig {
	private ImperativeCapabilityConfig() {
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> block(HostLms lms) {
		if (lms == null) {
			return Map.of();
		}

		final var config = lms.getClientConfig();
		if (config == null) {
			return Map.of();
		}

		if (config.get("capabilities") instanceof Map<?, ?> capabilities
			&& capabilities.get("imperative") instanceof Map<?, ?> imperative) {

			return (Map<String, Object>) imperative;
		}

		return Map.of();
	}

	/**
	 * Value for {@code key} from the imperative block, falling back to the same
	 * key at the top level of clientConfig, or null.
	 */
	public static Object setting(HostLms lms, String key) {
		final var block = block(lms);
		if (block.containsKey(key)) {
			return block.get(key);
		}

		final var config = lms != null ? lms.getClientConfig() : null;
		return config != null ? config.get(key) : null;
	}
}
