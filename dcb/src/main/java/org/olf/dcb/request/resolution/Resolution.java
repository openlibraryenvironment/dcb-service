package org.olf.dcb.request.resolution;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;

import lombok.Data;

@Data
public class Resolution {
	private final PatronRequest patronRequest;
	private final Optional<SupplierRequest> optionalSupplierRequest;
}
