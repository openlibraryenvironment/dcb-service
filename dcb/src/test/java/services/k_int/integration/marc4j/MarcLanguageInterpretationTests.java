package services.k_int.integration.marc4j;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.marc4j.marc.impl.MarcFactoryImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class MarcLanguageInterpretationTests {
	private static final MarcFactory marcFactory = new MarcFactoryImpl();

	@Test
	void shouldFindNoLanguagesWhenNo041FieldIsPresent() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, is(empty()));
	}

	@Test
	void shouldFindNoLanguagesWhenNo041aSubfieldPresent() {
		// Arrange
		final var marcRecord = createMarcRecord();

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, is(empty()));
	}

	@Test
	void shouldFindSingleLanguageForSingle041aSubfieldWithSingleLanguageCode() {
		// Arrange
		final var marcRecord = createMarcRecord("eng");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("eng"));
	}

	@Test
	void shouldFindMultipleLanguagesForMultiple041aSubfieldsEachWithSingleLanguageCode() {
		// Arrange
		final var marcRecord = createMarcRecord("eng", "fre");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("eng", "fre"));
	}

	@Test
	void shouldFindMultipleLanguagesForMultiple041aSubfieldsInSeparateFields() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

		addLanguageCodeField(marcRecord, "ger");
		addLanguageCodeField(marcRecord, "swe");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("ger", "swe"));
	}

	List<String> interpretLanguages(final Record marcRecord) {
		log.debug("MARC record: {}", marcRecord);

		assertThat(marcFactory.validateRecord(marcRecord), is(true));

		final var languageCodeFields = marcRecord.getVariableFields("041");

		return languageCodeFields.stream()
			.flatMap(MarcLanguageInterpretationTests::parseSubFields)
			.toList();
	}

	private static Stream<String> parseSubFields(final VariableField languageCodeField) {
		if (languageCodeField instanceof DataField languageDataField) {
			return languageDataField.getSubfields('a')
				.stream()
				.map(Subfield::getData);
		}
		else {
			return Stream.of();
		}
	}

	private static Record createMarcRecord(String... languageCodes) {
		final var marcRecord = marcFactory.newRecord();

		final var languageCodeField = marcFactory.newDataField("041", '#', '#');

		final var languageCodesList = List.of(languageCodes);

		languageCodesList.forEach(languageCode -> {
			final var aSubfield = marcFactory.newSubfield('a', languageCode);

			languageCodeField.addSubfield(aSubfield);
		});

		marcRecord.addVariableField(languageCodeField);

		return marcRecord;
	}

	private static void addLanguageCodeField(Record marcRecord, String languageCode) {
		final var languageCodeField = marcFactory.newDataField("041", '#', '#');

		final var aSubfield = marcFactory.newSubfield('a', languageCode);

		languageCodeField.addSubfield(aSubfield);

		marcRecord.addVariableField(languageCodeField);
	}
}
