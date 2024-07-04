package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.model.*;

import io.micronaut.serde.annotation.Serdeable;
import lombok.experimental.Accessors;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

/**
 * Core attributes needed for workflow steps.
 * Many requesting workflow steps require the same core set of data objects to progress a request.
 * This class creates a reusable context that can be initialised and passed between different
 * requesting workflow steps.
 * The core idea is to gather together all the classes needed to aggregate these objects into a
 * single place and reduce duplication between workflow steps.
 *
 * Please use RequestWorkflowContextHelper to obtain instances of this context rather than adding methods to individual classes/workflows
 */
@Serdeable // Required because it is used in audit data that is serialised to json
@Data
@Accessors(chain=true)
public class RequestWorkflowContext {
	String patronAgencyCode;
	String patronSystemCode;
	Agency patronAgency;
	DataHostLms patronSystem;

	// Information about the AGENCY being used for pickup
	String pickupAgencyCode;
	String pickupSystemCode;
	Agency pickupAgency;

	// the ID of the pickup location as it is known to the pickup agency
	// For example in polaris this is the internal integer primary key of the pickup location.
	String pickupLocationLocalId;
	// The actual location record for the pickup location.
	Location pickupLocation;

	String lenderAgencyCode;
	String lenderSystemCode;
	Agency lenderAgency;

	PatronIdentity patronHomeIdentity;
	PatronIdentity patronVirtualIdentity;

	PatronRequest patronRequest;
	SupplierRequest supplierRequest;
	String supplierHoldId;
	String supplierHoldStatus;
	Patron patron;

	PatronRequest.Status patronRequestStateOnEntry;

	// Provide a list of strings that workflow actions can use to propagate messages to the audit log
	List<String> workflowMessages = new ArrayList<String>();

	public String generateTransactionNote() {
    String note = "Consortial Hold. tno=" + patronRequest.getId() + " \nFor " +
			( patronHomeIdentity != null ? patronHomeIdentity.getLocalBarcode() : "UNKNOWN" ) + 
			"@" + patronAgencyCode +
			generatePickupNote();
		return note;
	}

	private String generatePickupNote() {
		String note = "\n Pickup "+
			( pickupLocation != null ? pickupLocation.getName() : "UNKNOWN" ) +
			"@"+pickupAgencyCode;
		return note;
	}

	public static <T> T extractFromSupplierReq(RequestWorkflowContext ctx,
		Function<SupplierRequest, T> extractor, String fieldName) {

		return Optional.ofNullable(ctx)
			.map(RequestWorkflowContext::getSupplierRequest)
			.map(extractor)
			.orElseThrow(() -> new DcbError("Unable to extract ctx.sr.'%s'".formatted(fieldName)));
	}

	public static <T> T extractFromVirtualIdentity(RequestWorkflowContext ctx,
		Function<PatronIdentity, T> extractor, String fieldName) {

		return Optional.ofNullable(ctx)
			.map(RequestWorkflowContext::getPatronVirtualIdentity)
			.map(extractor)
			.orElseThrow(() -> new DcbError("Unable to extract ctx.'%s'".formatted(fieldName)));
	}
}
