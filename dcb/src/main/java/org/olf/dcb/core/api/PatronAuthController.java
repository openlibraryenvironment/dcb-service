package org.olf.dcb.core.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.core.api.PatronAuthController.Status.*;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/patron/auth")
@Tag(name = "Patron Auth API")
public class PatronAuthController {
	private static final Logger log = LoggerFactory.getLogger(PatronAuthController.class);
	private final AgencyRepository agencyRepository;
	private final HostLmsService hostLmsService;
	private final HostLmsRepository hostLmsRepository;

	public PatronAuthController(
		AgencyRepository agencyRepository,
		HostLmsService hostLmsService,
		HostLmsRepository hostLmsRepository)
	{
		this.agencyRepository = agencyRepository;
		this.hostLmsService = hostLmsService;
		this.hostLmsRepository = hostLmsRepository;
	}

	@Post(consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	public Mono<HttpResponse<VerificationResponse>> patronAuth(@Body PatronCredentials request) {
		log.debug("REST, verify patron {}", request);
		return Mono.from(agencyRepository.findOneByCode(request.agencyCode))
			.flatMap(this::addHostLms)
			.flatMap(agency -> patronAuth(request, agency))
			.defaultIfEmpty( invalid(request) )
			.map(HttpResponse::ok);
	}

	private Mono<VerificationResponse> patronAuth(PatronCredentials creds, DataAgency agency) {
		return hostLmsService.getClientFor(agency.getHostLms().code)
			.flatMap(hostLmsClient -> hostLmsClient.patronAuth( agency.getAuthProfile(), creds.patronPrinciple, creds.secret))
			.map(patron -> VerificationResponse.builder()
				.status(VALID)
				.localPatronId(patron.getLocalId())
				.agencyCode(agency.getCode())
				.systemCode(agency.getHostLms().code)
				.homeLocationCode(patron.getLocalHomeLibraryCode())
				.build());
	}

	private static VerificationResponse invalid(PatronCredentials patronCredentials) {
		log.debug("Unable to authenticate patron: {}", patronCredentials);
		return VerificationResponse.builder().status(INVALID).build();
	}

	@Builder
	@Data
	@Serdeable
	public static class PatronCredentials {
		String agencyCode;
		String patronPrinciple;
		String secret;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class VerificationResponse {
		Status status;
		List<String> localPatronId;
		String agencyCode;
		String systemCode;
		String homeLocationCode;
		private boolean isValid() {
			return VALID.equals(status);
		}
	}

	@Serdeable
	public enum Status { VALID, INVALID }

	private Mono<DataAgency> addHostLms(DataAgency dataAgency) {
		log.debug("addHostLms: {}",dataAgency);
		return Mono.from(agencyRepository.findHostLmsIdById(dataAgency.getId()))
			.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.map(dataAgency::setHostLms);
	}
}
