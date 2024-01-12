package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.model.*;
import lombok.experimental.Accessors;
import lombok.Data;

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
@Data
@Accessors(chain=true)
public class RequestWorkflowContext {
	String patronAgencyCode;
	String patronSystemCode;
	Agency patronAgency;
	DataHostLms patronSystem;

	String pickupAgencyCode;
	String pickupSystemCode;
	Agency pickupAgency;

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
}

