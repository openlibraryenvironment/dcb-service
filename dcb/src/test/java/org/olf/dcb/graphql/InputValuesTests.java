package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The present-but-null case, which is the one that broke.
 *
 * DCB Admin sends {@code maxConsortialLoans: null} on every library it creates - a
 * perfectly ordinary way for a client to say "this optional field has no value". GraphQL
 * puts it in the input map as a present key holding null, so {@code containsKey} was true,
 * {@code get} returned null, and {@code toString()} threw. The user saw a failed library
 * creation naming no field at all.
 */
class InputValuesTests {

	/** A map that really can hold a null value - Map.of cannot. */
	private static Map<String, Object> input(Object... keysAndValues) {
		final var map = new HashMap<String, Object>();

		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}

		return map;
	}

	@Test
	void aPresentNullIsNotAValue() {
		final var map = input("maxConsortialLoans", null, "authProfile", null);

		// Before the fix each of these threw NullPointerException.
		assertThat(InputValues.integerValue(map, "maxConsortialLoans"), is(nullValue()));
		assertThat(InputValues.stringValue(map, "authProfile"), is(nullValue()));
		assertThat(InputValues.booleanValue(map, "authProfile"), is(nullValue()));
		assertThat(InputValues.floatValue(map, "maxConsortialLoans"), is(nullValue()));
	}

	@Test
	void anAbsentKeyIsAlsoNotAValue() {
		final var map = input();

		assertThat(InputValues.stringValue(map, "fullName"), is(nullValue()));
		assertThat(InputValues.integerValue(map, "maxConsortialLoans"), is(nullValue()));
		assertThat(InputValues.floatValue(map, "latitude"), is(nullValue()));
		assertThat(InputValues.booleanValue(map, "isBorrowingAgency"), is(nullValue()));
	}

	@Test
	void readsOrdinaryValues() {
		final var map = input(
			"fullName", "College",
			"maxConsortialLoans", 5,
			"latitude", 53.408,
			"isBorrowingAgency", true);

		assertThat(InputValues.stringValue(map, "fullName"), is("College"));
		assertThat(InputValues.integerValue(map, "maxConsortialLoans"), is(5));
		assertThat(InputValues.floatValue(map, "latitude"), is(53.408f));
		assertThat(InputValues.booleanValue(map, "isBorrowingAgency"), is(true));
	}

	@Test
	void anEmptyStringSurvivesAsItself() {
		// A cleared text box is an instruction to store nothing, and is a different fact
		// from never having been mentioned. Collapsing it to null here would make
		// "I removed the support hours" indistinguishable from "I did not mention them".
		final var map = input("supportHours", "");

		assertThat(InputValues.stringValue(map, "supportHours"), is(""));
	}

	@Test
	void aBlankNumberIsNothing() {
		// Where an empty string cannot mean anything: a cleared numeric field arrives from
		// a form as "", and Integer.parseInt("") is a NumberFormatException reported to
		// the user as a server error.
		final var map = input("maxConsortialLoans", "", "latitude", "  ");

		assertThat(InputValues.integerValue(map, "maxConsortialLoans"), is(nullValue()));
		assertThat(InputValues.floatValue(map, "latitude"), is(nullValue()));
	}

	@Test
	void readsNumbersAndBooleansSentAsStrings() {
		// Both spellings reach these fetchers depending on the client and the schema type.
		final var map = input(
			"maxConsortialLoans", "5",
			"latitude", "53.408",
			"isBorrowingAgency", "true");

		assertThat(InputValues.integerValue(map, "maxConsortialLoans"), is(5));
		assertThat(InputValues.floatValue(map, "latitude"), is(53.408f));
		assertThat(InputValues.booleanValue(map, "isBorrowingAgency"), is(true));
	}
}
