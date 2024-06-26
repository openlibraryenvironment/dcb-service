package services.k_int.utils;

import static io.micronaut.core.util.StringUtils.*;
import static java.lang.Math.min;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.micronaut.core.annotation.Nullable;

public interface StringUtils {
	static String padRightToLength(String toPad, int length) {
		return Optional.of(toPad != null ? toPad : "")
			.map(Functions.formatAs("%-" + length + "s"))
			.get();
	}

	static String truncate(String value, int maximumLength) {
		if (isEmpty(value)) {
			return value;
		}

		return value.substring(0, min(value.length(), maximumLength));
	}

	/**
	 * Converts an Integer to a String
	 *
	 * String.valueOf is unsuitable for this as it converts null to "null"
	 * which potentially stops later null checks from working as intended
	 *
	 * @param value integer to convert
	 * @return null when value is null, otherwise value as a string
	 */
	static String convertIntegerToString(@Nullable Integer value) {
		return value == null
			? null
			: value.toString();
	}

	interface Functions {
		static Function<String, String> formatAs(String pattern) {
			return (String subject) -> String.format(pattern, subject);
		}
	}

	static List<String> parseList(String input) {
		if (input == null)
			return null;

		int startIndex = input.indexOf("[");
		int endIndex = input.lastIndexOf("]");

		if (isWrappedWithSquareBrackets(input, startIndex, endIndex)) {
			String substring = input.substring(startIndex + 1, endIndex);

			return Arrays.asList(substring.split(", "));
		} else {

			return Arrays.asList(input.split(", "));
		}
	}

	private static boolean isWrappedWithSquareBrackets(String input, int startIndex, int endIndex) {
		return startIndex == 0
			&& endIndex == input.length() - 1
			&& startIndex < endIndex;
	}
}
