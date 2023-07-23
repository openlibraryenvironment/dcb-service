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
import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RequestWorkflowContextHelper {

        private static final Logger log = LoggerFactory.getLogger(RequestWorkflowContextHelper.class);
        private final SupplierRequestService supplierRequestService;
        private final ReferenceValueMappingRepository referenceValueMappingRepository;
        private final AgencyRepository agencyRepository;
        private final HostLmsRepository hostLmsRepository;

        public RequestWorkflowContextHelper(
                ReferenceValueMappingRepository referenceValueMappingRepository,
                SupplierRequestService supplierRequestService,
                HostLmsRepository hostLmsRepository,
                AgencyRepository agencyRepository) {
                this.supplierRequestService = supplierRequestService;
                this.referenceValueMappingRepository = referenceValueMappingRepository;
                this.hostLmsRepository = hostLmsRepository;
                this.agencyRepository = agencyRepository;
        }

        public Mono<RequestWorkflowContext> collect(RequestWorkflowContext context) {
                return findSupplierRequest(context)
                        .flatMap(this::resolvePickupLocationAgency)
                        ;
        }

        // Remember that @Accessors means that setSupplierRequest returns this
        //
        // If there is a -live- supplier request availabe for this patron request attach it to the context
        //
        private Mono<RequestWorkflowContext> findSupplierRequest(RequestWorkflowContext ctx) {
                return supplierRequestService.findSupplierRequestFor(ctx.getPatronRequest())
                        .map(supplierRequest -> ctx.setSupplierRequest(supplierRequest))
                        .defaultIfEmpty(ctx);
        }               

        // A Patron request can specify a pickup location - resolve the agency and system for that location given
        private Mono<RequestWorkflowContext> resolvePickupLocationAgency(RequestWorkflowContext ctx) {
                return Mono.from(referenceValueMappingRepository.findByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
                        "PickupLocation",
                        "DCB",
                        ctx.getPatronRequest().getPickupLocationCode(),
                        "AGENCY",
                        "DCB"))
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
                                                log.warn("setting agency host lms to {}",hostLms);
                                                agency.setHostLms(hostLms);
                                                return Mono.just(agency);
                                        });
                                // return Mono.just(agency);
                        });
        }

}

