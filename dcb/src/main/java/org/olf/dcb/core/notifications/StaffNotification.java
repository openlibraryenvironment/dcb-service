package org.olf.dcb.core.notifications;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One item in a library's notification feed. The id is stable for the same fact, so a browser
 * can remember it as read. No field carries a patron or the text of a note.
 */
@Serdeable
public record StaffNotification(
	String id,
	String kind,
	String occurredAt,
	@Nullable Integer count,
	@Nullable String bucket,
	@Nullable String patronRequestId,
	@Nullable String authorAgencyCode,
	@Nullable String measure,
	@Nullable Double current,
	@Nullable Double baseline,
	@Nullable String direction) {

	static StaffNotification count(HomeBucket bucket, long count, String day, String since) {
		return new StaffNotification("counts:%s:%s:%d".formatted(bucket.id(), day, count),
			bucket.kind().name(), since, (int) count, bucket.id(), null, null, null, null, null, null);
	}

	static StaffNotification note(String patronRequestId, String noteId, String occurredAt,
		@Nullable String authorAgencyCode) {

		return new StaffNotification("note:%s:%s".formatted(patronRequestId, noteId),
			StaffNotificationKind.NOTE_ADDED.name(), occurredAt, null, null, patronRequestId,
			authorAgencyCode, null, null, null, null);
	}

	static StaffNotification trend(TrendMeasure measure, String week, String occurredAt, long current,
		double baseline) {

		return new StaffNotification("trend:%s:%s".formatted(measure.name(), week),
			StaffNotificationKind.TREND.name(), occurredAt, null, null, null, null, measure.name(),
			(double) current, baseline, "UP");
	}
}
