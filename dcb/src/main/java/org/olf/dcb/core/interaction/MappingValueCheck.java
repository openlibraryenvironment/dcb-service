package org.olf.dcb.core.interaction;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Whether one value someone is about to map actually exists in the Host LMS.
 * <p>
 * The same four outcomes {@link ConfigurationReport} keeps apart, for a single value: a
 * vocabulary that could not be read reports {@code UNKNOWN} and never {@code MISSING},
 * because telling someone a patron type is absent when we simply could not look would send
 * them to create one that already exists.
 */
@Serdeable
public record MappingValueCheck(
	String hostLmsCode,
	MappingVocabulary vocabulary,
	String value,
	Result result,
	@Nullable String detail) {

	public enum Result {
		/** The Host LMS holds this value. */
		PRESENT,
		/** The Host LMS was read, and does not hold this value. */
		MISSING,
		/** The vocabulary could not be read, so this value cannot be judged. */
		UNKNOWN,
		/** This adapter cannot read that vocabulary from its Host LMS. */
		NOT_SUPPORTED
	}

	/** Judges a value against a vocabulary that was read successfully. */
	public static MappingValueCheck against(String hostLmsCode, MappingVocabulary vocabulary,
		String value, List<ConfigurationReport.Entry> entries) {

		if (entries.isEmpty()) {
			return new MappingValueCheck(hostLmsCode, vocabulary, value, Result.UNKNOWN,
				"The Host LMS returned no values for this vocabulary");
		}

		final var present = entries.stream()
			.anyMatch(entry -> value.equals(entry.code()));

		return new MappingValueCheck(hostLmsCode, vocabulary, value,
			present ? Result.PRESENT : Result.MISSING,
			present ? null : "Not found in " + hostLmsCode);
	}

	public static MappingValueCheck notSupported(String hostLmsCode, MappingVocabulary vocabulary,
		String value) {

		return new MappingValueCheck(hostLmsCode, vocabulary, value, Result.NOT_SUPPORTED,
			"This adapter cannot read " + vocabulary + " from its Host LMS");
	}

	public static MappingValueCheck unknown(String hostLmsCode, MappingVocabulary vocabulary,
		String value, String detail) {

		return new MappingValueCheck(hostLmsCode, vocabulary, value, Result.UNKNOWN, detail);
	}
}
