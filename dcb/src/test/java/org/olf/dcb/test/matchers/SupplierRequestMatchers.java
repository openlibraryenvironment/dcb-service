package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;

public class SupplierRequestMatchers {
	public static Matcher<SupplierRequest> hasLocalStatus(String expectedStatus) {
		return hasProperty("localStatus", is(expectedStatus));
	}

	public static Matcher<SupplierRequest> hasStatusCode(SupplierRequestStatusCode expectedStatus) {
		return hasProperty("statusCode", is(expectedStatus));
	}

	public static Matcher<SupplierRequest> hasLocalItemBarcode(String expectedBarcode) {
		return hasProperty("localItemBarcode", is(expectedBarcode));
	}

	public static Matcher<SupplierRequest> hasLocalItemId(String expectedId) {
		return hasProperty("localItemId", is(expectedId));
	}

	public static Matcher<SupplierRequest> hasNoLocalStatus() {
		return hasProperty("localStatus", is(nullValue()));
	}

	public static Matcher<SupplierRequest> hasNoLocalId() {
		return hasProperty("localId", is(nullValue()));
	}

	public static Matcher<SupplierRequest> hasNoLocalItemStatus() {
		return hasProperty("localItemStatus", is(nullValue()));
	}

	public static Matcher<SupplierRequest> hasLocalItemLocationCode(String expectedCode) {
		return hasProperty("localItemLocationCode", is(expectedCode));
	}

	public static Matcher<SupplierRequest> hasLocalBibId(String expectedId) {
		return hasProperty("localBibId", is(expectedId));
	}

	public static Matcher<SupplierRequest> hasResolvedAgency(
		DataAgency expectedAgency) {
		return hasProperty("resolvedAgency", allOf(
			notNullValue(),
			hasProperty("id", is(expectedAgency.getId()))
		));
	}

	public static Matcher<SupplierRequest> hasLocalAgencyCode(String expectedCode) {
		return hasProperty("localAgency", is(expectedCode));
	}

    public static Matcher<SupplierRequest> hasHostLmsCode(String expectedHostLmsCode) {
        return hasProperty("hostLmsCode", is(expectedHostLmsCode));
    }
}
