package org.olf.dcb.request.resolution;

import java.util.List;

import org.olf.dcb.core.model.PatronRequest;

public interface ItemFilterParameters {
	PatronRequest getPatronRequest();
	List<String> getExcludedAgencyCodes();
	String getBorrowingAgencyCode();
}
