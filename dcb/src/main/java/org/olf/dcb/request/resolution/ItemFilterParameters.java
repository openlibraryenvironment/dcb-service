package org.olf.dcb.request.resolution;

import java.util.List;

public interface ItemFilterParameters {
	List<String> getExcludedAgencyCodes();
	String getBorrowingAgencyCode();
	String getBorrowingHostLmsCode();
}
