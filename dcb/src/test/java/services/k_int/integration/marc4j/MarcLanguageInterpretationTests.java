package services.k_int.integration.marc4j;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.impl.MarcFactoryImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class MarcLanguageInterpretationTests {
	private static final MarcFactory marcFactory = new MarcFactoryImpl();

	@Test
	void shouldFindNoLanguagesWhenNo041aSubfieldOr008fieldArePresent() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

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

	@Test
	void shouldSplitConcatenatedLanguageCodesWithinSingle041aSubfield() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

		/* In 2001: the practice of placing multiple language codes in one subfield,
			e.g., $aengfreger, was made obsolete and subfields $a, ... to Repeatable (R)

			from https://www.loc.gov/marc/bibliographic/bd041.html
		*/
		addLanguageCodeField(marcRecord, "itamac");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("ita", "mac"));
	}

	@Test
	void shouldSplitConcatenatedLanguageCodesSeparateBySpace() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

		addLanguageCodeField(marcRecord, "rom som");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("rom", "som"));
	}

	@Test
	void shouldNotSplitNonDivisibleLanguageCodes() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

		// Language codes should be
		addLanguageCodeField(marcRecord, "swedish");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("swedish"));
	}

	@Test
	void shouldFallbackTo008FieldWhen041aSubfieldIsNotPresent() {
		// Arrange
		final var marcRecord = createMarcRecord();

		final var fixedLengthControlField = marcFactory.newControlField("008", "741030s1958    nyu           000 0 per u");

		marcRecord.addVariableField(fixedLengthControlField);

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("per"));
	}

	@Test
	void shouldNotFallbackTo008FieldWhen041aSubfieldIsPresent() {
		// Arrange
		final var marcRecord = createMarcRecord();

		final var fixedLengthControlField = marcFactory.newControlField("008", "741030s1958    nyu           000 0 pol u");

		marcRecord.addVariableField(fixedLengthControlField);

		addLanguageCodeField(marcRecord, "eng");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("eng"));
	}

	@Test
	void shouldNotFallbackTo008FieldWhenFixedLengthLanguageCodeIsBlank() {
		// Arrange
		final var marcRecord = createMarcRecord();

		final var fixedLengthControlField = marcFactory.newControlField("008", "741030s1958    nyu           000 0     u");

		marcRecord.addVariableField(fixedLengthControlField);

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, empty());
	}

	@Test
	void shouldNotFallbackTo008FieldWhenFixedLengthFieldIsTooShort() {
		// Arrange
		final var marcRecord = createMarcRecord();

		final var fixedLengthControlField = marcFactory.newControlField("008", "741030s1958    nyu           000 0 en");

		marcRecord.addVariableField(fixedLengthControlField);

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, empty());
	}

	@Test
	void shouldNotFallbackTo008FieldWhenFixedLengthFieldIsEmpty() {
		// Arrange
		final var marcRecord = createMarcRecord();

		final var fixedLengthControlField = marcFactory.newControlField("008", "");

		marcRecord.addVariableField(fixedLengthControlField);

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, empty());
	}

	@Test
	void shouldNotFallbackTo008FieldWhenFixedLengthFieldIsNull() {
		// Arrange
		final var marcRecord = createMarcRecord();

		final var fixedLengthControlField = marcFactory.newControlField("008", null);

		marcRecord.addVariableField(fixedLengthControlField);

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, empty());
	}

	@ParameterizedTest
	@CsvSource({"GRC,grc", "Eng,eng"})
	void languageCodesShouldBeLowerCase(String inputLanguageCode, String expectedLanguageCode) {
		/* "Capitalization - All language codes are recorded in lowercase alphabetic characters."
			from https://www.loc.gov/marc/bibliographic/bd041.html
		*/

		// Arrange
		final var marcRecord = createMarcRecord(inputLanguageCode);

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder(expectedLanguageCode));
	}

	@Test
	void shouldNotIncludeDuplicateLanguageCodes() {
		// Arrange
		final var marcRecord = createMarcRecord("eng", "eng");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, containsInAnyOrder("eng"));
	}

	@Test
	void shouldTolerateEmptyLanguageCode() {
		// Arrange
		final var marcRecord = marcFactory.newRecord();

		addLanguageCodeField(marcRecord, "");

		// Act
		final var languages = interpretLanguages(marcRecord);

		// Assert
		assertThat(languages, empty());
	}

	List<String> interpretLanguages(final Record marcRecord) {
		log.debug("MARC record: {}", marcRecord);

		return Marc4jRecordUtils.interpretLanguages(marcRecord);
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
