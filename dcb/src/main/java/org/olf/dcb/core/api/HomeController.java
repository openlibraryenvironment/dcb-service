package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.TEXT_PLAIN;

import java.security.Principal;

import org.olf.dcb.security.RoleNames;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Testing purposes
 */

@Slf4j
@Controller
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "Management API")
public class HomeController {

	@Produces(TEXT_PLAIN)
	@Get
	public String index(Principal principal) {
		return principal.getName();
	}

	@Secured(RoleNames.ADMINISTRATOR)
	@Get(value = "/secured", produces = TEXT_PLAIN)
	public String securedEndpoint(Principal principal) {
		io.micronaut.security.authentication.ServerAuthentication sa = (io.micronaut.security.authentication.ServerAuthentication) principal;
		log.info("principal: {}", principal);
		log.info("attrs: {}", sa.getAttributes());
		log.info("roles: {}", sa.getRoles());
		log.info("json: {}", sa.toJson());
		return principal.getName();
	}

}
