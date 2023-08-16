package org.olf.dcb.request.workflow;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;

@Prototype
public class FinaliseCompletedRequestTransition implements PatronRequestStateTransition {

	private static final Logger log = LoggerFactory.getLogger(FinaliseCompletedRequestTransition.class);


	private final PatronIdentityRepository patronIdentityRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final AgencyRepository agencyRepository;
	private final SupplyingAgencyService supplyingAgencyService;
	private final BorrowingAgencyService borrowingAgencyService;

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	public FinaliseCompletedRequestTransition(PatronIdentityRepository patronIdentityRepository, HostLmsService hostLmsService,
			PatronRequestAuditService patronRequestAuditService,
			ReferenceValueMappingRepository referenceValueMappingRepository,
			BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
			AgencyRepository agencyRepository,
                        SupplyingAgencyService supplyingAgencyService,
                        BorrowingAgencyService borrowingAgencyService) {

		this.patronIdentityRepository = patronIdentityRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.agencyRepository = agencyRepository;
		this.supplyingAgencyService = supplyingAgencyService;
		this.borrowingAgencyService = borrowingAgencyService;
	}

	/**
	 * Attempts to transition the patron request to the next state, 
	 *
	 * @param patronRequest The patron request to transition.
	 * @return A Mono that emits the patron request after the transition, or an
	 *         error if the transition is not possible.
	 */
	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("finalizeRequest {}", patronRequest);

		assert isApplicableFor(patronRequest);

                return Mono.just(patronRequest)
                        .flatMap( supplyingAgencyService::cleanUp )
                        .flatMap( borrowingAgencyService::cleanUp )
			.doOnSuccess( pr -> log.debug("finalized patron request: {}", pr))
                        .doOnError( error -> log.error( "Error occurred finalizing a patron request: {}", error.getMessage()))
                        .map( pr -> { pr.setStatus(Status.FINALISED); return pr; } )
			.flatMap(this::createAuditEntry)
		        .transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}


	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		if (patronRequest.getStatus() == Status.ERROR)
			return Mono.just(patronRequest);
		return patronRequestAuditService.addAuditEntry(patronRequest, Status.COMPLETED, getTargetStatus().get())
				.thenReturn(patronRequest);
	}

	@Override
	public boolean isApplicableFor(PatronRequest pr) {
		return pr.getStatus() == Status.COMPLETED;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.FINALISED);
	}

}
