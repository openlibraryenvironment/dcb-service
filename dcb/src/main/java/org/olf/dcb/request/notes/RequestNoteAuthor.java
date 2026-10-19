package org.olf.dcb.request.notes;

import static org.olf.dcb.core.model.RequestNoteAuthorKind.CONSORTIUM;
import static org.olf.dcb.core.model.RequestNoteAuthorKind.LIBRARY;

import org.olf.dcb.core.model.RequestNoteAuthorKind;

import io.micronaut.core.annotation.Nullable;

/** The library, or the consortium, a note or tag speaks for. */
public record RequestNoteAuthor(RequestNoteAuthorKind kind, @Nullable String agencyCode) {
	public static RequestNoteAuthor consortium() {
		return new RequestNoteAuthor(CONSORTIUM, null);
	}

	public static RequestNoteAuthor library(String agencyCode) {
		return new RequestNoteAuthor(LIBRARY, agencyCode);
	}

	@Override
	public String toString() {
		return kind == CONSORTIUM ? "the consortium" : "library " + agencyCode;
	}
}
