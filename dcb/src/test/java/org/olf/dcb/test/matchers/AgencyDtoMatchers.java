package org.olf.dcb.test.matchers;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.core.api.serde.AgencyDTO;

public class AgencyDtoMatchers {
	public static Matcher<AgencyDTO> hasId(UUID expectedId) {
		return hasProperty("id", is(expectedId));
	}

	public static Matcher<AgencyDTO> hasCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}

	public static Matcher<AgencyDTO> hasName(String expectedName) {
		return hasProperty("name", is(expectedName));
	}

	public static Matcher<AgencyDTO> hasHostLmsCode(String expectedHostLmsCode) {
		return hasProperty("hostLMSCode", is(expectedHostLmsCode));
	}

	public static Matcher<AgencyDTO> hasAuthProfile(String expectedAuthProfile) {
		return hasProperty("authProfile", is(expectedAuthProfile));
	}

	public static Matcher<AgencyDTO> hasIdpUrl(String expectedIdpUrl) {
		return hasProperty("idpUrl", is(expectedIdpUrl));
	}

	public static Matcher<AgencyDTO> isSupplyingAgency() {
		return hasProperty("isSupplyingAgency", is(true));
	}

	public static Matcher<AgencyDTO> isNotBorrowingAgency() {
		return hasProperty("isBorrowingAgency", is(false));
	}
}
