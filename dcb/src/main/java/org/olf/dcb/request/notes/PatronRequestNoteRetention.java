package org.olf.dcb.request.notes;

import static org.olf.dcb.core.model.PatronRequest.Status.ARCHIVED;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.COMPLETED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.storage.PatronRequestNoteRepository;
import org.olf.dcb.storage.PatronRequestTagRepository;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import services.k_int.micronaut.scheduling.processor.AppTask;

/**
 * Notes are free text about a loan and often mention the patron, so they and the request's tags
 * are deleted a year after the request ends, and at once if the request itself has gone.
 * <p>
 * No cluster lock: the deletes are idempotent, so two instances running a pass together is harmless.
 */
@Slf4j
@Singleton
public class PatronRequestNoteRetention {
	public static final Duration RETENTION = Duration.ofDays(365);

	// ERROR is left out because a request in error can still be rolled back into the workflow
	static final Set<String> TERMINAL_STATUSES = Stream.of(FINALISED, CANCELLED, COMPLETED, ARCHIVED)
		.map(Enum::name)
		.collect(Collectors.toUnmodifiableSet());

	private final PatronRequestNoteRepository noteRepository;
	private final PatronRequestTagRepository tagRepository;
	private final Clock clock;

	public PatronRequestNoteRetention(PatronRequestNoteRepository noteRepository,
		PatronRequestTagRepository tagRepository) {

		this(noteRepository, tagRepository, Clock.systemUTC());
	}

	PatronRequestNoteRetention(PatronRequestNoteRepository noteRepository,
		PatronRequestTagRepository tagRepository, Clock clock) {

		this.noteRepository = noteRepository;
		this.tagRepository = tagRepository;
		this.clock = clock;
	}

	@AppTask
	@Scheduled(initialDelay = "20m", fixedDelay = "24h")
	public void scheduledSweep() {
		sweep()
			.doOnSuccess(removed -> log.info("Patron request note retention: removed {} note(s) and {} tag(s)",
				removed.getT1(), removed.getT2()))
			.doOnError(error -> log.error("Patron request note retention failed", error))
			.subscribe();
	}

	/** @return how many notes, then tags, were removed */
	public Mono<Tuple2<Long, Long>> sweep() {
		final var endedBefore = clock.instant().minus(RETENTION);

		return Mono.zip(
			Mono.from(noteRepository.deleteOrphanedOrEndedBefore(TERMINAL_STATUSES, endedBefore)).defaultIfEmpty(0L),
			Mono.from(tagRepository.deleteOrphanedOrEndedBefore(TERMINAL_STATUSES, endedBefore)).defaultIfEmpty(0L));
	}
}
