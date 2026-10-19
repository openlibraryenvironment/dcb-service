package org.olf.dcb.request.notes;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Objects;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Names each note's library with one lookup for all the notes given, not one per note. */
@Singleton
public class NoteLibraryNames {
	private final AgencyRepository agencyRepository;

	public NoteLibraryNames(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}

	public Mono<List<PatronRequestNote>> fill(List<PatronRequestNote> notes) {
		final var codes = notes.stream()
			.map(PatronRequestNote::getAgencyCode)
			.filter(Objects::nonNull)
			.collect(toUnmodifiableSet());

		if (codes.isEmpty()) {
			return Mono.just(notes);
		}

		return Flux.from(agencyRepository.findByCodeIn(codes))
			.collectMap(DataAgency::getCode, DataAgency::getName)
			.map(names -> {
				notes.forEach(note -> note.setLibraryName(
					note.getAgencyCode() == null ? null : names.get(note.getAgencyCode())));

				return notes;
			});
	}

	public Mono<PatronRequestNote> fill(PatronRequestNote note) {
		return fill(List.of(note)).thenReturn(note);
	}
}
