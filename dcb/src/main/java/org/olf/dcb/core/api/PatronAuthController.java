package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.core.api.PatronAuthController.Status.INVALID;
import static org.olf.dcb.core.api.PatronAuthController.Status.VALID;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Role;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Mono;

@Controller("/patron/auth")
@Validated
@Secured({ RoleNames.ADMINISTRATOR, RoleNames.INTERNAL_API })
@Tag(name = "Patron Auth API")
public class PatronAuthController {
	private static final Logger log = LoggerFactory.getLogger(PatronAuthController.class);
	private final AgencyRepository agencyRepository;
	private final HostLmsService hostLmsService;
	private final HostLmsRepository hostLmsRepository;

	public PatronAuthController(AgencyRepository agencyRepository, HostLmsService hostLmsService,
			HostLmsRepository hostLmsRepository) {
		this.agencyRepository = agencyRepository;
		this.hostLmsService = hostLmsService;
		this.hostLmsRepository = hostLmsRepository;
	}
	
	@Secured(SecurityRule.IS_ANONYMOUS)
	@Post(consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	@Operation(summary = "Verify Patron Credentials", description = "Verify a patron's login details", requestBody = @RequestBody(description = "Request body containing patron credentials.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PatronCredentials.class), examples = {
			@ExampleObject(name = "BASIC/BARCODE+PIN", description = "Sierra only", value = "{\"agencyCode\": \"ab6\", \"patronPrinciple\": \"BAR123456\", \"secret\": \"1234\"}"),
			@ExampleObject(name = "BASIC/BARCODE+NAME", description = "Sierra only", value = "{\"agencyCode\": \"ab6\", \"patronPrinciple\": \"BAR789012\", \"secret\": \"John Doe\"}"),
			@ExampleObject(name = "BASIC/BARCODE+PASSWORD", description = "Polaris only", value = "{\"agencyCode\": \"ab6\", \"patronPrinciple\": \"BAR789012\", \"secret\": \"password123\"}") }), required = true))
	public Mono<HttpResponse<LocalPatronDetails>> patronAuth(@Body @Valid PatronCredentials request) {
		log.info("REST, verify patron {}", request);
		return Mono.from(agencyRepository.findOneByCode(request.agencyCode)).flatMap(this::addHostLms)
				.flatMap(agency -> patronAuth(request, agency)).switchIfEmpty(Mono.defer(() -> {
					return invalid(request);
				})).map(HttpResponse::ok);
	}

	private Mono<LocalPatronDetails> patronAuth(PatronCredentials creds, DataAgency agency) {
		log.info("patronAuth({},{}) {}", creds, agency, agency.getAuthProfile());

		return hostLmsService.getClientFor(agency.getHostLms().code)
				.flatMap(
						hostLmsClient -> hostLmsClient.patronAuth(agency.getAuthProfile(), creds.patronPrinciple, creds.secret))
				.map(patron -> LocalPatronDetails.builder()
					.status(VALID)
					.localPatronId(patron.getLocalId())
					.agencyCode(agency.getCode())
					.systemCode(agency.getHostLms().code)
					.homeLocationCode(patron.getLocalHomeLibraryCode())
					.build());
	}

	private static Mono<LocalPatronDetails> invalid(PatronCredentials patronCredentials) {
		log.warn("Unable to authenticate patron: {}", patronCredentials);
		return Mono.just(LocalPatronDetails.builder().status(INVALID).build());
	}

	@Builder
	@Data
	@Serdeable
	public static class PatronCredentials {
		@Schema(name = "agencyCode", description = "The agency code associated with the patron", type = "string", example = "ab6")
		String agencyCode;
		@Schema(name = "patronPrinciple", description = "Patrons barcode or unique identifier", type = "string", example = "BAR789012")
		String patronPrinciple;
		@Schema(name = "secret", description = "Patrons PIN, name or password", type = "string", example = "1234")
		String secret;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class LocalPatronDetails {
		Status status;
		List<String> localPatronId;
		String agencyCode;
		String systemCode;
		String homeLocationCode;

		// If we are able to infer a DCB agency code from the data returned from the patron, store it here
		String inferredAgencyCode;

		private boolean isValid() {
			return VALID.equals(status);
		}
	}

	@Serdeable
	public enum Status {
		VALID, INVALID
	}

	private Mono<DataAgency> addHostLms(DataAgency dataAgency) {
		log.debug("addHostLms: {}", dataAgency);
		return Mono.from(agencyRepository.findHostLmsIdById(dataAgency.getId()))
				.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId))).map(dataAgency::setHostLms);
	}

	/**
	 * A secured endpoint to look up a user record by their ID in a remote system.
	 */
	@Post(uri = "/lookup", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	@Secured({ RoleNames.ADMINISTRATOR, RoleNames.INTERNAL_API, RoleNames.CONSORTIUM_ADMIN, RoleNames.LIBRARY_ADMIN, RoleNames.LIBRARY_READ_ONLY})
	public Mono<HttpResponse<LocalPatronDetails>> getUserByLocalPrincipal(@Body @Valid PatronCredentials c) {

		log.info("PatronAuthController::getUserByLocalPrincipal({})", c);

		// Lookup agency
		return Mono.from(agencyRepository.findOneByCode(c.agencyCode))
				// Attach hostLMS to agency object
				.flatMap(this::addHostLms)
				// Ask HostLMS to look up username
				.flatMap(agency -> patronByUsername(c, agency)).switchIfEmpty(Mono.defer(() -> {
					return invalid(c);
				})).map(HttpResponse::ok);
	}

	private Mono<LocalPatronDetails> patronByUsername(PatronCredentials creds, DataAgency agency) {
		log.debug("patronByUsername({},{}) {}", creds, agency, agency.getAuthProfile());
		return hostLmsService.getClientFor(agency.getHostLms().code)
				.flatMap(hostLmsClient -> hostLmsClient.getPatronByUsername(creds.patronPrinciple))
				.map(patron -> LocalPatronDetails.builder().status(VALID).localPatronId(patron.getLocalId())
						.agencyCode(agency.getCode()).systemCode(agency.getHostLms().code)
						.homeLocationCode(patron.getLocalHomeLibraryCode()).build());
	}

}
