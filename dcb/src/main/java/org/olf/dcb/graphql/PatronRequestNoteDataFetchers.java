package org.olf.dcb.graphql;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestNote;
import org.olf.dcb.core.model.RequestTag;
import org.olf.dcb.request.notes.NoteLibraryNames;
import org.olf.dcb.request.notes.PatronRequestNoteService;
import org.olf.dcb.request.notes.PatronRequestTagService;
import org.olf.dcb.request.notes.PatronRequestTagSummary;
import org.olf.dcb.request.notes.RequestNoteAccess;
import org.olf.dcb.request.notes.RequestNoteCaller;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Notes and tags on patron requests. Writing needs an administrative role (read-only staff read
 * only); the nested readers on PatronRequest check involvement themselves, because a request can
 * be reached through nested resolvers that are not scoped.
 */
@Singleton
public class PatronRequestNoteDataFetchers {
	static final String TAGS_LOADER = "PatronRequest.tags";

	/**
	 * Caps the request ids in one tags statement, because patronRequests does not cap its page size.
	 * A page of up to this many requests costs one batch.
	 */
	static final int TAGS_BATCH_SIZE = 500;

	private final PatronRequestNoteService noteService;
	private final PatronRequestTagService tagService;
	private final RequestNoteAccess access;
	private final NoteLibraryNames libraryNames;

	public PatronRequestNoteDataFetchers(PatronRequestNoteService noteService,
		PatronRequestTagService tagService, RequestNoteAccess access, NoteLibraryNames libraryNames) {

		this.noteService = noteService;
		this.tagService = tagService;
		this.access = access;
		this.libraryNames = libraryNames;
	}

	public DataFetcher<CompletableFuture<PatronRequestNote>> addPatronRequestNote() {
		return env -> {
			GraphQLRoles.require(env, "addPatronRequestNote", GraphQLRoles.ADMINISTRATIVE);

			return noteService.add(requiredUuid(env, "patronRequestId"), env.getArgument("body"),
				callerOf(env), env.getArgument("agencyCode"))
				.flatMap(libraryNames::fill)
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<PatronRequestNote>> retractPatronRequestNote() {
		return env -> {
			GraphQLRoles.require(env, "retractPatronRequestNote", GraphQLRoles.ADMINISTRATIVE);

			return noteService.retract(requiredUuid(env, "id"), callerOf(env))
				.flatMap(libraryNames::fill)
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<PatronRequestTagSummary>>> tagPatronRequest() {
		return env -> {
			GraphQLRoles.require(env, "tagPatronRequest", GraphQLRoles.ADMINISTRATIVE);

			return tagService.tag(requiredUuid(env, "patronRequestId"), env.getArgument("tagCode"),
				callerOf(env), env.getArgument("agencyCode")).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<PatronRequestTagSummary>>> untagPatronRequest() {
		return env -> {
			GraphQLRoles.require(env, "untagPatronRequest", GraphQLRoles.ADMINISTRATIVE);

			return tagService.untag(requiredUuid(env, "patronRequestId"), env.getArgument("tagCode"),
				callerOf(env), env.getArgument("agencyCode")).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<RequestTag>>> requestTags() {
		return env -> {
			GraphQLRoles.require(env, "requestTags", GraphQLRoles.STAFF);

			return tagService.vocabulary().collectList().toFuture();
		};
	}

	public DataFetcher<CompletableFuture<RequestTag>> createRequestTag() {
		return env -> {
			GraphQLRoles.require(env, "createRequestTag", GraphQLRoles.CONSORTIUM);

			final Map<String, Object> input = env.getArgument("input");

			return tagService.createTag((String) input.get("code"), (String) input.get("label"),
				(String) input.get("description")).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<RequestTag>> updateRequestTag() {
		return env -> {
			GraphQLRoles.require(env, "updateRequestTag", GraphQLRoles.CONSORTIUM);

			final Map<String, Object> input = env.getArgument("input");

			// Present-but-null is not the same as omitted: an omitted description is kept
			return tagService.updateTag((String) input.get("code"), (String) input.get("label"),
				(String) input.get("description"), !input.containsKey("description"),
				(Boolean) input.get("active")).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Map<String, Object>>> deleteRequestTag() {
		return env -> {
			GraphQLRoles.require(env, "deleteRequestTag", GraphQLRoles.CONSORTIUM);

			return tagService.deleteTag(env.getArgument("code"))
				.<Map<String, Object>>map(deleted -> Map.of("success", true,
					"message", "Tag " + deleted.getCode() + " deleted."))
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Page<PatronRequestNote>>> notesOfPatronRequest() {
		return env -> {
			GraphQLRoles.require(env, "PatronRequest.notes", GraphQLRoles.STAFF);

			final PatronRequest request = env.getSource();
			final Integer pageno = env.getArgument("pageno");
			final Integer pagesize = env.getArgument("pagesize");

			return access.canRead(request.getId(), callerOf(env))
				.flatMap(allowed -> allowed
					? noteService.list(request.getId(), pageno, pagesize)
						.flatMap(page -> libraryNames.fill(page.getContent()).thenReturn(page))
					: Mono.just(Page.<PatronRequestNote>of(List.of(), Pageable.from(0, PatronRequestNoteService.MAX_PAGE_SIZE), 0L)))
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<PatronRequestTagSummary>>> tagsOfPatronRequest() {
		return env -> {
			GraphQLRoles.require(env, "PatronRequest.tags", GraphQLRoles.STAFF);

			final PatronRequest request = env.getSource();

			return tagsLoader(env).load(request.getId());
		};
	}

	// The registry belongs to one execution, so the caller captured here is the caller of every load
	private DataLoader<UUID, List<PatronRequestTagSummary>> tagsLoader(DataFetchingEnvironment env) {
		final var caller = callerOf(env);

		return env.getDataLoaderRegistry().computeIfAbsent(TAGS_LOADER, name ->
			DataLoaderFactory.<UUID, List<PatronRequestTagSummary>>newMappedDataLoader(
				ids -> tagsReadableBy(ids, caller).toFuture(),
				DataLoaderOptions.newOptions().setMaxBatchSize(TAGS_BATCH_SIZE).build()));
	}

	private Mono<Map<UUID, List<PatronRequestTagSummary>>> tagsReadableBy(Set<UUID> ids,
		RequestNoteCaller caller) {

		return access.readableAmong(ids, caller)
			.flatMap(tagService::tagsOfEach)
			.map(tags -> ids.stream().collect(Collectors.toMap(Function.identity(),
				id -> tags.getOrDefault(id, List.of()))));
	}

	public DataFetcher<CompletableFuture<List<String>>> involvedAgencyCodesOfPatronRequest() {
		return env -> {
			GraphQLRoles.require(env, "PatronRequest.involvedAgencyCodes", GraphQLRoles.STAFF);

			final PatronRequest request = env.getSource();
			final var caller = callerOf(env);

			return access.involvedAgencyCodes(request.getId())
				.map(involved -> caller.consortium() || caller.agencyCodes().stream().anyMatch(involved::contains)
					? List.copyOf(involved)
					: List.<String>of())
				.toFuture();
		};
	}

	private static RequestNoteCaller callerOf(DataFetchingEnvironment env) {
		return new RequestNoteCaller(AgencyAccessScope.isUnrestricted(env),
			AgencyAccessScope.visibleAgencyCodes(env));
	}

	private static UUID requiredUuid(DataFetchingEnvironment env, String name) {
		final Object value = env.getArgument(name);

		try {
			return UUID.fromString(String.valueOf(value));
		}
		catch (IllegalArgumentException e) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, name + " must be a UUID.");
		}
	}
}
