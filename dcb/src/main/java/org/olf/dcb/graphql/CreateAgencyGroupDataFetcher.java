package org.olf.dcb.graphql;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.AgencyGroup;
import org.olf.dcb.storage.AgencyGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Singleton
public class CreateAgencyGroupDataFetcher implements DataFetcher<CompletableFuture<AgencyGroup>> {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private AgencyGroupRepository agencyGroupRepository;
	private R2dbcOperations r2dbcOperations;

	public CreateAgencyGroupDataFetcher(AgencyGroupRepository agencyGroupRepository, R2dbcOperations r2dbcOperations) {
		this.agencyGroupRepository = agencyGroupRepository;
		this.r2dbcOperations = r2dbcOperations;
	}

	@Override
	public CompletableFuture<AgencyGroup> get(DataFetchingEnvironment env) {
		// String name = env.getArgument("name");
		// String code = env.getArgument("code");
		// AgencyGroup input = env.getArgument("input");
		Map input_map = env.getArgument("input");

		AgencyGroup input = AgencyGroup.builder()
				.id(input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null)
				.code(input_map.get("code").toString()).name(input_map.get("name").toString()).build();

		log.debug("getCreateAgencyGroupDataFetcher {}/{}", input_map, input);

		if (input.getId() == null) {
			input.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "AgencyGroup:" + input.getCode()));
		} else {
			log.debug("update existing");
		}

		log.debug("save or update agency {}", input);

		return Mono.from(r2dbcOperations.withTransaction(status -> Mono.from(agencyGroupRepository.saveOrUpdate(input))))
				.toFuture();
	}
}
