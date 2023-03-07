package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import javax.validation.Valid;

import org.olf.reshare.dcb.request.fulfilment.PatronRequestService;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import org.olf.reshare.dcb.core.model.Agency;
import org.olf.reshare.dcb.storage.AgencyRepository;
import java.util.List;
import java.util.ArrayList;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/agencies")
@Tag(name = "Agencies")
public class AgenciesController {

	private static final Logger log = LoggerFactory.getLogger(AgenciesController.class);

        private AgencyRepository agencyRepository;

	public AgenciesController(AgencyRepository agencyRepository) {
                this.agencyRepository = agencyRepository;
	}

        @Operation(
                summary = "List agencies"
        )
        @Get("/")
        Mono<Page<Agency>> list() {
                List<Agency> temp_agencies = new ArrayList<Agency>();
                return Mono.just(
                        Page.of(
                                temp_agencies,
                                Pageable.from(1, temp_agencies.size()),
                                temp_agencies.size()));
        }

}
