package org.olf.dcb.graphql;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;

/**
 * Reading a GraphQL input map without assuming a present key holds a value.
 *
 * <h2>The bug this exists to end</h2>
 *
 * Every field in the library fetchers was read as
 * {@code map.containsKey(k) ? map.get(k).toString() : null}, and
 * {@code containsKey} is TRUE for a key present with a null value. GraphQL sends exactly
 * that for any nullable input field the client supplies as null - which DCB Admin does for
 * {@code maxConsortialLoans} on every library it creates. The result was
 * {@code Cannot invoke "Object.toString()" because the return value of
 * "java.util.Map.get(Object)" is null}: an NPE surfaced to the user as a failed library
 * creation with no indication of which field caused it.
 *
 * <p>A partial-update fetcher that genuinely needs to distinguish "absent, leave it alone"
 * from "present and null, clear it" must keep asking {@code containsKey} — and must then
 * also stop calling {@code toString()} on the result. Neither library fetcher does today.
 */
final class InputValues {

	private InputValues() {
	}

	/** GraphQL sends an explicit null as a present key with a null value. */
	static @Nullable String asString(@Nullable Object value) {
		return value == null ? null : value.toString();
	}

	/**
	 * The value as a string, or null when the key is absent or its value is null.
	 *
	 * An EMPTY STRING is returned as itself, not as null. A cleared text box is a
	 * deliberate instruction to store nothing, and collapsing it to null here would make
	 * "I removed the support hours" indistinguishable from "I did not mention them".
	 */
	static @Nullable String stringValue(Map<String, Object> input, String key) {
		return asString(input.get(key));
	}

	/**
	 * The value as an Integer, or null when the key is absent, null, or blank.
	 *
	 * Blank counts as absent for a NUMBER, where it cannot mean anything else: a cleared
	 * numeric field arrives as an empty string from a form, and {@code Integer.parseInt("")}
	 * is a NumberFormatException reported to the user as a server error.
	 */
	static @Nullable Integer integerValue(Map<String, Object> input, String key) {
		final var value = stringValue(input, key);
		return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
	}

	/** The value as a Float, or null when the key is absent, null, or blank. */
	static @Nullable Float floatValue(Map<String, Object> input, String key) {
		final var value = stringValue(input, key);
		return value == null || value.isBlank() ? null : Float.valueOf(value.trim());
	}

	/**
	 * The value as a Boolean, or null when the key is absent or its value is null.
	 *
	 * Null is preserved rather than defaulted to false, because the library fetchers use it:
	 * "nobody said whether this library supplies" is a different fact from "this library
	 * does not supply", and only the second one should switch anything off.
	 */
	static @Nullable Boolean booleanValue(Map<String, Object> input, String key) {
		final var value = input.get(key);

		if (value == null) {
			return null;
		}

		return value instanceof Boolean bool ? bool : Boolean.valueOf(value.toString());
	}
}
