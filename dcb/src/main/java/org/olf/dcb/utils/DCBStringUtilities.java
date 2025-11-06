package org.olf.dcb.utils;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.text.Normalizer;
import java.util.Collections;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.olf.dcb.core.clustering.ImprovedRecordClusteringService;

import io.micronaut.core.util.StringUtils;
import jakarta.validation.constraints.NotNull;
import static services.k_int.features.Features.featureIsEnabled;
import services.k_int.utils.UUIDUtils;

public class DCBStringUtilities {

	private static final String UUID5_ID_PREFIX = "dcbid";

	// Adapted from ISO list of stopwords - this may be overkill!
	private static final Set<String> stopwords = Stream.of("0o", "0s", "3a", "3b", "3d", "6b", "6o", "a", "a1", "a2",
			"a3", "a4", "ab", "ac", "ad", "ae", "af", "ag", "ah", "ain", "aj", "al", "all", "an", "and", "ao", "ap", "ar",
			"as", "a's", "au", "av", "aw", "ax", "ay", "az", "b", "b1", "b2", "b3", "ba", "bc", "bd", "be", "bi", "biol",
			"bj", "bk", "bl", "bn", "bp", "br", "bs", "bt", "bu", "but", "bx", "by", "c", "c1", "c2", "c3", "ca", "cc", "cd",
			"ce", "cf", "cg", "ch", "ci", "cit", "cj", "cl", "cm", "cn", "co", "cp", "cq", "cr", "cry", "cs", "c's", "ct",
			"cu", "cv", "cx", "cy", "cz", "d", "d2", "da", "dc", "dd", "de", "df", "di", "dj", "dk", "dl", "do", "dp", "dr",
			"ds", "dt", "du", "due", "dx", "dy", "e", "e2", "e3", "ea", "ec", "ed", "ee", "ef", "eg", "ei", "ej", "el", "em",
			"en", "end", "eo", "ep", "eq", "er", "es", "et", "et-al", "eu", "ev", "ey", "f", "f2", "fa", "far", "fc", "ff",
			"fi", "fj", "fl", "fn", "fo", "fy", "g", "ga", "gi", "gj", "gl", "gr", "gs", "gy", "h", "h2", "h3", "hj", "ho",
			"hr", "hs", "http", "hu", "hy", "i", "i2", "i3", "i4", "i6", "i7", "i8", "ia", "ib", "ibid", "ic", "id", "i'd",
			"ie", "ig", "ih", "ii", "ij", "il", "i'll", "im", "i'm","in", "io", "ip", "iq", "ir", "ix", "iy", "iz", "j", "jj",
			"jr", "js", "jt", "ju", "k", "ke", "kg", "kj", "km", "ko", "l", "l2", "la", "lb", "lc", "le", "lj", "ll", "ll",
			"ln", "lo", "lr", "ls", "lt", "ltd", "m", "m2", "ma", "ml", "mn", "mo", "n", "n2", "na", "nc", "nd", "ne", "ng",
			"ni", "nj", "nl", "nn", "nr", "ns", "nt", "ny", "o", "oa", "ob", "oc", "od", "of", "og", "oh", "oi", "oj", "ok",
			"ol", "om", "on", "oo", "op", "oq", "or", "ord", "os", "ot", "ow", "p", "p1", "p2", "p3", "pc", "pd", "pe", "pf",
			"ph", "pi", "pj", "pk", "pl", "pm", "pn", "po", "pp", "pq", "pr", "pu", "py", "q", "qj", "qu", "qv", "r", "r2",
			"ra", "rc", "rd", "re", "ref", "refs", "rf", "rh", "ri", "rj", "rl", "rm", "rn", "ro", "rq", "rr", "rs", "rt",
			"ru", "rv", "ry", "s", "s2", "sa", "sc", "sd", "se", "sf", "sj", "sl", "sm", "sn", "sp", "sq", "sr", "ss", "st",
			"sy", "sz", "t", "t1", "t2", "t3", "tb", "tc", "td", "te", "tf", "th", "the", "ti", "tj", "tl", "tm", "tn", "to", "tp",
			"tq", "tr", "ts", "t's", "tt", "tv", "u", "u201d", "ue", "ui", "uj", "uk", "um", "un", "uo", "ur", "ut", "v",
			"va", "vd", "ve", "ve", "vj", "vo", "vq", "vs", "vt", "vu", "w", "wa", "wi", "wo", "x", "x1", "x2", "x3", "xf",
			"xi", "xj", "xk", "xl", "xn", "xo", "xs", "xt", "xv", "xx", "y", "y2", "yj", "yl", "yr", "ys", "yt", "z", "zi",
			"zz").collect(Collectors.toUnmodifiableSet());

