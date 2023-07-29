package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.DataAgency;
import reactor.core.publisher.Mono;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Singleton
public class RequestWorkflowContextHelper {

        private static final Logger log = LoggerFactory.getLogger(RequestWorkflowContextHelper.class);

        private final SupplierRequestService supplierRequestService;
        private final ReferenceValueMappingRepository referenceValueMappingRepository;
        private final SupplierRequestRepository supplierRequestRepository;
        private final PatronRequestRepository patronRequestRepository;
        private final PatronIdentityRepository patronIdentityRepository;
        private final AgencyRepository agencyRepository;
        private final HostLmsRepository hostLmsRepository;

        public RequestWorkflowContextHelper(
                ReferenceValueMappingRepository referenceValueMappingRepository,
                SupplierRequestService supplierRequestService,
                HostLmsRepository hostLmsRepository,
                SupplierRequestRepository supplierRequestRepository,
                PatronRequestRepository patronRequestRepository,
                PatronIdentityRepository patronIdentityRepository,
                AgencyRepository agencyRepository) {
                this.supplierRequestService = supplierRequestService;
                this.referenceValueMappingRepository = referenceValueMappingRepository;
                this.hostLmsRepository = hostLmsRepository;
                this.supplierRequestRepository = supplierRequestRepository;
                this.patronRequestRepository = patronRequestRepository;
                this.patronIdentityRepository = patronIdentityRepository;
                this.agencyRepository = agencyRepository;
        }

        public Mono<RequestWorkflowContext> fromPatronRequestId(UUID patronRequestId) {
                return Mono.from(patronRequestRepository.findById(patronRequestId))
                        .flatMap( this::fromPatronRequest );
        }

	// Given a patron request, construct the patron request context containing all related objects for a workflow
        public Mono<RequestWorkflowContext> fromPatronRequest(PatronRequest pr) {
		RequestWorkflowContext rwc = new RequestWorkflowContext();
                return  Mono.just(rwc.setPatronRequest(pr))
			.flatMap(this::findSupplierRequest )
			.flatMap(this::decorateContextWithPatronDetails )
                        .flatMap(this::resolvePickupLocationAgency)
                        ;
        }

	// Given a supplier request, construct the patron request context containing all related objects for a workflow
        public Mono<RequestWorkflowContext> fromSupplierRequest(SupplierRequest sr) {
                RequestWorkflowContext rwc = new RequestWorkflowContext();

                return Mono.just( rwc.setSupplierRequest(sr) )
			.doOnNext(rwcp -> log.debug("decorate {}",rwcp))
                        .flatMap( rwcp -> Mono.from(supplierRequestRepository.findPatronRequestById(rwc.getSupplierRequest().getId())) )
			.doOnNext(pr -> log.debug("got pr {}",pr))
                        .flatMap( pr -> Mono.just(rwc.setPatronRequest(pr)) )
			.doOnNext(rwcp -> log.debug("decorate {}",rwcp))
			.flatMap( this::decorateContextWithPatronDetails )
			.doOnNext(rwcp -> log.debug("decorate {}",rwcp))
                        .flatMap( this::resolvePickupLocationAgency )
			.doOnNext(rwcp -> log.debug("decorate {}",rwcp))
                        ;
        }


	// The patron request should have an attached patronIdentity, the supplier request should have a virtual identity. 
	// Find and attach those records here.
	// We also need   patronAgencyCode, patronSystemCode and patronAgency
	private Mono<RequestWorkflowContext> decorateContextWithPatronDetails(RequestWorkflowContext ctx) {

		log.debug("decorateContextWithPatronDetails {}",ctx);

		if ( ( ctx.getPatronRequest() == null ) || ( ctx.getPatronRequest().getId() == null ) ) {
			log.error("Context does not have a patron request");
			throw new RuntimeException("Unable to locate patron request in workflow context");
		}

		return getRequestingIdentity(ctx)
			.flatMap(this::decorateContextWithPatronAgency)
			;
	}

