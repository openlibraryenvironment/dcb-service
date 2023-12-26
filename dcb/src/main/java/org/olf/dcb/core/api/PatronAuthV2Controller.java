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
import static org.olf.dcb.core.api.PatronAuthV2Controller.Status.*;


/**
 * BETA Feature: A new way to process authentication requests that encodes the patrons home institution
 * in their principal identity - which makes the auth payload siginificantly better aligned with most
 * auth systems and able to pass transparently through (For example) keycloak adapters without needing to
 * create very specialised identity providers
 */
@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/v2/patron/auth")
@Tag(name = "Patron Auth API v2")
public class PatronAuthV2Controller {

	private static final Logger log = LoggerFactory.getLogger(PatronAuthV2Controller.class);

	private final AgencyRepository agencyRepository;
	private final HostLmsService hostLmsService;
	private final HostLmsRepository hostLmsRepository;

	public PatronAuthV2Controller(
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
			content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = V2PatronCredentials.class),
				examples = {
					@ExampleObject(
						value = "{\"principal\": \"6lind/BAR123456\", \"credentials\": \"1234\", \"as\":\"6lind/FOO3434\"}"
					)
				}),
			required = true
		))
	public Mono<HttpResponse<LocalPatronDetails>> patronAuth(@Body @Valid V2PatronCredentials request) {
		log.info("RESTv2, verify patron {}", request);

		String[] principalComponents = request.getPrincipal().split("/");
		String agencyCode = principalComponents[0];
		String username = principalComponents[1];

		return Mono.from(agencyRepository.findOneByCode(agencyCode))
				.flatMap(this::addHostLms)
				.flatMap(agency -> patronAuth(request, agency, username))
				.switchIfEmpty( Mono.defer(() -> { return invalid(request); } ) )
				.map(HttpResponse::ok);
	}

	private Mono<LocalPatronDetails> patronAuth(V2PatronCredentials creds, DataAgency agency, String username) {
		log.info("patronAuth({},{}) {}",creds,agency,agency.getAuthProfile());

		return hostLmsService.getClientFor(agency.getHostLms().code)
			.flatMap(hostLmsClient -> hostLmsClient.patronAuth( agency.getAuthProfile(), username, creds.getCredentials()))
			.map(patron -> LocalPatronDetails.builder()
				.status(VALID)
				.localPatronId(patron.getLocalId())
				.agencyCode(agency.getCode())
				.systemCode(agency.getHostLms().code)
				.homeLocationCode(patron.getLocalHomeLibraryCode())
				.build());
	}

	private static Mono<LocalPatronDetails> invalid(V2PatronCredentials patronCredentials) {
		log.warn("Unable to authenticate patron: {}", patronCredentials);
		return Mono.just(LocalPatronDetails.builder().status(INVALID).build());
	}

	@Builder
	@Data
	@Serdeable
	public static class V2PatronCredentials {
		@Schema(name = "principal", description = "The principal and their home agency in the format AGENCY/patronid", type = "string", example = "ab6")
		String principal;
		@Schema(name = "credentials", description = "Patrons PIN, name or password", type = "string", example = "1234")
		String credentials;
		@Schema(name = "as", description = "The identity to assume as an identifier, using the same authority as the principal if just patronid or with a different authority as AUTHORITY/patronid", type = "string", example = "1234")
		String as;
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

	/**
 	 * A secured endpoint to look up a user record by their ID in a remote system.
	 */
 	@Secured({ "ADMIN" })
 	@Post(value="/lookup", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	public Mono<HttpResponse<LocalPatronDetails>> getUserByLocalPrincipal(@Body @Valid V2PatronCredentials c) {

		log.info("PatronAuthController::getUserByLocalPrincipal({})",c);

		String[] principalComponents = c.getPrincipal().split("/");
		String agencyCode = principalComponents[0];
		String username = principalComponents[1];

		// Lookup agency
		return Mono.from(agencyRepository.findOneByCode(agencyCode))
		// Attach hostLMS to agency object
			.flatMap(this::addHostLms)
			// Ask HostLMS to look up username
			.flatMap(agency -> patronByUsername(c, agency, username))
			.switchIfEmpty( Mono.defer(() -> { return invalid(c); } ) )
       .map(HttpResponse::ok);
	}

	private Mono<LocalPatronDetails> patronByUsername(V2PatronCredentials creds, DataAgency agency, String username) {
    log.debug("patronByUsername({},{}) {}",creds,agency,agency.getAuthProfile(),username);
		return hostLmsService.getClientFor(agency.getHostLms().code)
			.flatMap(hostLmsClient -> hostLmsClient.getPatronByUsername( username ))
			.map(patron -> LocalPatronDetails.builder()
				.status(VALID)
				.localPatronId(patron.getLocalId())
				.agencyCode(agency.getCode())
				.systemCode(agency.getHostLms().code)
				.homeLocationCode(patron.getLocalHomeLibraryCode())
				.build());
	}

}
