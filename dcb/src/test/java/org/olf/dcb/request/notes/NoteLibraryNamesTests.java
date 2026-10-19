package org.olf.dcb.request.notes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.RequestNoteAuthorKind.CONSORTIUM;
import static org.olf.dcb.core.model.RequestNoteAuthorKind.LIBRARY;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.RequestNoteAuthorKind;
import org.olf.dcb.storage.AgencyRepository;

import reactor.core.publisher.Flux;

class NoteLibraryNamesTests {
	@Test
	@SuppressWarnings("unchecked")
	void aPageOfNotesIsNamedWithOneLookup() {
		final var agencies = mock(AgencyRepository.class);
		when(agencies.findByCodeIn(any())).thenReturn(Flux.just(
			DataAgency.builder().id(UUID.randomUUID()).code("LIB_A").name("Library A").build(),
			DataAgency.builder().id(UUID.randomUUID()).code("LIB_B").name("Library B").build()));

		final var notes = List.of(note(LIBRARY, "LIB_A"), note(LIBRARY, "LIB_B"), note(LIBRARY, "LIB_A"),
			note(CONSORTIUM, null));

		new NoteLibraryNames(agencies).fill(notes).block();

		assertThat(notes.stream().map(PatronRequestNote::getLibraryName).toList(),
			contains("Library A", "Library B", "Library A", null));

		final ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.forClass(Collection.class);
		verify(agencies, times(1)).findByCodeIn(codes.capture());
		assertThat(codes.getValue(), containsInAnyOrder("LIB_A", "LIB_B"));
		verify(agencies, never()).findOneByCode(anyString());
	}

	@Test
	void consortiumNotesNeedNoLookup() {
		final var agencies = mock(AgencyRepository.class);
		final var note = note(CONSORTIUM, null);

		new NoteLibraryNames(agencies).fill(note).block();

		assertThat(note.getLibraryName(), is(nullValue()));
		verify(agencies, never()).findByCodeIn(any());
	}

	private static PatronRequestNote note(RequestNoteAuthorKind kind, String agencyCode) {
		return PatronRequestNote.builder()
			.id(UUID.randomUUID())
			.patronRequestId(UUID.randomUUID())
			.authorKind(kind)
			.agencyCode(agencyCode)
			.build();
	}
}
