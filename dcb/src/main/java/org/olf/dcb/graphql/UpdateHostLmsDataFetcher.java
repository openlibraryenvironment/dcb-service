package org.olf.dcb.graphql;


import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.events.RulesetRelatedDataChangedEvent;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.graphql.validation.HostLmsConfigValidator;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.storage.HostLmsRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
@SuppressWarnings("unchecked")
public class UpdateHostLmsDataFetcher implements DataFetcher<CompletableFuture<UpdateHostLmsDataFetcher.UpdateHostLmsResult>> {

	private final HostLmsRepository hostLmsRepository;
	private final HostLmsService hostLmsService;
	private final R2dbcOperations r2dbcOperations;
	private final ApplicationEventPublisher<RulesetRelatedDataChangedEvent> eventPublisher;
	private final HostLmsConfigValidator configValidator;

	// Constants for Ingest Sources
	private static final String ING_SRC_FOLIO = "org.olf.dcb.core.interaction.folio.FolioOaiPmhIngestSource";
	private static final String ING_SRC_ALMA = "org.olf.dcb.core.interaction.alma.AlmaOaiPmhIngestSource";

	public UpdateHostLmsDataFetcher(HostLmsRepository hostLmsRepository,
																	HostLmsService hostLmsService,
																	R2dbcOperations r2dbcOperations,
																	ApplicationEventPublisher<RulesetRelatedDataChangedEvent> eventPublisher,
																	HostLmsConfigValidator configValidator) {
		this.hostLmsRepository = hostLmsRepository;
		this.hostLmsService = hostLmsService;
		this.r2dbcOperations = r2dbcOperations;
		this.eventPublisher = eventPublisher;
		this.configValidator = configValidator;
	}

	@Override
	public CompletableFuture<UpdateHostLmsResult> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		Collection<String> roles = env.getGraphQlContext().get("roles");

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");		log.debug("updateHostLmsDataFetcher {}", input_map);

		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("updateHostLmsDataFetcher: Access denied for user {}: user does not have the required role to update a Host LMS.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to update a Host LMS.");
		}


		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;
		if (id == null) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Host LMS ID is required for update.");
		}


		// Core fields
		Optional<String> name = Optional.ofNullable((String) input_map.get("name"));
		Optional<String> lmsClientClass = Optional.ofNullable((String) input_map.get("lmsClientClass"));
		Optional<String> ingestSourceClass = Optional.ofNullable((String) input_map.get("ingestSourceClass"));
		Optional<Map<String, Object>> clientConfig = Optional.ofNullable((Map<String, Object>) input_map.get("clientConfig"));
		Optional<String> suppressionRulesetName = Optional.ofNullable((String) input_map.get("suppressionRulesetName"));
		Optional<String> itemSuppressionRulesetName = Optional.ofNullable((String) input_map.get("itemSuppressionRulesetName"));

		// Audit specific fields
		Optional<String> reason = Optional.ofNullable((String) input_map.get("reason"));
		Optional<String> changeCategory = Optional.ofNullable((String) input_map.get("changeCategory"));


		// Container to hold warnings generated during the transaction
		List<String> warningsContainer = new ArrayList<>();

		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(hostLmsRepository.findById(id))
					.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND, "Host LMS not found with ID: " + id)))
					.flatMap(hostLms -> {

						// Here come the updates. Note that the class changing is very important as it might have implications
						name.ifPresent(hostLms::setName);
						suppressionRulesetName.ifPresent(hostLms::setSuppressionRulesetName);
						itemSuppressionRulesetName.ifPresent(hostLms::setItemSuppressionRulesetName);
						clientConfig.ifPresent(hostLms::setClientConfig);

						boolean clientClassChanged = lmsClientClass.isPresent() && !lmsClientClass.get().equals(hostLms.getLmsClientClass());

						lmsClientClass.ifPresent(hostLms::setLmsClientClass);

						// If ingest source is explicitly provided, use it.
						// Otherwise, if client class changed, try to auto-detect.
						if (ingestSourceClass.isPresent()) {
							hostLms.setIngestSourceClass(ingestSourceClass.get());
						} else if (clientClassChanged) {
							String cls = hostLms.getLmsClientClass().toLowerCase();
							if (cls.contains("folio")) {
								hostLms.setIngestSourceClass(ING_SRC_FOLIO);
							} else if (cls.contains("alma")) {
								hostLms.setIngestSourceClass(ING_SRC_ALMA);
							}
						}

						hostLms.setLastEditedBy(userString);
						reason.ifPresent(hostLms::setReason);
						changeCategory.ifPresent(hostLms::setChangeCategory);

						// VALIDATION
						// We must validate the final state of the object (or the new config if replaced)
						// Note: This assumes clientConfig is replaced entirely if provided.
						if (hostLms.getClientConfig() != null) {
							configValidator.validate(hostLms.getLmsClientClass(), hostLms.getClientConfig());
							// Collect warnings
							warningsContainer.addAll(
								configValidator.findConfigurationWarnings(hostLms.getLmsClientClass(), hostLms.getClientConfig())
							);
						}

						return Mono.from(hostLmsRepository.update(hostLms));
					})
			))
			.doOnSuccess(saved -> {
				log.info("Host LMS updated: {}", saved.getCode());
				eventPublisher.publishEvent(new RulesetRelatedDataChangedEvent(saved));
			})
			.flatMap(saved -> performVerification(saved, warningsContainer))
			.toFuture();
	}

	private Mono<UpdateHostLmsResult> performVerification(DataHostLms hostLms, List<String> initialWarnings) {
		UpdateHostLmsResult.UpdateHostLmsResultBuilder resultBuilder = UpdateHostLmsResult.builder()
			.hostLms(hostLms)
			.warnings(new ArrayList<>(initialWarnings));

		// Ping once
		Mono<String> pingCheck = hostLmsService.getClientFor(hostLms)
			.flatMap(HostLmsClient::ping)
			.map(pingResponse -> "Status: " + pingResponse.getStatus() +
				(pingResponse.getAdditional() != null ? " - " + pingResponse.getAdditional() : ""))
			.onErrorResume(e -> Mono.just("Ping Failed: " + e.getMessage()));

		// Ingest once
		Mono<String> ingestCheck = hostLmsService.getIngestSourceFor(hostLms)
			.flatMap(source -> {
				if (source instanceof SourceRecordDataSource srds) {
					return Mono.from(srds.getChunk(Optional.empty()))
						.map(chunk -> "Success: Retrieved chunk with " + chunk.getSize() + " records.")
						.timeout(Duration.ofSeconds(20));
				} else {
					return Mono.just("Skipped: Source does not support chunked retrieval.");
				}
			})
			.onErrorResume(e -> {
				log.error("Ingest check failed for {}", hostLms.getCode(), e);
				return Mono.just("Ingest Check Failed: " + e.getMessage());
			});

		return Mono.zip(pingCheck, ingestCheck)
			.map(tuple -> resultBuilder
				.pingStatus(tuple.getT1())
				.ingestStatus(tuple.getT2())
				.build());
	}

	@Data
	@Builder
	@Serdeable
	@Introspected
	public static class UpdateHostLmsResult {
		DataHostLms hostLms;
		String pingStatus;
		String ingestStatus;
		List<String> warnings;
	}
}
