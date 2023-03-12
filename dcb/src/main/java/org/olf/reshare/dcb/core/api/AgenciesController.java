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
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import org.olf.reshare.dcb.core.model.DataAgency;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.olf.reshare.dcb.core.api.types.AgencyDTO;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/agencies")
@Tag(name = "Agencies")
public class AgenciesController {

	private static final Logger log = LoggerFactory.getLogger(AgenciesController.class);

        private AgencyRepository agencyRepository;
	private HostLmsRepository hostLmsRepository;

	public AgenciesController(AgencyRepository agencyRepository,
                                  HostLmsRepository hostLmsRepository) {
                this.agencyRepository = agencyRepository;
                this.hostLmsRepository = hostLmsRepository;
	}

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

        @Get("/{id}") 
        public Mono<DataAgency> show(UUID id) {
                return Mono.from(agencyRepository.findById(id)); 
        }

        /*
 	@Post("/")
	public Mono<DataAgency> postAgency(@Body DataAgency agency) {
                return Mono.from(agencyRepository.existsById(agency.getId()))
                        .flatMap(exists -> Mono.fromDirect(exists ? agencyRepository.update(agency) : agencyRepository.save(agency)));
	}
        */

	// TODO: Convert return DataAgency to AgencyDTO
        @Post("/")
        public Mono<DataAgency> postAgency(@Body AgencyDTO agency) {

		// Convert AgencyDTO into DataAgency with correctly linked HostLMS
		DataHostLms lms = Mono.from(hostLmsRepository.findByCode(agency.hostLMSCode())).block();
                DataAgency da = new DataAgency(agency.id(),
                                               agency.code(),
                                               agency.name(),
                                               lms);
 
                return Mono.from(agencyRepository.existsById(da.getId()))
                       .flatMap(exists -> Mono.fromDirect(exists ? agencyRepository.update(da) : agencyRepository.save(da)));
        }

}
