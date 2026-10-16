package org.olf.dcb.indexing.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Classifications sorted the way a shelf reads.
 *
 * The first two tests carry a correction. The defect is often given as "638.09 sorts after
 * 638.1 lexically", which is false — a decimal fraction compared digit by digit is already in
 * shelf order. The defect that IS real is width: a cataloguer writes 5 where the shelf reads
 * 005, and PS50 sorts after PS4000 as a string.
 *
 * Every assertion here is a comparison rather than a snapshot of a key, because the key's
 * exact bytes are an implementation detail and its ORDER is the contract.
 */
class ShelfSortKeyTests {

	@Test
	@DisplayName("the defect is WIDTH, not the decimal point")
	void theDefectIsWidthNotTheDecimalPoint() {
		// It is often said that 638.09 sorts after 638.1 lexically. It does not: compared digit by
		// digit a decimal fraction is already in shelf order, and this asserts that so the
		// correction cannot quietly be un-made.
		assertThat("638.09".compareTo("638.1") < 0, is(true));

		// This is the one that is really broken. 5 shelves at 005, before 10 - and as
		// strings "5" sorts AFTER "10".
		assertThat("5".compareTo("10") > 0, is(true));
	}

	@Test
	@DisplayName("a short Dewey class shelves where its three-digit form does")
	void aShortDeweyClassIsPaddedToThreeDigits() {
		assertThat(ShelfSortKey.dewey("5"), lessThan(ShelfSortKey.dewey("10")));
		assertThat(ShelfSortKey.dewey("10"), lessThan(ShelfSortKey.dewey("100")));
	}

	@Test
	@DisplayName("Dewey decimals stay in order once padded")
	void deweyDecimalsStayOrdered() {
		assertThat(ShelfSortKey.dewey("638.09"), lessThan(ShelfSortKey.dewey("638.1")));
		assertThat(ShelfSortKey.dewey("638.1"), lessThan(ShelfSortKey.dewey("638.12")));

		// Trailing zeros are the same place on the shelf and must be the same key, or a
		// browse window would show one book twice under two adjacent headings.
		assertThat(ShelfSortKey.dewey("638.1"), is(ShelfSortKey.dewey("638.10")));
	}

	@Test
	@DisplayName("MARC segmentation marks inside a Dewey number are not the end of it")
	void deweySegmentationMarksAreIgnored() {
		assertThat(ShelfSortKey.dewey("823/.914"), is("823.914000"));
		assertThat(ShelfSortKey.dewey("641.5/973"), is("641.597300"));
		assertThat(ShelfSortKey.dewey("641.5'973"), is("641.597300"));
	}

	@Test
	@DisplayName("a whole Dewey ladder comes back in shelf order")
	void aDeweyLadderSortsCorrectly() {
		final var shelf = List.of("6", "63", "638", "638.09", "638.1", "638.12", "638.2", "639");

		assertThat(sortedByKey(shelf, ShelfSortKey::dewey), contains(shelf.toArray()));
	}

	@Test
	@DisplayName("LCC class letters sort as letters, and a shorter class comes first")
	void lccClassLettersSortShortestFirst() {
		// P before PA before PS, which is shelf order. This is why the letters are padded
		// with SPACES: space is below every letter, so "P  " precedes "PA ". Zero-padding
		// would give a different order, and a plausible one.
		assertThat(ShelfSortKey.lcc("P100"), lessThan(ShelfSortKey.lcc("PA100")));
		assertThat(ShelfSortKey.lcc("PA100"), lessThan(ShelfSortKey.lcc("PS100")));
	}

	@Test
	@DisplayName("an LCC class number sorts as a number")
	void lccClassNumbersSortNumerically() {
		// "PS50" > "PS4000" as a string; PS 50 is well before PS 4000 on the shelf.
		assertThat("PS50".compareTo("PS4000") > 0, is(true));

		assertThat(ShelfSortKey.lcc("PS50"), lessThan(ShelfSortKey.lcc("PS4000")));
	}