	private static final Pattern DIACRITIC_AND_NONE_ALPHANUMERIC = Pattern.compile("([^\\p{Alnum}\\s]|\\p{M})");
	private static final Pattern SPLITTING_DELIMETER = Pattern.compile("((\\s*\\&\\s*)|\\s+)+");
	private static final Pattern NAMES = Pattern.compile("\\s*\\b([\\w\\.]+) *,(( *?\\b((\\w\\.)|([\\w\\-]+)))+)");

	public static final String toNoneDiacriticAlphaNumeric(final String source) {

		return Optional.ofNullable(source)
				.map(text -> Normalizer.normalize(text, Normalizer.Form.NFKD))
				.map(text -> DIACRITIC_AND_NONE_ALPHANUMERIC.matcher(text)
						.replaceAll(" "))
				.orElseGet(() -> null);
	}

	public static String generateBlockingString(final String inputString) {
		return generateBlockingString(inputString, Collections.emptyList());
	}
	
	public static String generateBlockingString(final String inputString, @NotNull List<String> qualifiers) {
		if (inputString == null)
			return null;
		
		// Under a feature flag we need to branch and make sure the string is ordered. 
		// This should be removed when we are confident this is better and just use the "new" route.
		if (featureIsEnabled(ImprovedRecordClusteringService.FEATURE_IMPROVED_CLUSTERING))
			return generateNewBlockingString(inputString, qualifiers);
		
		// Feature not enabled. Drop through to the previous method 
		// Replace Names
		return Stream.concat(
			qualifiers.stream()
				.map(DCBStringUtilities::toNoneDiacriticAlphaNumeric),
			Stream.of(NAMES.matcher(toNoneDiacriticAlphaNumeric(inputString)).replaceAll("$2 $1"))
		)
    .filter(Objects::nonNull)
		.map(String::trim)														// Trim
		.map(String::toLowerCase)											// Lowercase
		.flatMap(SPLITTING_DELIMETER::splitAsStream)	// Split
		.filter(Predicate.not(stopwords::contains)) 	// Remove stop words
		.collect(Collectors.joining(" "));						// Rejoin with single spacing.
	}
	
	private static String generateNewBlockingString(final String inputString, @NotNull List<String> qualifiers) {
		// Replace Names
			return Stream.concat(
				qualifiers.stream()
					.map(DCBStringUtilities::toNoneDiacriticAlphaNumeric),
				Stream.of(NAMES.matcher(toNoneDiacriticAlphaNumeric(inputString)).replaceAll("$2 $1"))
			)
			.map(StringUtils::trimToNull)
			.filter(Objects::nonNull)													// Trim blanks and nulls
			.map(String::toLowerCase)													// Lowercase
			.flatMap(SPLITTING_DELIMETER::splitAsStream)			// Split
			.filter(Predicate.not(stopwords::contains)) 			// Remove stop words
			.filter(StringUtils::hasText)											// Trim blanks and nulls
			.sorted()																					// Sort the members
			.collect(Collectors.joining(" "));								// Rejoin with single spacing.
	}

//	public static final String generateOldBlockingString(String inputString, List<String> qualifiers) {
//		if (inputString == null)
//			return null;
//		
//		// If we have additional qualifying terms, blend them here. If the terms have come from Marc, they
//		// could have all kinds of punc and diacritics so adding them now will let the normalisation take care
//		// of them cleanly.
//		if ( ( qualifiers != null ) && ( qualifiers.size() > 0 ) )
//			inputString = inputString + " " + String.join(" ", qualifiers);
//
//		List<String> words = Arrays
//				.stream(Normalizer.normalize(inputString, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
//						.replaceAll("[^\\p{Alnum}\\s]", "").toLowerCase().split("\\s+"))
//				.filter(word -> !stopwords.contains(word)).distinct().collect(Collectors.toList());
//
//		words.sort(String::compareTo);
//		return String.join(" ", words);
//	}

	public static final UUID uuid5ForIdentifier(@NotNull final String namespace, @NotNull final String value,
			@NotNull final UUID ownerId) {

		final String concat = UUID5_ID_PREFIX + ":" + namespace + ":" + value + ":" + ownerId.toString();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	// In Sierra, the APIs return a URI with the actual identifier at the end. this is great for HATEOS style
	// interactions, but sucks for storage in the DB. Strip  off the identifier part and return just that
	public static final String deRestify(String uri) {
		String result = null;
		if ( uri != null ) {
			int last_slash_position = uri.lastIndexOf('/');
			if ( last_slash_position >= 0 )
				result = uri.substring(last_slash_position+1);
			else
				result = uri;
		}
		return result;
	}

	public static final String toCsv(Iterable<? extends CharSequence> vals) {
		if (vals == null) return null;
		return StreamSupport.stream(vals.spliterator(), false)
			.collect(Collectors.joining(","));
	}
}
