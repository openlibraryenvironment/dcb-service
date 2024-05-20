package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class UpdateAgencyParticipationStatusDataFetcher implements DataFetcher<CompletableFuture<DataAgency>> {

	private final AgencyRepository agencyRepository;
	private final R2dbcOperations r2dbcOperations;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateAgencyParticipationStatusDataFetcher(AgencyRepository agencyRepository, R2dbcOperations r2dbcOperations) {
		this.agencyRepository = agencyRepository;
		this.r2dbcOperations = r2dbcOperations;
	}


	// Updates an agency's participation status depending on which is supplied
	@Override
	public CompletableFuture<DataAgency> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateAgencyParticipationStatusDataFetcher {}", input_map);

		String code = input_map.get("code").toString();
		Boolean isSupplyingAgency = input_map.containsKey("isSupplyingAgency") ?
			Boolean.parseBoolean(input_map.get("isSupplyingAgency").toString()) : null;
		Boolean isBorrowingAgency = input_map.containsKey("isBorrowingAgency") ?
			Boolean.parseBoolean(input_map.get("isBorrowingAgency").toString()) : null;

		Mono<DataAgency> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(agencyRepository.findOneByCode(code))
				.flatMap(agency -> {
					if (isSupplyingAgency != null) {
						agency.setIsSupplyingAgency(isSupplyingAgency);
					}
					if (isBorrowingAgency != null) {
						agency.setIsBorrowingAgency(isBorrowingAgency);
					}
					return Mono.from(agencyRepository.update(agency));
				})
		));

		return transactionMono.toFuture();
	}
}
