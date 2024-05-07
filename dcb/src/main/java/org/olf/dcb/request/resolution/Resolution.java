package org.olf.dcb.request.resolution;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;

import lombok.Value;

@Value
public class Resolution {
	PatronRequest patronRequest;
	Optional<SupplierRequest> optionalSupplierRequest;
}
