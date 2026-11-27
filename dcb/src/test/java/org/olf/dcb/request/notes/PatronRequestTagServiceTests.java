package org.olf.dcb.request.notes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

	@ParameterizedTest
	@ValueSource(strings = { "default", "primary", "secondary", "info", "success", "warning", "error" })
	void aTagIsCreatedWithAnyPaletteColour(String colour) {
		when(vocabulary.count()).thenReturn(Mono.just(0L));
		when(vocabulary.findById("COLOURED")).thenReturn(Mono.empty());
		// doAnswer, because the mock outlives one invocation here and when() would call the last stub
		doAnswer(invocation -> Mono.just(invocation.getArgument(0))).when(vocabulary).save(any());

		final var created = service.createTag("COLOURED", "Coloured", null, colour).block();

		assertThat(created.getColour(), is(colour));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "Error", "ERROR", " error", "red", "#d32f2f", "rgb(211, 47, 47)", "inherit" })
	void aColourThatIsNotAPaletteNameIsRefusedOnCreateAndUpdate(String colour) {
		final var created = assertThrows(HttpStatusException.class,
			() -> service.createTag("COLOURED", "Coloured", null, colour));
		final var updated = assertThrows(HttpStatusException.class,
			() -> service.updateTag("COLOURED", "Coloured", null, true, colour, false, null));

		for (var error : List.of(created, updated)) {
			assertThat(error.getStatus(), is(HttpStatus.BAD_REQUEST));
			assertThat(error.getMessage(), is("A tag colour must be one of: default, primary, secondary, "
				+ "info, success, warning, error; or null for the app's default."));
		}
	}

	private static RequestTag tag(String code) {
		return RequestTag.builder().code(code).label(code).active(true).build();
	}
}
