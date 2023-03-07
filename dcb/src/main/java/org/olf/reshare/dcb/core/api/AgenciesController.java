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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import org.olf.reshare.dcb.core.model.DataAgency;
import org.olf.reshare.dcb.storage.AgencyRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

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

/*
        @Secured(SecurityRule.IS_ANONYMOUS)
        @Operation(
                summary = "Browse Agencies",
                description = "Paginate through the list of known agencies",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/{?pageable*}")
        public Mono<Page<DataAgency>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from(agencyRepository.findAll(pageable));
        }
*/

        @Get("/{id}") 
        public Mono<DataAgency> show(UUID id) {
                return Mono.from(agencyRepository.findById(id)); 
        }
}