	private Mono<RequestWorkflowContext> decorateContextWithPatronAgency(RequestWorkflowContext ctx) {

		log.debug("decorateContextWithPatronAgency {}",ctx);

		return Mono.from(patronIdentityRepository.findResolvedAgencyById(ctx.getPatronHomeIdentity().getId()))
			.flatMap( agency -> {
				ctx.setPatronAgency(agency);
				ctx.setPatronAgencyCode(agency.getCode());
				return Mono.just(ctx);
			});
	}


	// We find the patrons requesting identity via the patron request requestingIdentity property. this should NEVER be null
	private Mono<RequestWorkflowContext> getRequestingIdentity(RequestWorkflowContext ctx) {
		return Mono.from(patronRequestRepository.findRequestingIdentityById(ctx.getPatronRequest().getId()))
                        .flatMap( pid -> Mono.just(ctx.setPatronHomeIdentity(pid)) );
	}

        // Remember that @Accessors means that setSupplierRequest returns this
        //
        // If there is a -live- supplier request availabe for this patron request attach it to the context
        //
        private Mono<RequestWorkflowContext> findSupplierRequest(RequestWorkflowContext ctx) {
                log.debug("findSupplierRequst....");
                return supplierRequestService.findSupplierRequestFor(ctx.getPatronRequest())
                        .map(supplierRequest -> ctx.setSupplierRequest(supplierRequest))
                        .defaultIfEmpty(ctx);
        }               

        // A Patron request can specify a pickup location - resolve the agency and system for that location given
	// The procedure for turning a pickup location code into an agency code is different to the other kinds of identifiers
	// it is tempting to thing that resolving patron and lending systems could be coalesced into a single function, but
	// this is problematic due to the semantic difference. Please think carefully before attempting this (Desireable) consolidation
        private Mono<RequestWorkflowContext> resolvePickupLocationAgency(RequestWorkflowContext ctx) {
                log.debug("resolve pickup location....{}",ctx.getPatronRequest().getPickupLocationCode());
                return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
                        "PickupLocation",
                        "DCB",
                        ctx.getPatronRequest().getPickupLocationCode(),
                        "AGENCY",
                        "DCB"))
                        .switchIfEmpty(Mono.error(new RuntimeException("No mapping found for pickup location \""+ctx.getPatronRequest().getPickupLocationCode()+"\""))) 
                        .flatMap(rvm -> { return Mono.from(getDataAgencyWithHostLms(rvm.getToValue())); } )
                        .flatMap(pickupAgency -> { return Mono.just(ctx.setPickupAgency(pickupAgency)); } )
                        .flatMap(ctx2 -> { return Mono.just(ctx2.setPickupAgencyCode(ctx2.getPickupAgency().getCode())); } )
                        .flatMap(ctx2 -> { return Mono.just(ctx2.setPickupSystemCode(ctx2.getPickupAgency().getHostLms().getCode())); } )
                        ;
        }

        // Get the agency and get the related HostLMS
        // I know this looks a little macarbe. It seems that mn-data can't process the DataHostLms because of the transient getType method
        // this means that the agencyRepo.findHostLmsById approach does't work as expected, so we need to get the agency in a 2 step
        // process where we first grab the ID of the hostLms then we get it by ID
        private Mono<DataAgency> getDataAgencyWithHostLms(String code) {
                return Mono.from(agencyRepository.findOneByCode(code))
                        .flatMap(agency -> {
                                log.debug("getDataAgencyWithHostLms({}) {}",code,agency);
                                return Mono.from(agencyRepository.findHostLmsIdById(agency.getId()))
                                        .flatMap( hostLmsId -> { return Mono.from(hostLmsRepository.findById(hostLmsId)); } )
                                        .flatMap( hostLms -> {
                                                agency.setHostLms(hostLms);
                                                return Mono.just(agency);
                                        });
                                // return Mono.just(agency);
                        });
        }

}

