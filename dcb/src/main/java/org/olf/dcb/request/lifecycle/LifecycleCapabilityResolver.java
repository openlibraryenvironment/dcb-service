package org.olf.dcb.request.lifecycle;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Prototype
public class LifecycleCapabilityResolver {
	private final LifecycleCapabilitiesConfiguration configuration;

	public LifecycleCapabilityResolver(
		LifecycleCapabilitiesConfiguration configuration) {

		this.configuration = configuration;
	}

	public StrategyType placementStrategy(LifecycleRole role) {
		final var capability = switch (role) {
			case SUPPLIER -> configuration.getSupplyingAgencyRequest();
			case BORROWER -> configuration.getBorrowingAgencyRequest();
			case PICKUP -> new LifecycleCapabilitiesConfiguration.PlacementCapability();
		};

		final var strategy = defaultPlacementStrategy(capability);
		final var protocol = capability != null ? capability.getProtocol() : null;

		log.info("DCB-LIFECYCLE-CAPABILITY: role={} placementStrategy={} protocol={}",
			role, strategy, protocol);

		if (strategy == StrategyType.DECLARATIVE) {
			requireProtocol(role, protocol, "placement");
		}

		return strategy;
	}

	public String placementProtocol(LifecycleRole role) {
		final var capability = switch (role) {
			case SUPPLIER -> configuration.getSupplyingAgencyRequest();
			case BORROWER -> configuration.getBorrowingAgencyRequest();
			case PICKUP -> new LifecycleCapabilitiesConfiguration.PlacementCapability();
		};

		return capability != null ? capability.getProtocol() : null;
	}

	public TrackingMode trackingMode(LifecycleRole role) {
		final var capability = switch (role) {
			case SUPPLIER -> configuration.getSupplierTracking();
			case BORROWER -> configuration.getBorrowerTracking();
			case PICKUP -> new LifecycleCapabilitiesConfiguration.TrackingCapability();
		};

		final var trackingMode = defaultTrackingMode(capability);

		if (trackingMode == TrackingMode.EVENT_DRIVEN) {
			requireProtocol(role, capability.getProtocol(), "tracking");
		}

		return trackingMode;
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
}
