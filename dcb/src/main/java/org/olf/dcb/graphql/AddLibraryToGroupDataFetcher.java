package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
public class AddLibraryToGroupDataFetcher implements DataFetcher<CompletableFuture<LibraryGroupMember>> {

	private static Logger log = LoggerFactory.getLogger(AddLibraryToGroupDataFetcher.class);

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

		Map input_map = env.getArgument("input");

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

		LibraryGroupMember gm = LibraryGroupMember.builder().build();
		// Note: here is where we could set 'memberFrom'.
		// 'MemberTo' is more tricky and can't be done meaningfully w/o ability to leave a LibraryGroup.

		log.debug("get...");
		// For future reference: when we expose this to end users in DCB Admin we should introduce the ability to do this
		// by providing a name or code, and then use that for lookup.


		return Mono.from(r2dbcOperations.withTransaction(status -> {
			log.debug("input_map is"+input_map);
			String library_uuid_as_string = input_map.get("library").toString();
			String group_uuid_as_string = input_map.get("libraryGroup").toString();
			log.debug("UUIDs are "+library_uuid_as_string+"and"+group_uuid_as_string);
			UUID library_uuid = UUID.fromString(library_uuid_as_string);
			UUID group_uuid = UUID.fromString(group_uuid_as_string);

			Mono<LibraryGroup> group_mono = Mono.from(libraryGroupRepository.findById(group_uuid));
			Mono<Library> library_mono = Mono.from(libraryRepository.findById(library_uuid));

			log.debug("add library to group library={} group={}", library_uuid, group_uuid);

			return Mono.just(gm).flatMap(gm2 -> group_mono.map(group -> gm2.setLibraryGroup(group)))
				.flatMap(gm2 -> library_mono.map(library -> gm2.setLibrary(library))).map(gm2 -> {
					UUID record_uuid = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
						"LGM:" + library_uuid_as_string + ":" + group_uuid_as_string);
					log.debug("Set uuid for member {} {}", gm2, record_uuid);
					return gm2.setId(record_uuid);
				}).flatMap(gm2 -> Mono.from(libraryGroupMemberRepository.saveOrUpdate(gm2)));
		})).toFuture();
	}

}
