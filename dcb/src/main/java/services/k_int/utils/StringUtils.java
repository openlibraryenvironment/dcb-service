package services.k_int.utils;

import java.util.Optional;
import java.util.function.Function;

public interface StringUtils {
	static String padRightToLength(String toPad, int length) {
		return Optional.of(toPad != null ? toPad : "")
			.map(Functions.formatAs("%-" + length + "s"))
			.get();
	}

	interface Functions {
		static Function<String, String> formatAs(String pattern) {
			return (String subject) -> String.format(pattern, subject);
		}
	}
}
