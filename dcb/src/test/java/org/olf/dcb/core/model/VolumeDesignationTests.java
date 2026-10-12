package org.olf.dcb.core.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class VolumeDesignationTests {
	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(delimiter = '|', value = {
		// Sierra item volume fields
		"v.1, p:1           | v1p1",
		"Vol. 1             | v1",
		"v.2                | v2",
		"V. 2               | v2",
		"v 2                | v2",
		"v2                 | v2",
		"vol 2              | v2",
		"Volume 2           | v2",
		"v.02               | v2",
		"v.10               | v10",
		"v.0                | v0",
		"v.1 pt.2           | v1p2",
		"v.1 part 2         | v1p2",
		"v.1 p.2            | v1p2",
		"no. 5              | n5",
		"No.5               | n5",
		"number 5           | n5",
		"v.3 no.12          | v3n12",
		"v.3, 1998          | v3y1998",
		"1998 v.3           | v3y1998",
		"no.4 v.2 pt.1 2001 | v2p1n4y2001",
		"2020               | y2020",
		// Polaris volume numbers
		"Vol [ 2 ]          | v2",
		"Vol [ 2 ] Pt [ 1 ] | v2p1",
		"Vol [ 02 ] Pt [ 1 ] 2020 | v2p1y2020",
		"Vol[2]Pt[1]        | v2p1",
		// Already canonical
		"v1p2               | v1p2",
		"v3y1998            | v3y1998",
		// Not recognised: lowercased, punctuation and spaces removed
		"volume one         | volumeone",
		"v.2 suppl.         | v2suppl",
		"v.1 c.2            | v1c2",
		"Index              | index",
		"2                  | 2",
		"v.                 | v",
		"v.1 v.2            | v1v2",
		"1998-1999          | 1998-1999",
		"v.1-2              | v1-2",
		"v. 1 2             | v1-2",
		"Sept. 2001         | sept2001",
	})
	void shouldNormaliseVolumeStatement(String statement, String expected) {
		assertThat(VolumeDesignation.normalise(statement), is(expected));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "\t", "--", ". ," })
	void shouldHaveNoDesignationForBlankStatement(String statement) {
		assertThat(VolumeDesignation.normalise(statement), is(nullValue()));
	}

	@ParameterizedTest(name = "\"{0}\" is \"{1}\"")
	@CsvSource(delimiter = '|', value = {
		"v.2                | Vol [ 2 ]",
		"Vol. 2             | v2",
		"v.1, p:1           | Vol [ 1 ] Pt [ 1 ]",
		"v.3, 1998          | Vol [ 3 ] 1998",
		"Volume one         | volume one",
	})
	void shouldGiveTheSameDesignationToTheSameVolumeWrittenDifferently(String first, String second) {
		assertThat(VolumeDesignation.normalise(first), is(VolumeDesignation.normalise(second)));
	}

	@ParameterizedTest(name = "\"{0}\" is not \"{1}\"")
	@CsvSource(delimiter = '|', value = {
		"v.1                | v.2",
		"v.12               | v.1-2",
		"v.1                | v.1 pt.2",
		"v.3 1998           | v.3 1999",
	})
	void shouldDistinguishDifferentVolumes(String first, String second) {
		assertThat(VolumeDesignation.normalise(first), is(not(VolumeDesignation.normalise(second))));
	}

	@ParameterizedTest(name = "a request for \"{0}\" is met by a copy stating \"{1}\"")
	@CsvSource(delimiter = '|', value = {
		"v.22               | Vol [ 22].(1990)",
		"v.22 1990          | Vol [ 22].(1990)",
		"Vol. 2             | v.2",
		"v.2 pt.1           | Vol [ 2 ] Pt [ 1 ]",
		"Volume one         | volume one",
	})
	void shouldMeetARequestWithACopyOfThatVolume(String requested, String copy) {
		assertThat(VolumeDesignation.matches(requested, copy), is(true));
	}

	@ParameterizedTest(name = "a request for \"{0}\" is not met by a copy stating \"{1}\"")
	@CsvSource(delimiter = '|', value = {
		"v.22 1991          | Vol [ 22].(1990)",
		"v.22 1990          | Vol [ 22]",
		"v.22               | v.23",
		"v.2                | v.2 pt.1",
		"v.45               | v.45 no.4",
		"v.45               | Vol [ 44-48].(1956-1960)",
		"v.2                | ''",
	})
	void shouldNotMeetARequestWithACopyOfAnotherOrPartialVolume(String requested, String copy) {
		assertThat(VolumeDesignation.matches(requested, copy), is(false));
	}

	// The open entries, "10v." and "v. 423 p." are MOBIUS FOLIO extents measured on 12 Oct
	@ParameterizedTest(name = "\"{0}\" is several volumes")
	@ValueSource(strings = {
		"3 v. : ill. ; 24 cm",
		"10v., v.10 supplementary",
		"12 vols.",
		"2 volumes",
		"1 online resource (3 v.)",
		"v. 1-4 ; 28 cm",
		"v. : ill. ; 24 cm",
		"v.",
		"volumes <1-5, pt. 1> :",
		"v. <1-3, pt. 2, 4, pt. 2-5, pt. 2 > :",
	})
	void shouldRecogniseAnExtentOfSeveralVolumes(String physicalDescription) {
		assertThat(VolumeDesignation.describesSeveralVolumes(physicalDescription), is(true));
	}

	@ParameterizedTest(name = "\"{0}\" is not several volumes")
	@NullSource
	@ValueSource(strings = {
		"",
		"320 p. : ill. ; 24 cm",
		"xv, 423 p.",
		"v. 423 p. ;",
		"1 v. (various pagings)",
		"2 v. in 1",
		"3 videodiscs (180 min.)",
	})
	void shouldNotTakeAnyOtherExtentForSeveralVolumes(String physicalDescription) {
		assertThat(VolumeDesignation.describesSeveralVolumes(physicalDescription), is(false));
	}

	@ParameterizedTest(name = "\"{0}\" reads back as itself")
	@ValueSource(strings = { "v22y1990", "v2p1", "v3n4y1998" })
	void shouldReadACanonicalFormBackAsItself(String canonical) {
		assertThat(VolumeDesignation.normalise(canonical), is(canonical));
	}
}
