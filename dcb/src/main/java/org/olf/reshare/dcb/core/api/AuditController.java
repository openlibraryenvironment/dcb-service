package org.olf.reshare.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olf.reshare.dcb.core.api.datavalidation.*;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

@Secured(SecurityRule.IS_ANONYMOUS)
@Produces(APPLICATION_JSON)
@Controller
@Tag(name = "Audit API")
public class AuditController {

	public static final Logger log = LoggerFactory.getLogger(AuditController.class);
	private final PatronRequestRepository patronRequestRepository;

	public AuditController(PatronRequestRepository patronRequestRepository) {
		this.patronRequestRepository = patronRequestRepository;
	}

	@SingleResult
	@Get(value = "/admin/patrons/requests/{id}", produces = APPLICATION_JSON)
	public Mono<HttpResponse<PatronRequestCommand>> getPatronRequest(@PathVariable("id") final UUID id) {
		log.debug("REST, get patron request with id: {}", id);

		// Bean to return
		PatronRequestCommand patronRequestCommand = new PatronRequestCommand();
		CitationCommand citationCommand = new CitationCommand();
		RequestorCommand requestorCommand = new RequestorCommand();
		PickupLocationCommand pickupLocationCommand = new PickupLocationCommand();
		AgencyCommand agencyCommand = new AgencyCommand();

		return Mono.from(patronRequestRepository.findById(id))
			.map(p -> {
				// TODO: use service to do logic
				// convert model to bean
				patronRequestCommand.setId(p.getId());

				citationCommand.setBibClusterId(p.getBibClusterId());
				patronRequestCommand.setCitation(citationCommand);

				agencyCommand.setCode(p.getPatronAgencyCode());
				requestorCommand.setIdentifiier(p.getPatronId());
				requestorCommand.setAgency(agencyCommand);
				patronRequestCommand.setRequestor(requestorCommand);

				pickupLocationCommand.setCode(p.getPickupLocationCode());
				patronRequestCommand.setPickupLocation(pickupLocationCommand);

				return HttpResponse.ok(patronRequestCommand);
			});
	}

}
