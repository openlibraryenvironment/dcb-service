package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.build008;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.buildLeader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * DCB-2215: virtual bibs were being created in Polaris as "Book" regardless of the real
 * material type. The host LMS determines material type from leader position 06, so these
 * tests pin the leader layout and the 008 length rather than trusting the literals by eye.
 */
class ApplicationServicesClientMarcTests {
	private static final int LEADER_TYPE_OF_RECORD_POSITION = 6;
	private static final int LEADER_BIBLIOGRAPHIC_LEVEL_POSITION = 7;

	@Test
	@DisplayName("Leader should be exactly 24 characters long")
	void leaderShouldBeTwentyFourCharacters() {
		assertThat(buildLeader("a").length(), is(24));
		assertThat(buildLeader("g").length(), is(24));
	}

	@ParameterizedTest
	@DisplayName("Leader should carry the source type of record through to position 06")
	@CsvSource({
		"a, a",  // language material - book
		"g, g",  // projected medium - DVD / video recording
		"i, i",  // nonmusical sound recording - audiobook
		"j, j",  // musical sound recording
		"c, c",  // notated music
		"e, e",  // cartographic material
		"k, k",  // two dimensional non projectable graphic
		"o, o",  // kit
		"t, t"   // manuscript language material
	})
	void leaderShouldUseSourceTypeOfRecord(String typeOfRecord, char expectedTypeOfRecord) {
		final var leader = buildLeader(typeOfRecord);

		assertThat(leader.charAt(LEADER_TYPE_OF_RECORD_POSITION), is(expectedTypeOfRecord));
	}

	@ParameterizedTest
	@DisplayName("Leader should fall back to language material only when no type is available")
	@NullSource
	@ValueSource(strings = { "", "   " })
	void leaderShouldFallBackToLanguageMaterialWhenTypeIsMissing(String typeOfRecord) {
		final var leader = buildLeader(typeOfRecord);

		assertThat(leader.charAt(LEADER_TYPE_OF_RECORD_POSITION), is('a'));
		assertThat(leader.length(), is(24));
	}

	@Test
	@DisplayName("Leader should describe a monograph at position 07")
	void leaderShouldDescribeAMonograph() {
		assertThat(buildLeader("g").charAt(LEADER_BIBLIOGRAPHIC_LEVEL_POSITION), is('m'));
	}

	@Test
	@DisplayName("Leader should not be prefixed with a display label")
	void leaderShouldNotBePrefixedWithDisplayLabel() {
		// The previous implementation embedded the literal "LDR    " inside the leader,
		// which shifted every position and left position 06 holding a space
		final var leader = buildLeader("g");

		assertThat(leader.startsWith("LDR"), is(false));
		assertThat(leader.charAt(LEADER_TYPE_OF_RECORD_POSITION), is(not(' ')));
	}

	@Test
	@DisplayName("008 should be exactly 40 characters long")
	void controlField008ShouldBeFortyCharacters() {
		assertThat(build008().length(), is(40));
	}

	@Test
	@DisplayName("008 should be positionally coded rather than random")
	void controlField008ShouldBePositionallyCoded() {
		final var field008 = build008();

		// 00-05 date entered on file, six digits
		assertThat(field008.substring(0, 6).matches("\\d{6}"), is(true));
		// 06 type of date - no dates given
		assertThat(field008.charAt(6), is('n'));
		// 35-37 language - undetermined
		assertThat(field008.substring(35, 38), is("und"));
		// 39 cataloguing source - other
		assertThat(field008.charAt(39), is('d'));
	}

	@Test
	@DisplayName("008 should use the fill character for uncoded material specific positions")
	void controlField008ShouldFillMaterialSpecificPositions() {
		// 18-34 are material specific and we hold no reliable data for them at creation time
		assertThat(build008().substring(18, 35), is("|".repeat(17)));
	}
}
