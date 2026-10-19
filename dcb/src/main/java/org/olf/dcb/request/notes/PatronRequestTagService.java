package org.olf.dcb.request.notes;

import static org.olf.dcb.core.model.RequestNoteAuthorKind.CONSORTIUM;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.olf.dcb.core.model.PatronRequestTag;
import org.olf.dcb.core.model.RequestTag;
import org.olf.dcb.storage.PatronRequestTagRepository;
import org.olf.dcb.storage.RequestTagRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tags come from a vocabulary the consortium controls rather than free text, so they can be
 * filtered on and cannot carry personal data. Each author applies a tag in its own name.
 */
@Slf4j
@Singleton
public class PatronRequestTagService {
	public static final int MAX_VOCABULARY_SIZE = 100;

	private static final Pattern TAG_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");

	private final PatronRequestTagRepository tagRepository;
	private final RequestTagRepository vocabulary;
	private final RequestNoteAccess access;

	public PatronRequestTagService(PatronRequestTagRepository tagRepository,
		RequestTagRepository vocabulary, RequestNoteAccess access) {

		this.tagRepository = tagRepository;
		this.vocabulary = vocabulary;
		this.access = access;
	}

	public Mono<List<PatronRequestTagSummary>> tagsOf(UUID patronRequestId) {
		return Mono.zip(
				Flux.from(tagRepository.findByPatronRequestId(patronRequestId)).collectList(),
				Flux.from(vocabulary.listOrderByLabel()).collectMap(RequestTag::getCode, Function.identity()))
			.map(applied -> summarise(applied.getT1(), applied.getT2()));
	}

	public Mono<List<PatronRequestTagSummary>> tag(UUID patronRequestId, String tagCode,
		RequestNoteCaller caller, @Nullable String requestedAgencyCode) {

		return Mono.from(vocabulary.findById(tagCode))
			.filter(RequestTag::getActive)
			.switchIfEmpty(Mono.error(() -> new HttpStatusException(HttpStatus.BAD_REQUEST,
				"No such tag: " + tagCode + ".")))
			.flatMap(tag -> access.authorFor(patronRequestId, caller, requestedAgencyCode))
			.flatMap(author -> applied(patronRequestId, tagCode, author)
				.flatMap(alreadyApplied -> alreadyApplied
					? Mono.just(author)
					: Mono.from(tagRepository.save(PatronRequestTag.builder()
							.id(UUID.randomUUID())
							.patronRequestId(patronRequestId)
							.tagCode(tagCode)
							.authorKind(author.kind())
							.agencyCode(author.agencyCode())
							.dateCreated(Instant.now())
							.build()))
						.doOnNext(saved -> log.info("Tag {} applied to patron request {} by {}",
							tagCode, patronRequestId, author))
						.thenReturn(author)
						// A concurrent apply by the same author lost the race to the unique index
						.onErrorResume(error -> applied(patronRequestId, tagCode, author)
							.flatMap(nowApplied -> nowApplied ? Mono.just(author) : Mono.error(error)))))
			.then(tagsOf(patronRequestId));
	}

	public Mono<List<PatronRequestTagSummary>> untag(UUID patronRequestId, String tagCode,
		RequestNoteCaller caller, @Nullable String requestedAgencyCode) {

		return access.authorFor(patronRequestId, caller, requestedAgencyCode)
			.flatMap(author -> Mono.from(author.kind() == CONSORTIUM
					? tagRepository.deleteByPatronRequestIdAndTagCodeAndAuthorKind(patronRequestId, tagCode, CONSORTIUM)
					: tagRepository.deleteByPatronRequestIdAndTagCodeAndAgencyCode(patronRequestId, tagCode, author.agencyCode()))
				.doOnNext(deleted -> log.info("Tag {} removed from patron request {} by {} ({} rows)",
					tagCode, patronRequestId, author, deleted)))
			.then(tagsOf(patronRequestId));
	}

	public Flux<RequestTag> vocabulary() {
		return Flux.from(vocabulary.listOrderByLabel());
	}

	public Mono<RequestTag> createTag(@Nullable String code, @Nullable String label,
		@Nullable String description) {

		final var tag = validated(code, label, description, true);

		return Mono.from(vocabulary.count())
			.flatMap(count -> count >= MAX_VOCABULARY_SIZE
				? Mono.error(new HttpStatusException(HttpStatus.CONFLICT,
					"The tag vocabulary already has the maximum of " + MAX_VOCABULARY_SIZE + " tags."))
				: Mono.from(vocabulary.findById(tag.getCode())))
			.flatMap(existing -> Mono.<RequestTag>error(new HttpStatusException(HttpStatus.CONFLICT,
				"A tag with code " + tag.getCode() + " already exists.")))
			.switchIfEmpty(Mono.defer(() -> Mono.from(vocabulary.save(tag))));
	}

