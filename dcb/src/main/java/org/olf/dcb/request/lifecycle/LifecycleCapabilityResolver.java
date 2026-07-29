package org.olf.dcb.request.lifecycle;

import io.micronaut.context.annotation.Prototype;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.HostLms;

/**
 * Resolves the lifecycle placement strategy / tracking mode for a given role.
 *
 * Precedence, highest first:
 *   1. The host's own {@code clientConfig.capabilities} block (per-host).
 *   2. The instance-wide {@link LifecycleCapabilitiesConfiguration} (spike default).
 *   3. Imperative placement / scheduled-poll tracking.
 *
 * Per-host resolution is what allows profiles A-D to coexist in one consortium:
 * one host can be imperative while another is declarative in the same run.
 */
@Slf4j
@Prototype
public class LifecycleCapabilityResolver {
	private final LifecycleCapabilitiesConfiguration configuration;

	public LifecycleCapabilityResolver(
		LifecycleCapabilitiesConfiguration configuration) {

		this.configuration = configuration;
	}

	// ---- Instance-wide resolution (retained as fallback / for callers without a host) ----

	public StrategyType placementStrategy(LifecycleRole role) {
		return placementStrategy(null, role);
	}

	public String placementProtocol(LifecycleRole role) {
		return placementProtocol(null, role);
	}

	public TrackingMode trackingMode(LifecycleRole role) {
		return trackingMode(null, role);
	}

	// ---- Host-scoped resolution ----

	public StrategyType placementStrategy(HostLms hostLms, LifecycleRole role) {
		final var hostCapability = hostPlacementCapability(hostLms, role);

		final StrategyType strategy;
		final String protocol;

		if (hostCapability != null) {
			strategy = hostCapability.strategy() != null
				? hostCapability.strategy()
				: StrategyType.IMPERATIVE;
			protocol = hostCapability.protocol();
		} else {
			final var capability = globalPlacementCapability(role);
			strategy = defaultPlacementStrategy(capability);
			protocol = capability != null ? capability.getProtocol() : null;
		}

		log.info("DCB-LIFECYCLE-CAPABILITY: host={} role={} placementStrategy={} protocol={}",
			hostLms != null ? hostLms.getCode() : "<global>", role, strategy, protocol);

		if (strategy == StrategyType.DECLARATIVE) {
			requireProtocol(role, protocol, "placement");
		}

		return strategy;
	}

	public String placementProtocol(HostLms hostLms, LifecycleRole role) {
		final var hostCapability = hostPlacementCapability(hostLms, role);

		if (hostCapability != null) {
			return hostCapability.protocol();
		}

		final var capability = globalPlacementCapability(role);
		return capability != null ? capability.getProtocol() : null;
	}

	public TrackingMode trackingMode(HostLms hostLms, LifecycleRole role) {
		final var hostCapability = hostTrackingCapability(hostLms, role);

		final TrackingMode trackingMode;
		final String protocol;

		if (hostCapability != null) {
			trackingMode = hostCapability.mode() != null
				? hostCapability.mode()
				: TrackingMode.SCHEDULED_POLL;
			protocol = hostCapability.protocol();
		} else {
			final var capability = globalTrackingCapability(role);
			trackingMode = defaultTrackingMode(capability);
			protocol = capability != null ? capability.getProtocol() : null;
		}

		if (trackingMode == TrackingMode.EVENT_DRIVEN) {
			requireProtocol(role, protocol, "tracking");
		}

		return trackingMode;
	}

	// ---- Instance-wide config lookup ----

	private LifecycleCapabilitiesConfiguration.PlacementCapability globalPlacementCapability(
		LifecycleRole role) {

		return switch (role) {
			case SUPPLIER -> configuration.getSupplyingAgencyRequest();
			case BORROWER -> configuration.getBorrowingAgencyRequest();
			case PICKUP -> new LifecycleCapabilitiesConfiguration.PlacementCapability();
		};
	}

	private LifecycleCapabilitiesConfiguration.TrackingCapability globalTrackingCapability(
		LifecycleRole role) {

		return switch (role) {
			case SUPPLIER -> configuration.getSupplierTracking();
			case BORROWER -> configuration.getBorrowerTracking();
			case PICKUP -> new LifecycleCapabilitiesConfiguration.TrackingCapability();
		};
	}

	// ---- Per-host clientConfig lookup ----

	private static HostPlacement hostPlacementCapability(HostLms hostLms, LifecycleRole role) {
		final var capability = capabilityBlock(hostLms, placementKey(role));

		if (capability == null) {
			return null;
		}

		return new HostPlacement(
			parseStrategy(capability.get("strategy")),
			asString(capability.get("protocol")));
	}

	private static HostTracking hostTrackingCapability(HostLms hostLms, LifecycleRole role) {
		final var capability = capabilityBlock(hostLms, trackingKey(role));

		if (capability == null) {
			return null;
		}

		return new HostTracking(
			parseMode(capability.get("mode")),
			asString(capability.get("protocol")));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> capabilityBlock(HostLms hostLms, String key) {
		if (hostLms == null || key == null) {
			return null;
		}

		final var clientConfig = hostLms.getClientConfig();
		if (clientConfig == null) {
			return null;
		}

		final var capabilities = clientConfig.get("capabilities");
		if (!(capabilities instanceof Map<?, ?> capabilityMap)) {
			return null;
		}

		final var block = capabilityMap.get(key);
		return block instanceof Map<?, ?>
			? (Map<String, Object>) block
			: null;
	}

	private static String placementKey(LifecycleRole role) {
		return switch (role) {
			case SUPPLIER -> "supplying-agency-request";
			case BORROWER -> "borrowing-agency-request";
			case PICKUP -> null;
		};
	}

	private static String trackingKey(LifecycleRole role) {
		return switch (role) {
			case SUPPLIER -> "supplier-tracking";
			case BORROWER -> "borrower-tracking";
			case PICKUP -> null;
		};
	}

	private static StrategyType parseStrategy(Object value) {
		final var text = asString(value);
		return text != null ? StrategyType.valueOf(normalise(text)) : null;
	}

	private static TrackingMode parseMode(Object value) {
		final var text = asString(value);
		return text != null ? TrackingMode.valueOf(normalise(text)) : null;
	}

	private static String asString(Object value) {
		if (value == null) {
			return null;
		}

		final var text = value.toString().trim();
		return text.isEmpty() ? null : text;
	}

	private static String normalise(String value) {
		// Accept both YAML kebab-case (scheduled-poll) and enum SCREAMING_SNAKE.
		return value.trim().replace('-', '_').toUpperCase();
	}

	private static StrategyType defaultPlacementStrategy(
		LifecycleCapabilitiesConfiguration.PlacementCapability capability) {

		return capability != null && capability.getStrategy() != null
			? capability.getStrategy()
			: StrategyType.IMPERATIVE;
	}

	private static TrackingMode defaultTrackingMode(
		LifecycleCapabilitiesConfiguration.TrackingCapability capability) {

		return capability != null && capability.getMode() != null
			? capability.getMode()
			: TrackingMode.SCHEDULED_POLL;
	}

	private static void requireProtocol(
		LifecycleRole role,
		String protocol,
		String capabilityType) {

		if (protocol == null || protocol.isBlank()) {
			throw new LifecycleCapabilityConfigurationException(
				"%s %s capability requires an explicit protocol when configured as declarative/event-driven"
					.formatted(role, capabilityType));
		}
	}

	private record HostPlacement(StrategyType strategy, String protocol) {}

	private record HostTracking(TrackingMode mode, String protocol) {}
}
