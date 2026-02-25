package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.storage.LibraryGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
public class CreateLibraryGroupDataFetcher implements DataFetcher<CompletableFuture<LibraryGroup>> {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private LibraryGroupRepository libraryGroupRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateLibraryGroupDataFetcher(LibraryGroupRepository libraryGroupRepository, R2dbcOperations r2dbcOperations) {
		this.libraryGroupRepository = libraryGroupRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<LibraryGroup> get(DataFetchingEnvironment env) {
		Map input_map = env.getArgument("input");
		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		String reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString)
			.orElse("Creation of new group");
		// Reason and category aren't surfaced in the UI for this operation. But there is a case for allowing them to be set via API
		String changeCategory = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString)
			.orElse("Creation of new group");

		LibraryGroup input = LibraryGroup.builder()
			.id(input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null)
			.code(input_map.get("code").toString())
			.name(input_map.get("name").toString())
			.type(input_map.get("type").toString())
			.lastEditedBy(userString)
			.changeCategory(changeCategory)
			.reason(reason).build();

		log.debug("getCreateLibraryGroupDataFetcher {}/{}", input_map, input);

		if (input.getId() == null) {
			input.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Group:" + input.getCode()));
		} else {
			log.debug("update existing");
		}

		log.debug("save or update group {}", input);

		return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(libraryGroupRepository.saveOrUpdate(input))))
			.toFuture();
	}
}
