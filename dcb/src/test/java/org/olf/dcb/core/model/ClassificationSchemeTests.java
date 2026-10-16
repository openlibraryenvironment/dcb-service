package org.olf.dcb.core.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which classification a library may declare it shelves by.
 *
 * The vocabulary is an allow-list because the value chooses which index field a browse query
 * sorts on. An unrecognised one would name a field that does not exist, and the doctrine's
 * rule about allow-lists at the query boundary is exactly this case.
 */
class ClassificationSchemeTests {

	@Test
	@DisplayName("the two schemes a shelf key can be computed for are accepted")
	void theSupportedSchemesAreAccepted() {
		assertThat(ClassificationScheme.parse("DEWEY"), is(ClassificationScheme.DEWEY));
		assertThat(ClassificationScheme.parse("LCC"), is(ClassificationScheme.LCC));
	}

	@Test
	@DisplayName("a scheme is matched however the caller cased or padded it")
	void parsingIsForgivingAboutCaseAndSpace() {
		assertThat(ClassificationScheme.parse(" dewey "), is(ClassificationScheme.DEWEY));
		assertThat(ClassificationScheme.parse("Lcc"), is(ClassificationScheme.LCC));
	}

	@Test
	@DisplayName("not stated is a value, and is not defaulted to Dewey")
	void notStatedIsNotDefaulted() {
		// A library that has not said is one shelf browse is not offered for. Defaulting
		// would give a library that shelves by LCC a confidently wrong order, which is
		// precisely the failure shelf browse must never produce.
		assertThat(ClassificationScheme.parse(null), is(nullValue()));
		assertThat(ClassificationScheme.parse("  "), is(nullValue()));
	}

	@Test
	@DisplayName("an unrecognised scheme reads as not stated rather than failing the record")
	void anUnrecognisedSchemeReadsAsNotStated() {
		// The column is a varchar any SQL client can write, so a row carrying something else
		// must degrade to "no shelf browse here". Failing on READ would take the library's
		// whole record down for a value somebody typed once.
		assertThat(ClassificationScheme.parse("UDC"), is(nullValue()));
		assertThat(ClassificationScheme.parse("other"), is(nullValue()));
	}

	@Test
	@DisplayName("each scheme names the index field its shelf order is carried in")
	void eachSchemeNamesItsIndexField() {
		// Must match mappings-2.json's members.shelfSort.* properties, which is what
		// ShelfSortKey writes. A mismatch here sorts on a field nothing populates and
		// returns an empty shelf rather than an error.
		assertThat(ClassificationScheme.DEWEY.shelfSortField(), is("members.shelfSort.dewey"));
		assertThat(ClassificationScheme.LCC.shelfSortField(), is("members.shelfSort.lcc"));
	}
}
