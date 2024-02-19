package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.SupplierRequest;

public class SupplierRequestMatchers {

	public static Matcher<SupplierRequest> hasLocalStatus(String expectedStatus) {
		return hasProperty("localStatus", is(expectedStatus));
	}

	public static Matcher<SupplierRequest> hasLocalItemBarcode(String expectedBarcode) {
		return hasProperty("localItemBarcode", is(expectedBarcode));
	}
}
