package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class RequestWorkflowContextHelper {
	private static final Logger log = LoggerFactory.getLogger(RequestWorkflowContextHelper.class);

	private final SupplierRequestService supplierRequestService;
	private final ReferenceValueMappingService referenceValueMappingService;

	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestRepository patronRequestRepository;
	private final PatronIdentityRepository patronIdentityRepository;
	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;

	public RequestWorkflowContextHelper(
		SupplierRequestService supplierRequestService,
		ReferenceValueMappingService referenceValueMappingService,
		HostLmsRepository hostLmsRepository,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestRepository patronRequestRepository,
		PatronIdentityRepository patronIdentityRepository,
		AgencyRepository agencyRepository) {

		this.supplierRequestService = supplierRequestService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.hostLmsRepository = hostLmsRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestRepository = patronRequestRepository;
		this.patronIdentityRepository = patronIdentityRepository;
		this.agencyRepository = agencyRepository;
	}

	// Given a patron request, construct the patron request context containing all related objects for a workflow
	public Mono<RequestWorkflowContext> fromPatronRequest(PatronRequest pr) {
		RequestWorkflowContext rwc = new RequestWorkflowContext();
		log.info("fromPatronRequest {}", pr.getId());

		return  Mono.just(rwc.setPatronRequest(pr))
		.flatMap(this::findSupplierRequest)
		.flatMap(this::decorateWithPatronVirtualIdentity)
		.flatMap(this::decorateContextWithPatronDetails)
		.flatMap(this::decorateContextWithLenderDetails)
		.flatMap(this::resolvePickupLocationAgency)
		.flatMap(this::report);
	}

	// Given a supplier request, construct the patron request context containing all related objects for a workflow
	public Mono<RequestWorkflowContext> fromSupplierRequest(SupplierRequest sr) {
		RequestWorkflowContext rwc = new RequestWorkflowContext();
		log.info("fromSupplierRequest {}",sr.getId());

		return Mono.just( rwc.setSupplierRequest(sr) )
			.flatMap(rwcp -> Mono.from(supplierRequestRepository.findPatronRequestById(rwc.getSupplierRequest().getId())))
			.flatMap(pr -> Mono.just(rwc.setPatronRequest(pr)))
			.flatMap(this::decorateWithPatronVirtualIdentity)
			.flatMap(this::decorateContextWithPatronDetails)
			.flatMap(this::decorateContextWithLenderDetails)
			.flatMap(this::resolvePickupLocationAgency)
			.flatMap(this::report);
	}

	private Mono<RequestWorkflowContext> decorateContextWithLenderDetails(RequestWorkflowContext ctx) {
		log.info("decorateContextWithLenderDetails");

		ctx.setLenderAgencyCode(ctx.getSupplierRequest().getLocalAgency());
		ctx.setLenderSystemCode(ctx.getSupplierRequest().getHostLmsCode());

		return Mono.just(ctx);
	}

	// The patron request should have an attached patronIdentity, the supplier request should have a virtual identity. 
	// Find and attach those records here.
	// We also need patronAgencyCode, patronSystemCode and patronAgency
	private Mono<RequestWorkflowContext> decorateContextWithPatronDetails(RequestWorkflowContext ctx) {
		log.info("decorateContextWithPatronDetails");

		if ((ctx.getPatronRequest() == null ) || ( ctx.getPatronRequest().getId() == null)) {
			log.error("Context does not have a patron request");
			throw new RuntimeException("Unable to locate patron request in workflow context");
		}

		return getRequestingIdentity(ctx)
			.flatMap(this::decorateContextWithPatronAgency)
			.flatMap(this::decorateContextWithPatronSystem)
			.defaultIfEmpty(ctx);
	}

	private Mono<RequestWorkflowContext> decorateContextWithPatronAgency(RequestWorkflowContext ctx) {
		log.info("decorateContextWithPatronAgency");

		if (ctx.getPatronHomeIdentity() != null) {
			return Mono.from(patronIdentityRepository.findResolvedAgencyById(ctx.getPatronHomeIdentity().getId()))
				.flatMap(agency -> {
					log.debug("Found patron agency {}",agency);
					ctx.setPatronAgency(agency);
					ctx.setPatronAgencyCode(agency.getCode());
					return Mono.just(ctx);
				})
				.defaultIfEmpty(ctx);
		}
		else {
			log.warn("Cannot add patron agency to request - patron home identity is not attached to PR");
		}

		return Mono.just(ctx);
	}

	private Mono<RequestWorkflowContext> decorateContextWithPatronSystem(RequestWorkflowContext ctx) {
		log.info("decorateContextWithPatronSystem");

		// There is a problem here - as per getDataAgencyWithHostLms agencyRepository.findHostLmsById doesn't work directly
		return Mono.from(agencyRepository.findHostLmsIdById(ctx.getPatronAgency().getId()))
			.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.flatMap(patronHostLms -> {
				ctx.setPatronSystem(patronHostLms);
				ctx.setPatronSystemCode(patronHostLms.getCode());
				return Mono.just( ctx );
			})
			.defaultIfEmpty(ctx);
	}

	// We find the patrons requesting identity via the patron request requestingIdentity property. this should NEVER be null
	private Mono<RequestWorkflowContext> getRequestingIdentity(RequestWorkflowContext ctx) {
		log.info("getRequestingIdentity");

		// log.debug("getRequestingIdentity for request {}",ctx.getPatronRequest());
		return Mono.from(patronRequestRepository.findRequestingIdentityById(ctx.getPatronRequest().getId()))
			.flatMap(pid -> Mono.just(ctx.setPatronHomeIdentity(pid)))
			.defaultIfEmpty(ctx);
	}

	// Remember that @Accessors means that setSupplierRequest returns this
	//
	// If there is a -live- supplier request availabe for this patron request attach it to the context
	//
	private Mono<RequestWorkflowContext> findSupplierRequest(RequestWorkflowContext ctx) {
		log.debug("findSupplierRequest");

		return supplierRequestService.findSupplierRequestFor(ctx.getPatronRequest())
			.flatMap(supplierRequest -> Mono.just(ctx.setSupplierRequest(supplierRequest)))
			.defaultIfEmpty(ctx);
	}

	private Mono<RequestWorkflowContext> decorateWithPatronVirtualIdentity(RequestWorkflowContext ctx) {
		log.debug("decorateWithPatronVirtualIdentity");
		SupplierRequest sr = ctx.getSupplierRequest();

		if (sr != null) {
			// Look up the virtual patron identity and attach to the context
			log.debug("Do we know about a virtual identity for the patron at the supplying system");
			return Mono.from(supplierRequestRepository.findVirtualIdentityById(sr.getId()))
				.flatMap(vi -> {
					log.debug("found virtual identity {}",vi);
						return Mono.just(ctx.setPatronVirtualIdentity(vi));
					})
				.switchIfEmpty(Mono.defer(() -> {
					log.warn("Unable lookup patron virtual identity for supplier request {}",sr.getId());
					return Mono.just(ctx);
				}));
		}
		else {
			log.error("No supplier request to augment");
		}

		return Mono.just(ctx);
	}

	// A Patron request can specify a pickup location - resolve the agency and system for that location given
	// The procedure for turning a pickup location code into an agency code is different to the other kinds of identifiers
	// it is tempting to think that resolving patron and lending systems could be coalesced into a single function, but
	// this is problematic due to the semantic difference. Please think carefully before attempting this (Desireable) consolidation
	private Mono<RequestWorkflowContext> resolvePickupLocationAgency(RequestWorkflowContext ctx) {
		log.debug("resolvePickupLocationAgency");
                // ToDo: WARNING - this is a short term bridge - for the "2-legged" scenario - we default the context of the pickup location code to the
                // System of the patron - assuming the patron will pick up from one of their "Local" libraries. This will NOT work for PUA requests
                // We set pickupSymbolContext to the explicit code in the patron request - if it's not there we fall back to the patrons home system code
                String pickupSymbolContext = ctx.getPatronRequest().getPickupLocationCodeContext();
                if ( pickupSymbolContext == null )
                        pickupSymbolContext = ctx.getPatronRequest().getPatronHostlmsCode();

                String pickupSymbol = ctx.getPatronRequest().getPickupLocationCode();

                return agencyForPickupLocationSymbol(pickupSymbolContext, pickupSymbol)
                        .switchIfEmpty(Mono.error(new RuntimeException("No mapping found for pickup location \""+ctx.getPatronRequest().getPickupLocationCode()+"\""))) 
                        .flatMap(rvm -> { return Mono.from(getDataAgencyWithHostLms(rvm.getToValue())); } )
                        .flatMap(pickupAgency -> { return Mono.just(ctx.setPickupAgency(pickupAgency)); } )
                        .flatMap(ctx2 -> { return Mono.just(ctx2.setPickupAgencyCode(ctx2.getPickupAgency().getCode())); } )
                        .flatMap(ctx2 -> { return Mono.just(ctx2.setPickupSystemCode(ctx2.getPickupAgency().getHostLms().getCode())); } )
                        ;
        }

        private Mono<ReferenceValueMapping> agencyForPickupLocationSymbol(String pickupSymbolNamespace, String symbol) {
                if ( ( pickupSymbolNamespace != null ) && ( symbol != null ) ) {
                        return referenceValueMappingService.findPickupLocationToAgencyMapping(pickupSymbolNamespace,symbol);
                }
                else if ( symbol != null ) {
                        return agencyForPickupLocationSymbol(symbol);
                }

                return Mono.error(new RuntimeException("No pickup location code present"));
        }

        private Mono<ReferenceValueMapping> agencyForPickupLocationSymbol(String symbol) {
                String[] symbol_components = symbol.split(":");
                if ( symbol_components.length == 1 ) {
                        // We have an unscoped pickup location - see if we can hit a uniqie value
                        log.debug("Attempting unscoped location lookup for {}",symbol);
                        return referenceValueMappingService.findPickupLocationToAgencyMapping(symbol_components[0]);
                }
                else if ( symbol_components.length == 2 ) {
                        log.debug("Attempting scoped location lookup for {}",symbol);
                        return referenceValueMappingService.findPickupLocationToAgencyMapping(symbol_components[0], symbol_components[1]);
                }

                return Mono.error(new RuntimeException("Unable to resolve agency for pickup location code"+symbol));
        }

	// Get the agency and get the related HostLMS
	// I know this looks a little macabre. It seems that mn-data can't process the DataHostLms because of the transient getType method
	// this means that the agencyRepo.findHostLmsById approach does't work as expected, so we need to get the agency in a 2 step
	// process where we first grab the ID of the hostLms then we get it by ID
	private Mono<DataAgency> getDataAgencyWithHostLms(String code) {
		log.debug("getDataAgencyWithHostLms");

		return Mono.from(agencyRepository.findOneByCode(code))
			.flatMap(agency -> Mono.from(agencyRepository.findHostLmsIdById(agency.getId()))
			.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.flatMap(hostLms -> {
				agency.setHostLms(hostLms);
				return Mono.just(agency);
			}));
	}

	private Mono<RequestWorkflowContext> report(RequestWorkflowContext ctx) {
		log.debug("ctx agency:{} system:{} hasPatronAgency:{} hasPatronSystem:{} pickupAgency:{} pickupSystem:{} hasPickupAgency:{}",
			ctx.getPatronAgencyCode(),
			ctx.getPatronSystemCode(),
			ctx.getPatronAgency() != null,
			ctx.getPatronSystem() != null,
			ctx.getPickupAgencyCode(),
			ctx.getPickupSystemCode(),
			ctx.getPickupAgency() != null);

		log.debug("      lenderAgency: {}, lenderAgencySystem: {}, hasLenderAgency:{}, hasPatronHomeIdentity:{} hasPatronVirtualIdentity:{} supplierHoldId: {} supplierHoldStatus:{}",
			ctx.getLenderAgencyCode(),
			ctx.getLenderSystemCode(),
			ctx.getLenderAgency() != null,
			ctx.getPatronHomeIdentity() != null,
			ctx.getPatronVirtualIdentity() != null,
			ctx.getSupplierHoldId(),
			ctx.getSupplierHoldStatus());

		return Mono.just(ctx);
	}
}

