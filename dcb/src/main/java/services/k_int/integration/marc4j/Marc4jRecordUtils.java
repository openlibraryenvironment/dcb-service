package services.k_int.integration.marc4j;

import java.util.List;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.marc4j.marc.DataField;
import org.marc4j.marc.Leader;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;

import io.micronaut.core.util.StringUtils;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public interface Marc4jRecordUtils {
	
	public static Stream<String> concatSubfieldData(final Record marcRecord, @NotEmpty final String tag, @NotEmpty final String subfields) {
		return concatSubfieldData(marcRecord, tag, subfields, " ");
	}
	
	public static Stream<String> concatSubfieldData(final Record marcRecord, @NotEmpty final String tag, @NotEmpty final String subfields, @NotNull final String delimiter) {
	
		return marcRecord.getVariableFields(tag).stream()
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.map( field -> extractOrderedSubfields(field, subfields)
					.collect(Collectors.joining(delimiter)));
	}
	
	public static Stream<String> extractOrderedSubfields( DataField field, String orderedSpec ) {
		
		if (field == null) return Stream.empty();
		
		return orderedSpec.chars()
			.mapToObj( v -> (char)v )
			.flatMap( charVal -> {
				return field.getSubfields(charVal).stream();
			})
			.map( Subfield::getData )
		;
	}
	
	public static String typeFromLeader ( Leader leader ) {
		
		return switch ( leader.getTypeOfRecord() ) {
			case 'a' -> {
				yield switch (leader.getImplDefined1()[0]) {
					case 'a', 'c', 'd', 'm' -> "Book";
					default -> "Continuing Resources";
				};
			}
			case 'c', 'd' -> "Notated Music";
			case 'e', 'f' -> "Maps";
			case 'g' -> "Video Recording";
                        case 'i' -> "Audiobook";
                        case 'j' -> "Music";
                        case 'k' -> "Visual Material";
                        case 'o' -> "Kit";
			case 'm' -> "Electronic Resource";
			case 'p' -> "Mixed Material";
			case 't' -> "Book";
			case 'r' -> "Object";
			default -> "Unknown";
		};
	}

	static List<String> interpretLanguages(Record marcRecord) {
		final var languageCodeFields = marcRecord.getVariableFields("041");

		return languageCodeFields.stream()
			.flatMap(Marc4jRecordUtils::parseSubFields)
			.filter(StringUtils::isNotEmpty)
			.map(String::toLowerCase)
			.flatMap(Marc4jRecordUtils::splitConcatenatedLanguageCodes)
			.toList();
	}

	static Stream<String> splitConcatenatedLanguageCodes(String languageCode) {
		if (languageCode.length() % 3 != 0) {
			return Stream.of(languageCode);
		}

		return Pattern.compile(".{1,3}")
			.matcher(languageCode)
			.results()
			.map(MatchResult::group);
	}

	static Stream<String> parseSubFields(final VariableField field) {
		if (field instanceof DataField dataField) {
			return dataField.getSubfields('a')
				.stream()
				.map(Subfield::getData);
		}
		else {
			return Stream.of();
		}
	}
}
