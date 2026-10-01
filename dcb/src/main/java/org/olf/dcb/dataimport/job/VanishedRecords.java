package org.olf.dcb.dataimport.job;

import java.util.List;

import org.olf.dcb.dataimport.job.model.SourceRecord;

import reactor.core.publisher.Flux;

/**
 * @param held bibs DCB holds for the source
 * @param vanished how many of those the source no longer lists as live
 * @param sample at most a few of the vanished source record ids, for an operator to check by hand
 * @param deletions one deleted record per stored row of a vanished bib; nothing is read until
 *        subscribed, so a caller that only reports never touches them
 */
public record VanishedRecords(long held, long vanished, List<String> sample,
	Flux<SourceRecord> deletions) {
}
