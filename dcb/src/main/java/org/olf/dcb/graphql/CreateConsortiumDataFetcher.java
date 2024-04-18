package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.api.exceptions.ConsortiumCreationException;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.LibraryGroupRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class CreateConsortiumDataFetcher implements DataFetcher<CompletableFuture<Consortium>> {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private ConsortiumRepository consortiumRepository;

	private LibraryGroupRepository libraryGroupRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateConsortiumDataFetcher(ConsortiumRepository consortiumRepostory, LibraryGroupRepository libraryGroupRepository, R2dbcOperations r2dbcOperations) {
		this.consortiumRepository = consortiumRepostory;
		this.libraryGroupRepository = libraryGroupRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<Consortium> get(DataFetchingEnvironment env) {
		Map input_map = env.getArgument("input");

		// Pre-requisite: There must already be a LibraryGroup that we want to associate to this Consortium
		// It will have a one-to-one relationship (as long as the type is "consortium" - otherwise no relationship)
		// And we must supply the name when we create the Consortium.

		log.debug("createConsortiumDataFetcher {}", input_map);

		return Mono.from(libraryGroupRepository.findOneByNameAndTypeIgnoreCase(input_map.get("groupName").toString(), "Consortium"))
			.flatMap(libraryGroup -> {
				if (libraryGroup != null) {
					// If a library group matching the conditions is found, we can create the associated consortium
					Consortium consortium = Consortium.builder()
						.id(UUID.randomUUID())
						.name(input_map.get("name").toString())
						.libraryGroup(libraryGroup).build();
						return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(consortiumRepository.saveOrUpdate(consortium))));
				} else {
					// If not, we cannot create a consortium.
					return Mono.error(new ConsortiumCreationException("Consortium creation has failed because a compatible library group was not found. You must supply the name of an existing LibraryGroup of type consortium."));
				}
				}
			).toFuture();
	}
}

