package org.olf.dcb.core.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import services.k_int.interaction.oaipmh.OaiRecord;
import services.k_int.interaction.oaipmh.OaiRecord.Header;

class OaiPmhResumptionPolicyTest {
	@Test
	void highestTimestampUsesObservedSourceWatermark() {
		Instant fetchTime = Instant.parse("2026-08-01T12:05:00Z");
		Instant highestSeen = Instant.parse("2026-08-01T12:04:30Z");

		assertEquals(highestSeen, OaiPmhIngestSource.nextHarvestFrom(
			OaiPmhResumptionPolicy.HIGHEST_TIMESTAMP, fetchTime, highestSeen));
	}

	@Test
	void internalClockPreservesExistingFolioCheckpoint() {
		Instant fetchTime = Instant.parse("2026-08-01T12:05:00Z");
		Instant highestSeen = Instant.parse("2026-08-01T12:04:30Z");

		assertEquals(fetchTime, OaiPmhIngestSource.nextHarvestFrom(
			OaiPmhResumptionPolicy.INTERNAL_CLOCK, fetchTime, highestSeen));
	}

	@Test
	void greatestDatestampIsSelectedWithoutAssumingRecordOrder() {
		Instant previous = Instant.parse("2026-08-01T12:00:00Z");
		Instant greatest = Instant.parse("2026-08-01T12:04:30Z");

		assertEquals(greatest, OaiPmhIngestSource.highestRecordTimestampSeen(previous, List.of(
			recordAt(greatest),
			recordAt(Instant.parse("2026-08-01T12:03:00Z"))
		)));
	}

	@Test
	void previousWatermarkIsRetainedForEmptyOrOlderPages() {
		Instant previous = Instant.parse("2026-08-01T12:04:30Z");

		assertEquals(previous, OaiPmhIngestSource.highestRecordTimestampSeen(previous, List.of()));
		assertEquals(previous, OaiPmhIngestSource.highestRecordTimestampSeen(previous,
			List.of(recordAt(Instant.parse("2026-08-01T12:03:00Z")))));
	}

	private OaiRecord recordAt(Instant datestamp) {
		Header header = mock(Header.class);
		when(header.datestamp()).thenReturn(datestamp);
		OaiRecord record = mock(OaiRecord.class);
		when(record.header()).thenReturn(header);
		return record;
	}
}
