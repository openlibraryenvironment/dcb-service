package services.k_int.integration.marc4j;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.hasText;
import static java.util.Collections.emptyList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.marc4j.marc.ControlField;
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
	
	/**
	 * Parse the 041 language code fields
	 * Based upon this <a href="https://www.loc.gov/marc/bibliographic/bd041.html">document</a>
	 *
	 * @param marcRecord MARC record to parse
	 * @return list of non-duplicated language codes from 041a subfields, or 008 field, if none present
	 */
	static List<String> interpretLanguages(Record marcRecord) {
		final var languageCodeFields = marcRecord.getVariableFields("041");

		if (isEmpty(languageCodeFields)) {
			return parseLanguageInGeneralInformationField(marcRecord);
		}

		final var languageCodes = languageCodeFields.stream()
			.flatMap(Marc4jRecordUtils::parseSubfields)
			.filter(StringUtils::isNotEmpty)
			.map(String::toLowerCase)
			.map(Marc4jRecordUtils::removeWhitespace)
			.flatMap(Marc4jRecordUtils::splitConcatenatedLanguageCodes)
			.distinct()
			.toList();

		if (isEmpty(languageCodes)) {
			return parseLanguageInGeneralInformationField(marcRecord);
		}
		else {
			return languageCodes;
		}
	}

	/**
	 * Parse the 008 general information field for language code
	 * Based upon this <a href="https://www.loc.gov/marc/bibliographic/bd008.html">document</a>
	 *
	 * @param marcRecord MARC record to parse
	 * @return list of only language code from 008 field or empty list
	 */
	private static List<String> parseLanguageInGeneralInformationField(Record marcRecord) {
		final var generalInformationField = marcRecord.getVariableField("008");

		if (generalInformationField instanceof ControlField generalInfoControlField) {
			final var generalInfo = getValue(generalInfoControlField, ControlField::getData);

			if (StringUtils.isEmpty(generalInfo)) {
				return emptyList();
			}

			if (generalInfo.length() < 38) {
				return emptyList();
			}

			final var language = generalInfo.substring(35, 38);

			if (hasText(language)) {
				return List.of(language);
			}

			return emptyList();
		}
		else {
			return emptyList();
		}
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

	static Stream<String> parseSubfields(final VariableField field) {
		if (field instanceof DataField dataField) {
			final var subfields = dataField.getSubfields('a');

			if (isEmpty(subfields)) {
				return Stream.empty();
			}

			return subfields
				.stream()
				.map(Subfield::getData);
		}
		else {
			return Stream.empty();
		}
	}

	private static String removeWhitespace(String s) {
		return s.replaceAll("\\s", "");
	}
}
