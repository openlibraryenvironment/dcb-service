package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.olf.dcb.storage.LibraryRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
@Slf4j
public class AddLibraryToGroupDataFetcher implements DataFetcher<CompletableFuture<LibraryGroupMember>> {

	private LibraryGroupMemberRepository libraryGroupMemberRepository;
	private LibraryGroupRepository libraryGroupRepository;
	private LibraryRepository libraryRepository;
	private R2dbcOperations r2dbcOperations;

	public AddLibraryToGroupDataFetcher(LibraryGroupMemberRepository libraryGroupMemberRepository,
																			LibraryRepository libraryRepository, LibraryGroupRepository libraryGroupRepository, R2dbcOperations r2dbcOperations) {
		this.libraryGroupMemberRepository = libraryGroupMemberRepository;
		this.libraryRepository = libraryRepository;
		this.libraryGroupRepository = libraryGroupRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<LibraryGroupMember> get(DataFetchingEnvironment env) {

		Map<String, Object> input_map = env.getArgument("input");

		log.debug("AddLibraryToGroupDataFetcher::get {}", input_map);


		// input consists of a "libraryGroup" and "library" property - each of type ID which
		// maps to a UUID string

		// https://github.com/micronaut-projects/micronaut-graphql/issues/210 says to
		// use
		// public CompletableFuture<SetResult<S>> get(final DataFetchingEnvironment
		// environment) throws Exception {
		// var pub = .... get your publisher here
		// return Mono.from(pub).toFuture();
		// }

		String reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString)
			.orElse("Adding library to group");
		String changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString)
			.orElse("New member");


		// Get the authenticated user
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		LibraryGroupMember gm = LibraryGroupMember.builder().build();
		// Set audit information
		gm.setLastEditedBy(userString);
		gm.setReason(reason);
		gm.setChangeCategory(changeCategory);
		// Note: here is where we could set 'memberFrom'.
		// 'MemberTo' is more tricky and can't be done meaningfully w/o ability to leave a LibraryGroup.

		return Mono.from(r2dbcOperations.withTransaction(status -> {
			log.debug("input_map is{}", input_map);
			String library_uuid_as_string = input_map.get("library").toString();
			String group_uuid_as_string = input_map.get("libraryGroup").toString();
			log.debug("UUIDs are {}and{}", library_uuid_as_string, group_uuid_as_string);
			UUID library_uuid = UUID.fromString(library_uuid_as_string);
			UUID group_uuid = UUID.fromString(group_uuid_as_string);

			Mono<LibraryGroup> group_mono = Mono.from(libraryGroupRepository.findById(group_uuid));
			Mono<Library> library_mono = Mono.from(libraryRepository.findById(library_uuid));

			log.debug("add library to group library={} group={}", library_uuid, group_uuid);

			return Mono.just(gm).flatMap(gm2 -> group_mono.map(gm2::setLibraryGroup))
				.flatMap(gm2 -> library_mono.map(gm2::setLibrary)).map(gm2 -> {
					UUID record_uuid = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
						"LGM:" + library_uuid_as_string + ":" + group_uuid_as_string);
					log.debug("Set uuid for member {} {}", gm2, record_uuid);
					return gm2.setId(record_uuid);
				}).flatMap(gm2 -> Mono.from(libraryGroupMemberRepository.saveOrUpdate(gm2)));
		})).toFuture();
	}

}
