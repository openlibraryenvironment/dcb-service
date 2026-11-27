package org.olf.dcb.request.notes;

import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.PatronRequestTag;

import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * The patron request query fields that read notes and tags. Each is {@code id IN (subquery)} on its
 * own table rather than a join, so a request tagged by several parties, or with several matching
 * notes, is returned once.
 * <p>
 * Not a correlated EXISTS: Micronaut Data 5.0 renders an outer path inside a subquery with the
 * subquery's alias, and {@code Subquery.correlate} throws "Not supported operation!". Postgres
 * plans this form as the same semi-join.
 * <p>
 * patron_request_id is NOT NULL in both tables, so negating these never meets NOT IN's NULL trap.
 */
public final class PatronRequestNoteQueries {
	public static final String TAG_CODE = "tagCode";
	public static final String NOTE_TEXT = "noteText";

	/**
	 * Note bodies have no index, so every note search reads every live note: bounded by requests,
	 * about 100,000 a year, times at most 200 notes each. The minimum stops a one-character search
	 * from matching nearly all of them.
	 */
	public static final int MIN_NOTE_TEXT_LENGTH = 3;

	private static final char LIKE_ESCAPE = '\\';

	private PatronRequestNoteQueries() {
	}

	/** The specification a query field names, or empty when the field is not one of these. */
	public static <T> Optional<QuerySpecification<T>> forField(String field, String value) {
		return switch (field) {
			case TAG_CODE -> Optional.of(taggedWith(value));
			case NOTE_TEXT -> Optional.of(withNoteContaining(value));
			default -> Optional.empty();
		};
	}

	/** Requests carrying this tag, whoever applied it. The subquery is an index-only scan of idx_pr_tag_code. */
	public static <T> QuerySpecification<T> taggedWith(String tagCode) {
		return (root, query, criteriaBuilder) -> {
			requirePatronRequest(root, TAG_CODE);

			if (tagCode == null || !PatronRequestTagService.TAG_CODE.matcher(tagCode).matches()) {
				throw new IllegalArgumentException(
					"Cannot filter on tagCode: \"%s\" is not a tag code.".formatted(tagCode));
			}

			final Subquery<UUID> tagged = query.subquery(UUID.class);
			final Root<PatronRequestTag> tag = tagged.from(PatronRequestTag.class);
			tagged.select(tag.get("patronRequestId"))
				.where(criteriaBuilder.equal(tag.get("tagCode"), tagCode));

			return root.get("id").in(tagged);
		};
	}

	/** Requests with a note that has not been retracted and contains this text, ignoring case. */
	public static <T> QuerySpecification<T> withNoteContaining(String text) {
		return (root, query, criteriaBuilder) -> {
			requirePatronRequest(root, NOTE_TEXT);

			final var wanted = text == null ? "" : text.strip();

			if (wanted.length() < MIN_NOTE_TEXT_LENGTH) {
				throw new IllegalArgumentException("Cannot filter on noteText: search for at least %d characters."
					.formatted(MIN_NOTE_TEXT_LENGTH));
			}

			final Subquery<UUID> noted = query.subquery(UUID.class);
			final Root<PatronRequestNote> note = noted.from(PatronRequestNote.class);
			noted.select(note.get("patronRequestId"))
				.where(
					criteriaBuilder.isNull(note.get("retractedAt")),
					criteriaBuilder.like(
						criteriaBuilder.lower(note.get("body")),
						criteriaBuilder.lower(criteriaBuilder.literal("%" + escapeLike(wanted) + "%")),
						LIKE_ESCAPE));

			return root.get("id").in(noted);
		};
	}

	static String escapeLike(String text) {
		final var escaped = new StringBuilder(text.length());

		for (char c : text.toCharArray()) {
			if (c == LIKE_ESCAPE || c == '%' || c == '_') {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(c);
		}

		return escaped.toString();
	}

	private static void requirePatronRequest(Root<?> root, String field) {
		if (!PatronRequest.class.equals(root.getJavaType())) {
			throw new IllegalArgumentException("Cannot filter on %s: it applies only to patron requests."
				.formatted(field));
		}
	}
}
