package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;

public class BibRecordMatchers {
	public static Matcher<BibRecord> hasSourceRecordId(String expectedId) {
		return hasProperty("sourceRecordId", is(expectedId));
	}

	public static Matcher<BibRecord> hasSourceSystemIdFor(DataHostLms expectedHostLms) {
		return hasProperty("sourceSystemId", is(expectedHostLms.getId()));
	}

	public static Matcher<BibRecord> hasLanguageMetadata(String... expectedLanguages) {
		return hasMetadataProperty(equalTo("language"), containsInAnyOrder(expectedLanguages));
	}

	public static Matcher<BibRecord> hasTitleMetadata(String expectedTitle) {
		return hasMetadataProperty("title", expectedTitle);
	}

	public static Matcher<BibRecord> hasMetadataProperty(String key, String expectedValue) {
		return hasMetadataProperty(is(key), is(expectedValue));
	}

	public static <T> Matcher<BibRecord> hasMetadataProperty(Matcher<String> keyMatcher, Matcher<T> valueMatcher) {
		return hasProperty("canonicalMetadata", hasEntry(keyMatcher, valueMatcher));
	}
}
