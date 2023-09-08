package org.olf.dcb.core.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;


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
	@Operation(
		summary = "Verify Patron Credentials",
		description = "Verify a patron's login details",
		requestBody = @RequestBody(
			description = "Request body containing patron credentials.",
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PatronCredentials.class),
				examples = {
				@ExampleObject(
						name = "BASIC/BARCODE+PIN",
						value = "{\"agencyCode\": \"ab6\", \"patronPrinciple\": \"BAR123456\", \"secret\": \"1234\"}"
					),
					@ExampleObject(
						name = "BASIC/BARCODE+NAME",
						value = "{\"agencyCode\": \"ab6\", \"patronPrinciple\": \"BAR789012\", \"secret\": \"John Doe\"}"
					)
				}),
			required = true
		))
	public Mono<HttpResponse<VerificationResponse>> patronAuth(@Body @Valid PatronCredentials request) {
		log.debug("REST, verify patron {}", request);
		return Mono.from(agencyRepository.findOneByCode(request.agencyCode))
			.flatMap(this::addHostLms)
			.flatMap(agency -> patronAuth(request, agency))
			.defaultIfEmpty( invalid(request) )
			.map(HttpResponse::ok);
	}

	private Mono<VerificationResponse> patronAuth(PatronCredentials creds, DataAgency agency) {
                log.debug("patronAuth({},{}) {}",creds,agency,agency.getAuthProfile());

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
		@Schema(name = "agencyCode", description = "The agency code associated with the patron", type = "string", example = "ab6")
		String agencyCode;
		@Schema(name = "patronPrinciple", description = "Patrons barcode or unique identifier", type = "string", example = "BAR789012")
		String patronPrinciple;
		@Schema(name = "secret", description = "Patrons PIN or name", type = "string", example = "1234")
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
