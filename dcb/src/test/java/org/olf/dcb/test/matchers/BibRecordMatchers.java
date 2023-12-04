package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataHostLms;

public class BibRecordMatchers {
	public static Matcher<BibRecordMatchers> hasSourceRecordId(String expectedId) {
		return hasProperty("sourceRecordId", is(expectedId));
	}

	public static Matcher<BibRecordMatchers> hasSourceSystemIdFor(DataHostLms expectedHostLms) {
		return hasProperty("sourceSystemId", is(expectedHostLms.getId()));
	}
}