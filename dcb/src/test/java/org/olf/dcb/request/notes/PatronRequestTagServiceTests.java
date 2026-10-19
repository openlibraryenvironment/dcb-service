package org.olf.dcb.request.notes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.RequestTag;
import org.olf.dcb.storage.PatronRequestTagRepository;
import org.olf.dcb.storage.RequestTagRepository;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import reactor.core.publisher.Mono;

class PatronRequestTagServiceTests {
	private final PatronRequestTagRepository appliedTags = mock(PatronRequestTagRepository.class);
	private final RequestTagRepository vocabulary = mock(RequestTagRepository.class);
	private final PatronRequestTagService service =
		new PatronRequestTagService(appliedTags, vocabulary, mock(RequestNoteAccess.class));

	@Test
	void aTagAppliedWhileItIsDeletedIsRefusedAsInUse() {
		final var refusedByForeignKey = new IllegalStateException("violates foreign key constraint");
		when(vocabulary.findById("RACED")).thenReturn(Mono.just(tag("RACED")));
		when(vocabulary.deleteUnused("RACED")).thenReturn(Mono.error(refusedByForeignKey));
		when(appliedTags.existsByTagCode("RACED")).thenReturn(Mono.just(true));

		final var error = assertThrows(HttpStatusException.class, () -> service.deleteTag("RACED").block());

		assertThat(error.getStatus(), is(HttpStatus.CONFLICT));
	}

	@Test
	void aDeleteThatFailsForAnotherReasonIsNotReportedAsInUse() {
		final var unrelated = new IllegalStateException("connection lost");
		when(vocabulary.findById("BROKEN")).thenReturn(Mono.just(tag("BROKEN")));
		when(vocabulary.deleteUnused("BROKEN")).thenReturn(Mono.error(unrelated));
		when(appliedTags.existsByTagCode("BROKEN")).thenReturn(Mono.just(false));

		final var error = assertThrows(IllegalStateException.class, () -> service.deleteTag("BROKEN").block());

		assertThat(error, is(sameInstance(unrelated)));
	}

	@Test
	void aCodeThatCannotBeATagIsNotFound() {
		final var error = assertThrows(HttpStatusException.class, () -> service.deleteTag("not a code").block());

		assertThat(error.getStatus(), is(HttpStatus.NOT_FOUND));
	}

	private static RequestTag tag(String code) {
		return RequestTag.builder().code(code).label(code).active(true).build();
	}
}
