package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;

import org.olf.dcb.core.interaction.RequestShippingContext;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RequestShippingContextProjector {
	private RequestShippingContextProjector() {}

	/**
	 * Best-effort projection for callers that do not consume shipping context.
	 *
	 * Only the declarative strategies put this on the wire; the imperative
	 * adapters (Sierra, Polaris, FOLIO, Alma) ignore the field entirely. The
	 * workflow deliberately tolerates a context with no pickup location (null
	 * pickup symbol) and no home identity (identity lookup completes empty), so
	 * demanding a complete projection here would fail placements that have always
	 * succeeded. Declarative callers must use {@link #project} and fail loudly.
	 */
	public static RequestShippingContext projectQuietly(RequestWorkflowContext context) {
		try {
			return project(context);
		}
		catch (IllegalStateException e) {
			log.debug("Shipping context unavailable, omitting from hold request: {}",
				e.getMessage());
			return null;
		}
	}

	public static RequestShippingContext project(RequestWorkflowContext context) {
		final var patronRequest = require(context != null ? context.getPatronRequest() : null, "patron request");
		final var pickupLocation = require(context.getPickupLocation(), "pickup location");
		final var pickupAgency = require(context.getPickupAgency(), "pickup agency");
		final var patronIdentity = require(context.getPatronHomeIdentity(), "patron home identity");
		final var supplierRequest = require(context.getSupplierRequest(), "supplier request");
		final var workflowCode = requireText(patronRequest.getActiveWorkflow(), "active workflow");
		final var pickupSystemCode = requireText(context.getPickupSystemCode(), "pickup system code");
		final var pickupAgencyCode = requireText(context.getPickupAgencyCode(), "pickup agency code");
		final var pickupLocationCode = firstText(pickupLocation.getLocalId(), pickupLocation.getCode());
		if (!hasText(pickupLocationCode)) {
			throw unresolved("pickup location has neither a system-scoped local ID nor a DCB code");
		}

		final var patronSystemCode = requireText(context.getPatronSystemCode(), "patron system code");
		final var patronAgencyCode = requireText(context.getPatronAgencyCode(), "borrowing agency code");
		final var supplierSystemCode = requireText(firstText(
			context.getLenderSystemCode(), supplierRequest.getHostLmsCode()), "supplier system code");
		final var supplierAgencyCode = requireText(firstText(
			context.getLenderAgencyCode(), supplierRequest.getLocalAgency(), supplierSystemCode),
			"supplier agency code");
		final var patronBarcode = requireText(firstText(
			patronIdentity.getFirstBarcode(), patronIdentity.getLocalId()), "patron barcode");

		return new RequestShippingContext(
			RequestShippingContext.SCHEMA_VERSION,
			workflowCode,
			PICKUP_ANYWHERE_WORKFLOW.equals(workflowCode) ? "PickupAnywhere" : "Direct",
			new RequestShippingContext.Patron(patronBarcode, patronSystemCode, patronAgencyCode),
			endpoint(patronSystemCode, patronAgencyCode, context.getPatronAgency()),
			endpoint(supplierSystemCode, supplierAgencyCode, context.getLenderAgency()),
			new RequestShippingContext.PickupDestination(
				RequestShippingContext.DESTINATION_KIND,
				endpoint(pickupSystemCode, pickupAgencyCode, pickupAgency),
				pickupLocation.getIdAsString(),
				pickupLocation.getCode(),
				pickupLocationCode,
				pickupDisplayName(pickupLocation, context.getPickupLibrary(), pickupLocationCode),
				address(context.getPickupLibrary())),
			new RequestShippingContext.Provenance(
				"DCB_PATRON_REQUEST",
				patronRequest.getPickupLocationCode(),
				patronRequest.getPickupLocationCodeContext(),
				patronRequest.getPickupLocationContext(),
				patronRequest.getDateCreated(),
				pickupLocation.getLastImported()));
	}

	private static RequestShippingContext.Endpoint endpoint(
		String systemCode, String agencyCode, Agency agency) {

		return new RequestShippingContext.Endpoint(
			systemCode,
			agencyCode,
			agency != null ? agency.getName() : null);
	}

	private static RequestShippingContext.AddressSnapshot address(Library library) {
		if (library == null || !hasText(library.getAddress())) {
			return null;
		}
		return new RequestShippingContext.AddressSnapshot(
			library.getAddress(), "PICKUP_LIBRARY", "DCB_LIBRARY_DIRECTORY");
	}

	private static String pickupDisplayName(Location location, Library library, String fallback) {
		return firstText(
			location.getPrintLabel(),
			location.getName(),
			library != null ? library.getFullName() : null,
			fallback);
	}

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw unresolved(name + " is missing");
		}
		return value;
	}

	private static String requireText(String value, String name) {
		if (!hasText(value)) {
			throw unresolved(name + " is missing");
		}
		return value.trim();
	}

	private static IllegalStateException unresolved(String detail) {
		return new IllegalStateException("Cannot route supplier request: " + detail);
	}

	private static String firstText(String... values) {
		if (values != null) {
			for (String value : values) {
				if (hasText(value)) {
					return value.trim();
				}
			}
		}
		return null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
