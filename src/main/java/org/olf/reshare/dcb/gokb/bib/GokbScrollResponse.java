package org.olf.reshare.dcb.gokb.bib;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbScrollResponse(
		String result,
		int scrollSize,
		int lastPage,
		String scrollId,
		boolean hasMoreRecords,
		int size,
		int total,
		List<GokbTipp> records) {
}
