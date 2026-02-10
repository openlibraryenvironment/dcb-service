package org.olf.dcb.graphql;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.events.RulesetRelatedDataChangedEvent;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.graphql.validation.HostLmsConfigValidator;
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
import services.k_int.utils.UUIDUtils;

@Singleton
@Slf4j
@SuppressWarnings("unchecked")
public class CreateHostLmsDataFetcher implements DataFetcher<CompletableFuture<CreateHostLmsDataFetcher.CreateHostLmsResult>> {

	private final HostLmsRepository hostLmsRepository;
	private final HostLmsService hostLmsService;
	private final R2dbcOperations r2dbcOperations;
	private final ApplicationEventPublisher<RulesetRelatedDataChangedEvent> eventPublisher;
	private final HostLmsConfigValidator configValidator;

	// The ingest sources need constants
	private static final String INGEST_SOURCE_CLASS_FOLIO = "org.olf.dcb.core.interaction.folio.FolioOaiPmhIngestSource";
	private static final String INGEST_SOURCE_CLASS_ALMA = "org.olf.dcb.core.interaction.alma.AlmaOaiPmhIngestSource";

	public CreateHostLmsDataFetcher(HostLmsRepository hostLmsRepository,
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
	public CompletableFuture<CreateHostLmsResult> get(DataFetchingEnvironment env) {

		Map<String, Object> input = env.getArgument("input");
		Collection<String> roles = env.getGraphQlContext().get("roles");

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("CreateHostLmsDataFetcher: Access denied for user {}: user does not have the required role to create a host LMS.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to create a Host LMS.");
		}


		log.debug("CreateHostLmsDataFetcher input: {}", input);

		// Parse the input. Note that these are the common variables
		String code = (String) input.get("code");
		String name = (String) input.get("name");
		String lmsClientClass = (String) input.get("lmsClientClass");
		String ingestSourceClass = "";
		Map<String, Object> clientConfig = (Map<String, Object>) input.get("clientConfig");
		String suppressionRulesetName = (String) input.get("suppressionRulesetName");
		String itemSuppressionRulesetName = (String) input.get("itemSuppressionRulesetName");

		// Set the ingest source class
		if (lmsClientClass != null) {
			if (lmsClientClass.toLowerCase().contains("folio")) {
				ingestSourceClass = INGEST_SOURCE_CLASS_FOLIO;
			} else if (lmsClientClass.toLowerCase().contains("alma")) {
				ingestSourceClass = INGEST_SOURCE_CLASS_ALMA;
			}
		}

		configValidator.validate(lmsClientClass, clientConfig); // will throw 400 if bad
		List<String> configWarnings = configValidator.findConfigurationWarnings(lmsClientClass, clientConfig); // will not throw 400 if bad, but will give list of potentially missing things

		UUID id = UUIDUtils.generateHostLmsId(code);

		DataHostLms newHostLms = DataHostLms.builder()
			.id(id)
			.code(code)
			.name(name)
			.lmsClientClass(lmsClientClass)
			.ingestSourceClass(ingestSourceClass)
			.clientConfig(clientConfig) // Needs validating
			.suppressionRulesetName(suppressionRulesetName) // May also need validating
			.itemSuppressionRulesetName(itemSuppressionRulesetName)
			.reason("Adding a new Host LMS")
			.changeCategory("New Host LMS")
			.lastEditedBy(userString)
			.build();

		return Mono.from(r2dbcOperations.withTransaction(status ->
				Mono.from(hostLmsRepository.save(newHostLms))
			))
			.doOnSuccess(saved -> {
				log.info("Host LMS created: {}", saved.getCode());
				// This is meant to clear the suppression caches. For if someone uses this to update a Host LMS instead of just creating it
				eventPublisher.publishEvent(new RulesetRelatedDataChangedEvent(saved));
			})
			// Pass the warnings to verification. We might be able to make the response a bit nicer
			.flatMap(saved -> performVerification(saved, configWarnings))
			.toFuture();
	}

// This is where we verify that we can actually connect and do things
	private Mono<CreateHostLmsResult> performVerification(DataHostLms hostLms, List<String> initialWarnings) {
		CreateHostLmsResult.CreateHostLmsResultBuilder resultBuilder = CreateHostLmsResult.builder()
			.warnings(new ArrayList<>(initialWarnings))
			.hostLms(hostLms);

		// Ping first
		Mono<String> pingCheck = hostLmsService.getClientFor(hostLms)
			.flatMap(HostLmsClient::ping)
			.map(pingResponse -> "Status: " + pingResponse.getStatus() +
				(pingResponse.getAdditional() != null ? " - " + pingResponse.getAdditional() : ""))
			.onErrorResume(e -> Mono.just("Ping Failed: " + e.getMessage()));

		// Ideally we'd also look up a patron or do something else that demonstrates auth access

		Mono<String> ingestCheck = hostLmsService.getIngestSourceFor(hostLms)
			.flatMap(source -> {
				if (source instanceof SourceRecordDataSource srds) {
					// Try and get the first chunk, see what happens. Not sure if applicable to all LMS
					return Mono.from(srds.getChunk(Optional.empty()))
						.map(chunk -> "Success: Retrieved chunk with " + chunk.getSize() + " records.")
						.timeout(Duration.ofSeconds(20)); // Do not wait forever
				} else {
					return Mono.just("Skipped: Source does not support chunked retrieval.");
				}
			})
			.onErrorResume(e -> {
				log.error("Ingest check failed for {}", hostLms.getCode(), e);
				return Mono.just("Ingest Check Failed: " + e.getMessage());
			});

		// Execute checks in parallel and build result
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
	public static class CreateHostLmsResult {
		DataHostLms hostLms;
		String pingStatus;
		String ingestStatus;
		List<String> warnings;
	}
}
