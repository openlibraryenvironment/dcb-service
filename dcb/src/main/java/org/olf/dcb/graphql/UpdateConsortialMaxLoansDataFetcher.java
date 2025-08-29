package org.olf.dcb.graphql;

import com.hazelcast.internal.ascii.rest.HttpBadRequestException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


@Singleton
@Slf4j
public class UpdateConsortialMaxLoansDataFetcher implements DataFetcher<CompletableFuture<DataAgency>> {


		private final AgencyRepository agencyRepository;
		private final R2dbcOperations r2dbcOperations;

		public UpdateConsortialMaxLoansDataFetcher(AgencyRepository agencyRepository, R2dbcOperations r2dbcOperations) {
			this.agencyRepository = agencyRepository;
			this.r2dbcOperations = r2dbcOperations;
		}


		// Updates the consortial max loans value. This exists on the agency
		@Override
		public CompletableFuture<DataAgency> get(DataFetchingEnvironment env) {
			Map<String, Object> input_map = env.getArgument("input");
			log.debug("updateConsortialMaxLoansDataFetcher {}", input_map);

			// Role check. Only consortium administrators can change this.
			Collection<String> roles = env.getGraphQlContext().get("roles");
			String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
				.map(Object::toString)
				.orElse("User not detected");

			if (roles == null || (!roles.contains("CONSORTIUM_ADMIN"))) {
				log.warn("createRoleDataFetcher: Access denied for user {}: user does not have the required role to update consortial max loans.", userString);
				throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to update consortial max loans.");
			}

			// Get the agency code and the value
			String code = input_map.get("code").toString();
			Object maxLoansInput = input_map.get("maxLoans");

			if (maxLoansInput == null ) {
				throw new HttpBadRequestException("You must provide an integer value for max loans");
			}
			final int maxLoans;
			try {
				// Parse int: if that fails, format is invalid and we send a 400 back
				maxLoans = Integer.parseInt(maxLoansInput.toString());
			} catch (NumberFormatException e) {
				throw new HttpStatusException(HttpStatus.BAD_REQUEST, "The provided maxLoans value must be a valid integer.");
			}


			// Get data change log values
			Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
				.map(Object::toString);
			Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
				.map(Object::toString);
			Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
				.map(Object::toString);

			Mono<DataAgency> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(agencyRepository.findOneByCode(code))
					.flatMap(agency -> {
						reason.ifPresent(agency::setReason);
						changeCategory.ifPresent(agency::setChangeCategory);
						changeReferenceUrl.ifPresent(agency::setChangeReferenceUrl);
						agency.setMaxConsortialLoans(maxLoans);
						return Mono.from(agencyRepository.update(agency));
					})
			));

			return transactionMono.toFuture();
		}
	}