	/** What the input leaves out keeps its stored value, so a retired tag stays retired. */
	public Mono<RequestTag> updateTag(@Nullable String code, @Nullable String label,
		@Nullable String description, boolean keepDescription, @Nullable Boolean active) {

		final var requested = validated(code, label, description, true);

		return Mono.from(vocabulary.findById(requested.getCode()))
			.switchIfEmpty(Mono.error(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
				"No such tag: " + requested.getCode() + ".")))
			.flatMap(existing -> Mono.from(vocabulary.update(requested
				.setActive(active != null ? active : existing.getActive())
				.setDescription(keepDescription ? existing.getDescription() : requested.getDescription()))));
	}

	/** A tag that a request carries is retired, not deleted, so no request loses one. */
	public Mono<RequestTag> deleteTag(@Nullable String code) {
		return Mono.justOrEmpty(code)
			.filter(candidate -> TAG_CODE.matcher(candidate).matches())
			.flatMap(candidate -> Mono.from(vocabulary.findById(candidate)))
			.switchIfEmpty(Mono.error(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
				"No such tag: " + code + ".")))
			.flatMap(tag -> Mono.from(vocabulary.deleteUnused(tag.getCode()))
				// A tag applied after the NOT EXISTS check makes the foreign key refuse the delete instead
				.onErrorResume(error -> Mono.from(tagRepository.existsByTagCode(tag.getCode()))
					.flatMap(inUse -> inUse ? Mono.just(0L) : Mono.error(error)))
				.flatMap(deleted -> deleted == 0
					? Mono.<RequestTag>error(new HttpStatusException(HttpStatus.CONFLICT,
						"Tag " + tag.getCode() + " is on at least one request, so it cannot be deleted; retire it instead."))
					: Mono.just(tag)))
			.doOnNext(deleted -> log.info("Tag {} deleted from the vocabulary", deleted.getCode()));
	}

	private Mono<Boolean> applied(UUID patronRequestId, String tagCode, RequestNoteAuthor author) {
		return Mono.from(author.kind() == CONSORTIUM
			? tagRepository.existsByPatronRequestIdAndTagCodeAndAuthorKind(patronRequestId, tagCode, CONSORTIUM)
			: tagRepository.existsByPatronRequestIdAndTagCodeAndAgencyCode(patronRequestId, tagCode, author.agencyCode()));
	}

	private static RequestTag validated(@Nullable String code, @Nullable String label,
		@Nullable String description, boolean active) {

		if (code == null || !TAG_CODE.matcher(code).matches()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST,
				"A tag code is 2 to 32 capital letters, digits or underscores, starting with a letter.");
		}

		final var trimmedLabel = label == null ? "" : label.strip();

		if (trimmedLabel.isEmpty() || trimmedLabel.length() > 64) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "A tag label must have between 1 and 64 characters.");
		}

		final var trimmedDescription = description == null || description.isBlank() ? null : description.strip();

		if (trimmedDescription != null && trimmedDescription.length() > 200) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "A tag description must have at most 200 characters.");
		}

		return RequestTag.builder()
			.code(code)
			.label(trimmedLabel)
			.description(trimmedDescription)
			.active(active)
			.build();
	}

	private static List<PatronRequestTagSummary> summarise(List<PatronRequestTag> applied,
		Map<String, RequestTag> tags) {

		return applied.stream()
			.collect(Collectors.groupingBy(PatronRequestTag::getTagCode))
			.entrySet().stream()
			.map(entry -> new PatronRequestTagSummary(
				entry.getKey(),
				tags.containsKey(entry.getKey()) ? tags.get(entry.getKey()).getLabel() : entry.getKey(),
				entry.getValue().stream()
					.map(PatronRequestTag::getAgencyCode)
					.filter(Objects::nonNull)
					.sorted()
					.toList(),
				entry.getValue().stream().anyMatch(tag -> tag.getAuthorKind() == CONSORTIUM)))
			.sorted(Comparator.comparing(PatronRequestTagSummary::label))
			.toList();
	}
}
