package org.olf.dcb.request.lifecycle;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("capabilities")
public class LifecycleCapabilitiesConfiguration {
	private SupplyingAgencyRequestCapability supplyingAgencyRequest = new SupplyingAgencyRequestCapability();
	private BorrowingAgencyRequestCapability borrowingAgencyRequest = new BorrowingAgencyRequestCapability();
	private SupplierTrackingCapability supplierTracking = new SupplierTrackingCapability();
	private BorrowerTrackingCapability borrowerTracking = new BorrowerTrackingCapability();

	public PlacementCapability getSupplyingAgencyRequest() {
		return supplyingAgencyRequest;
	}

	public void setSupplyingAgencyRequest(
		PlacementCapability supplyingAgencyRequest) {

		this.supplyingAgencyRequest = SupplyingAgencyRequestCapability.from(
			supplyingAgencyRequest);
	}

	public void setSupplyingAgencyRequest(
		SupplyingAgencyRequestCapability supplyingAgencyRequest) {

		this.supplyingAgencyRequest = supplyingAgencyRequest;
	}

	public PlacementCapability getBorrowingAgencyRequest() {
		return borrowingAgencyRequest;
	}

	public void setBorrowingAgencyRequest(
		PlacementCapability borrowingAgencyRequest) {

		this.borrowingAgencyRequest = BorrowingAgencyRequestCapability.from(
			borrowingAgencyRequest);
	}

	public void setBorrowingAgencyRequest(
		BorrowingAgencyRequestCapability borrowingAgencyRequest) {

		this.borrowingAgencyRequest = borrowingAgencyRequest;
	}

	public TrackingCapability getSupplierTracking() {
		return supplierTracking;
	}

	public void setSupplierTracking(TrackingCapability supplierTracking) {
		this.supplierTracking = SupplierTrackingCapability.from(supplierTracking);
	}

	public void setSupplierTracking(SupplierTrackingCapability supplierTracking) {
		this.supplierTracking = supplierTracking;
	}

	public TrackingCapability getBorrowerTracking() {
		return borrowerTracking;
	}

	public void setBorrowerTracking(TrackingCapability borrowerTracking) {
		this.borrowerTracking = BorrowerTrackingCapability.from(borrowerTracking);
	}

	public void setBorrowerTracking(BorrowerTrackingCapability borrowerTracking) {
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

	@ConfigurationProperties("supplying-agency-request")
	public static class SupplyingAgencyRequestCapability
		extends PlacementCapability {

		static SupplyingAgencyRequestCapability from(PlacementCapability source) {
			final var capability = new SupplyingAgencyRequestCapability();
			if (source != null) {
				capability.setStrategy(source.getStrategy());
				capability.setProtocol(source.getProtocol());
			}
			return capability;
		}
	}

	@ConfigurationProperties("borrowing-agency-request")
	public static class BorrowingAgencyRequestCapability
		extends PlacementCapability {

		static BorrowingAgencyRequestCapability from(PlacementCapability source) {
			final var capability = new BorrowingAgencyRequestCapability();
			if (source != null) {
				capability.setStrategy(source.getStrategy());
				capability.setProtocol(source.getProtocol());
			}
			return capability;
		}
	}

	@ConfigurationProperties("supplier-tracking")
	public static class SupplierTrackingCapability extends TrackingCapability {

		static SupplierTrackingCapability from(TrackingCapability source) {
			final var capability = new SupplierTrackingCapability();
			if (source != null) {
				capability.setMode(source.getMode());
				capability.setProtocol(source.getProtocol());
			}
			return capability;
		}
	}

	@ConfigurationProperties("borrower-tracking")
	public static class BorrowerTrackingCapability extends TrackingCapability {

		static BorrowerTrackingCapability from(TrackingCapability source) {
			final var capability = new BorrowerTrackingCapability();
			if (source != null) {
				capability.setMode(source.getMode());
				capability.setProtocol(source.getProtocol());
			}
			return capability;
		}
	}
}
