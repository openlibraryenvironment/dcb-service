package services.k_int.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface StringUtils {
	
	public static String padRightToLength(String toPad, int length) {
		
		return Optional.of(toPad != null ? toPad : "")
			.map(Functions.formatAs("%-" + length + "s"))
			.get();
	}
	
	public static String padLeftToLength(String toPad, int length) {
		
		return Optional.of(toPad != null ? toPad : "")
			.map(Functions.formatAs("%" + length + "s"))
			.get();
	}
	
	public static interface Functions {
		public static Function<String, String> padRightToLength( int length ) {
			return ( String subject ) -> StringUtils.padRightToLength(subject, length);
		}
		public static Function<String, String> padLeftToLength( int length ) {
			return ( String subject ) -> StringUtils.padLeftToLength(subject, length);
		}
		public static Function<String, String> formatAs( String pattern ) {
			return ( String subject ) -> String.format(pattern, subject);
		}
	}

	public static List<String> splitTrimAndRemoveBrackets(String input, String delimiter) {
		return Optional.ofNullable(input)
			.map(s -> Arrays.stream(s.split(delimiter))
				.map(String::trim)
				.map(str -> str.replace("[", "").replace("]", ""))
				.collect(Collectors.toList()))
			.orElse(null);
	}

	public static String toStringWithoutBrackets(List<String> list, String delimiter) {
		if (list == null || list.isEmpty()) {
			return null;
		}

		return list.stream()
			.map(str -> str.replace("[", "").replace("]", ""))
			.collect(Collectors.joining(delimiter));
	}
}
