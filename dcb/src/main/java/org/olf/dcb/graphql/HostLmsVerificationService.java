package org.olf.dcb.graphql;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.storage.AgencyRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What creating or updating a Host LMS checks before it reports success. The ping and the
 * first ingest chunk call the Host LMS; the configuration and auth profile checks read only
 * what DCB holds.
 */
@Slf4j
@Singleton
public class HostLmsVerificationService {
	private final HostLmsService hostLmsService;
	private final AgencyRepository agencyRepository;

	public HostLmsVerificationService(HostLmsService hostLmsService, AgencyRepository agencyRepository) {
		this.hostLmsService = hostLmsService;
		this.agencyRepository = agencyRepository;
	}

	public record Verification(String pingStatus, String ingestStatus,
		String configurationStatus, String authProfileStatus) { }

	public Mono<Verification> verify(DataHostLms hostLms) {
		final var client = hostLmsService.getClientFor(hostLms).cache();

		final Mono<String> ping = client
			.flatMap(HostLmsClient::ping)
			.map(pingResponse -> "Status: " + pingResponse.getStatus() +
				(pingResponse.getAdditional() != null ? " - " + pingResponse.getAdditional() : ""))
			.defaultIfEmpty("Ping Skipped or Missing")
			.onErrorResume(e -> Mono.just("Ping Failed: " + e.getMessage()));

		final Mono<String> ingest = hostLmsService.getIngestSourceFor(hostLms)
			.flatMap(source -> source instanceof SourceRecordDataSource srds
				? Mono.from(srds.getChunk(Optional.empty()))
					.map(chunk -> "Success: Retrieved chunk with " + chunk.getSize() + " records.")
					.timeout(Duration.ofSeconds(20))
				: Mono.just("Skipped: Source does not support chunked retrieval."))
			.onErrorResume(e -> {
				log.error("Ingest check failed for {}", hostLms.getCode(), e);
				return Mono.just("Ingest Check Failed: " + e.getMessage());
			});

		final Mono<String> configuration = configurationStatus(client);

		final Mono<String> authProfiles = client
			.flatMap(found -> authProfileStatus(found, hostLms))
			.onErrorResume(e -> Mono.just("Failed: " + e.getMessage()));

		return Mono.zip(ping, ingest, configuration, authProfiles)
			.map(checks -> new Verification(checks.getT1(), checks.getT2(), checks.getT3(), checks.getT4()));
	}

	Mono<String> configurationStatus(Mono<HostLmsClient> client) {
		return client
			.map(HostLmsVerificationService::configurationStatus)
			.onErrorResume(e -> Mono.just("Failed: client could not be built: "
				+ ExceptionUtils.getRootCause(e).getMessage()));
	}

	private static String configurationStatus(HostLmsClient client) {
		final var missing = client.missingConfiguration();

		return missing.isEmpty() ? "OK" : "Missing: " + String.join(", ", missing);
	}

	/** At most one row per agency on this Host LMS: bounded by the member count. */
	Mono<String> authProfileStatus(HostLmsClient client, DataHostLms hostLms) {
		final var supported = client.supportedAuthProfiles();

		if (supported.isEmpty()) {
			return Mono.just("Not checked: this Host LMS does not use an auth profile");
		}

		return Flux.from(agencyRepository.findByHostLmsIds(List.of(hostLms.getId())))
			.collectList()
			.map(agencies -> authProfileStatus(agencies, supported));
	}

	private static String authProfileStatus(List<DataAgency> agencies, List<String> supported) {
		if (agencies.isEmpty()) {
			return "Not checked: no agency uses this Host LMS yet";
		}

		final var unsupported = agencies.stream()
			.filter(agency -> !supported.contains(agency.getAuthProfile()))
			.map(agency -> agency.getCode() + "=" + agency.getAuthProfile())
			.toList();

		return unsupported.isEmpty()
			? "OK"
			: "Unsupported: %s. Supported: %s".formatted(String.join(", ", unsupported),
				String.join(", ", supported));
	}
}