	@Test
	@DisplayName("an LCC cutter sorts as a decimal fraction")
	void lccCuttersSortAsFractions() {
		// .Y55 is before .Y6, because a cutter is read as a fraction: .55 < .6. Read as
		// integers it would be the other way round.
		assertThat(ShelfSortKey.lcc("PS3566.Y55"), lessThan(ShelfSortKey.lcc("PS3566.Y6")));
	}

	@Test
	@DisplayName("a whole LCC sequence comes back in shelf order")
	void anLccSequenceSortsCorrectly() {
		final var shelf = List.of(
			"P100", "PA50", "PA4000", "PS3566.Y55", "PS3566.Y55 A6", "PS3566.Y6", "PS3600");

		assertThat(sortedByKey(shelf, ShelfSortKey::lcc), contains(shelf.toArray()));
	}

	@Test
	@DisplayName("a classification this cannot read produces NO key, not a guess")
	void anUnreadableClassificationProducesNoKey() {
		// A half-normalised shelf is worse than no shelf, because it looks right and
		// is not. A record with no key is absent from the sequence, which a reader notices; a
		// record with a plausible wrong key sits in the wrong place, which nobody notices.
		assertThat(ShelfSortKey.dewey("FIC BRO"), is(nullValue()));
		assertThat(ShelfSortKey.dewey(""), is(nullValue()));
		assertThat(ShelfSortKey.lcc("638.1"), is(nullValue()));
		assertThat(ShelfSortKey.lcc("SHELF 3"), is(nullValue()));
	}

	@Test
	@DisplayName("MARC 084's 'other' scheme is never normalised")
	void theOtherSchemeIsNeverNormalised() {
		// 084 names its scheme in $2 and carries anything from a local shelving code to a
		// national scheme. Normalising an unknown scheme means guessing at its shape, which
		// is the failure this class exists to avoid — so it is left unsorted and honest.
		assertThat(ShelfSortKey.of("other", "638.1"), is(nullValue()));
		assertThat(ShelfSortKey.of("other", "Z 123"), is(nullValue()));
	}

	@Test
	@DisplayName("an unknown scheme name produces no key rather than being read as Dewey")
	void anUnknownSchemeProducesNoKey() {
		assertThat(ShelfSortKey.of("udc", "638.1"), is(nullValue()));
		assertThat(ShelfSortKey.of(null, "638.1"), is(nullValue()));
		assertThat(ShelfSortKey.of("dewey", null), is(nullValue()));
	}

	@Test
	@DisplayName("the scheme name is matched however the record cased it")
	void theSchemeNameIsCaseInsensitive() {
		assertThat(ShelfSortKey.of("DEWEY", "638.1"), is(ShelfSortKey.dewey("638.1")));
		assertThat(ShelfSortKey.of("Lcc", "PS3566"), is(ShelfSortKey.lcc("PS3566")));
	}

	@Test
	@DisplayName("a key fits the field's ignore_above, or the sort would silently drop it")
	void keysFitTheMappedFieldWidth() {
		// members.shelfSort.* is a keyword with ignore_above 64. A longer value is stored but
		// NOT indexed, so it would vanish from the sort rather than error - the quiet kind of
		// failure. This is the longest realistic LCC: three letters, four digits, decimals
		// and three cutters.
		final var longest = ShelfSortKey.lcc("PZS3566.1234 Y5555 A6666 B7777");

		assertThat(longest.length() <= 64, is(true));
	}

	private static List<String> sortedByKey(List<String> shelf,
		java.util.function.Function<String, String> key) {

		// Reversed first, so a sort that does nothing at all cannot pass.
		return Stream.of(shelf.reversed())
			.flatMap(List::stream)
			.sorted(java.util.Comparator.comparing(key))
			.toList();
	}
}
