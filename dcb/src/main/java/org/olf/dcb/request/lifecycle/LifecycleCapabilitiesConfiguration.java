package org.olf.dcb.request.lifecycle;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("capabilities")
public class LifecycleCapabilitiesConfiguration {
	private PlacementCapability supplyingAgencyRequest = new PlacementCapability();
	private PlacementCapability borrowingAgencyRequest = new PlacementCapability();
	private TrackingCapability supplierTracking = new TrackingCapability();
	private TrackingCapability borrowerTracking = new TrackingCapability();

	public PlacementCapability getSupplyingAgencyRequest() {
		return supplyingAgencyRequest;
	}

	public void setSupplyingAgencyRequest(
		PlacementCapability supplyingAgencyRequest) {

		this.supplyingAgencyRequest = supplyingAgencyRequest;
	}

	public PlacementCapability getBorrowingAgencyRequest() {
		return borrowingAgencyRequest;
	}

	public void setBorrowingAgencyRequest(
		PlacementCapability borrowingAgencyRequest) {

		this.borrowingAgencyRequest = borrowingAgencyRequest;
	}

	public TrackingCapability getSupplierTracking() {
		return supplierTracking;
	}

	public void setSupplierTracking(TrackingCapability supplierTracking) {
		this.supplierTracking = supplierTracking;
	}

	public TrackingCapability getBorrowerTracking() {
		return borrowerTracking;
	}

	public void setBorrowerTracking(TrackingCapability borrowerTracking) {
		this.borrowerTracking = borrowerTracking;
	}

	public static class PlacementCapability {
		private StrategyType strategy = StrategyType.IMPERATIVE;
		private String protocol;

		public StrategyType getStrategy() {
			return strategy;
		}

		public void setStrategy(StrategyType strategy) {
			this.strategy = strategy;
		}

		public String getProtocol() {
			return protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}
	}

	public static class TrackingCapability {
		private TrackingMode mode = TrackingMode.SCHEDULED_POLL;
		private String protocol;

		public TrackingMode getMode() {
			return mode;
		}

		public void setMode(TrackingMode mode) {
			this.mode = mode;
		}

		public String getProtocol() {
			return protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}
	}
}
