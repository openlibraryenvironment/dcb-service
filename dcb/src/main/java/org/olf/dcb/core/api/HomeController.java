package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.TEXT_PLAIN;
import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;

import java.security.Principal;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Testing purposes
 */

@Slf4j
@Tag(name = "Management API")
@Secured(IS_AUTHENTICATED)
@Controller
public class HomeController {

	@Produces(TEXT_PLAIN)
	@Get
	public String index(Principal principal) {
		return principal.getName();
	}

        @Secured({"ADMIN"}) 
        @Get(value="/secured", produces = TEXT_PLAIN)
	public String securedEndpoint(Principal principal) {
		io.micronaut.security.authentication.ServerAuthentication sa = (io.micronaut.security.authentication.ServerAuthentication) principal;
		log.info("principal: {}", principal);
		log.info("attrs: {}", sa.getAttributes());
		log.info("roles: {}", sa.getRoles());
		log.info("json: {}", sa.toJson());
		return principal.getName();
	}

}
