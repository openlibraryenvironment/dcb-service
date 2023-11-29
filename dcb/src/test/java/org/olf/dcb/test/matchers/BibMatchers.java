package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.resolution.Bib;

public class BibMatchers {
	public static Matcher<Bib> hasSourceRecordId(String expectedId) {
		return hasProperty("sourceRecordId", is(expectedId));
	}

	public static Matcher<Bib> hasHostLmsCode(String expectedCode) {
		return hasProperty("hostLms",
			hasProperty("code", is(expectedCode)));
	}

	public static Matcher<Bib> hasSourceSystemIdFor(DataHostLms expectedHostLms) {
		return hasProperty("sourceSystemId", is(expectedHostLms.getId()));
	}
}
