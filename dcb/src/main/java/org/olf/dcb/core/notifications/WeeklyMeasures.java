package org.olf.dcb.core.notifications;

import java.time.Instant;

import org.olf.dcb.storage.WeeklyMeasureRepository;
import org.olf.dcb.storage.WeeklyMeasureRepository.Figure;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/** A stored measure's figure for one library, as it stands: what the job records and the trend compares. */
@Singleton
public class WeeklyMeasures {
	private final WeeklyMeasureRepository repository;

	public WeeklyMeasures(WeeklyMeasureRepository repository) {
		this.repository = repository;
	}

	/** Empty when nothing happened to measure: no request reached the hold shelf in the window. */
	public Mono<Figure> figure(String agencyCode, TrendMeasure measure, Instant weekStart, Instant until) {
		return switch (measure) {
			case MEDIAN_TURNAROUND_HOURS -> Mono.from(repository.medianTurnaroundHours(agencyCode, weekStart, until))
				.filter(figure -> figure.value() != null && figure.samples() > 0);
			case AWAITING_PICKUP -> Mono.from(repository.awaitingPickup(agencyCode));
			default -> Mono.error(new IllegalArgumentException(measure + " is counted from the audit trail, not stored"));
		};
	}
}
