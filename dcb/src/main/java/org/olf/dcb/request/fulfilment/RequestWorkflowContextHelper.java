package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class RequestWorkflowContextHelper {
	private final SupplierRequestService supplierRequestService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;

	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestRepository patronRequestRepository;
	private final AgencyRepository agencyRepository;

	private final LocationService locationService;
	private final HostLmsService hostLmsService;
	private final PatronService patronService;

	public RequestWorkflowContextHelper(
		SupplierRequestService supplierRequestService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestRepository patronRequestRepository,
		AgencyRepository agencyRepository, LocationService locationService,
		HostLmsService hostLmsService, PatronService patronService) {

		this.supplierRequestService = supplierRequestService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestRepository = patronRequestRepository;
		this.agencyRepository = agencyRepository;
		this.locationService = locationService;
		this.hostLmsService = hostLmsService;
		this.patronService = patronService;
	}

	// Given a patron request, construct the patron request context containing all related objects for a workflow
	public Mono<RequestWorkflowContext> fromPatronRequest(PatronRequest pr) {
		RequestWorkflowContext rwc = new RequestWorkflowContext();
		log.info("fromPatronRequest {}", pr.getId());

		return  Mono.just(rwc.setPatronRequest(pr))
		.flatMap(this::decorateWithPatronRequestStateOnEntry)
		.flatMap(this::decorateWithPatron)
		.flatMap(this::findSupplierRequest)
		.flatMap(this::decorateWithPatronVirtualIdentity)
		.flatMap(this::decorateContextWithPatronDetails)
		.flatMap(this::decorateContextWithLenderDetails)
		.flatMap(this::resolvePickupLocationAgency)
		.flatMap(this::report);
	}

	private Mono<RequestWorkflowContext> decorateWithPatronRequestStateOnEntry(RequestWorkflowContext requestWorkflowContext) {
		requestWorkflowContext.patronRequestStateOnEntry = requestWorkflowContext.getPatronRequest().getStatus();
		return Mono.just(requestWorkflowContext);
	}

	// Given a supplier request, construct the patron request context containing all related objects for a workflow
	public Mono<RequestWorkflowContext> fromSupplierRequest(SupplierRequest sr) {
		RequestWorkflowContext rwc = new RequestWorkflowContext();
		log.info("fromSupplierRequest {}",sr.getId());

		return Mono.just( rwc.setSupplierRequest(sr) )
			.flatMap(rwcp -> Mono.from(supplierRequestRepository.findPatronRequestById(rwc.getSupplierRequest().getId())))
			.flatMap(pr -> Mono.just(rwc.setPatronRequest(pr)))
			.flatMap(this::decorateWithPatronRequestStateOnEntry)
			.flatMap(this::decorateWithPatron)
			.flatMap(this::decorateWithPatronVirtualIdentity)
			.flatMap(this::decorateContextWithPatronDetails)
			.flatMap(this::decorateContextWithLenderDetails)
			.flatMap(this::resolvePickupLocationAgency)
			.flatMap(this::report);
	}

	private Mono<RequestWorkflowContext> decorateWithPatron(
		RequestWorkflowContext requestWorkflowContext) {

		// we get the patron id but nothing else on the patron from the patron request.
		final var patronId = PatronId.fromPatron(requestWorkflowContext.getPatronRequest().getPatron());

		return patronService.findById(patronId)
			.map(requestWorkflowContext::setPatron);
	}

	private Mono<RequestWorkflowContext> decorateContextWithLenderDetails(RequestWorkflowContext ctx) {
		log.info("decorateContextWithLenderDetails");

		if ( ctx.getSupplierRequest() != null ) {
			ctx.setLenderAgencyCode(ctx.getSupplierRequest().getLocalAgency());
			ctx.setLenderSystemCode(ctx.getSupplierRequest().getHostLmsCode());
		}

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
			return patronService.findResolvedAgencyByIdentity(ctx.getPatronHomeIdentity())
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

		if ( ctx.getPatronAgency() == null ) {
			// Revisit after v1
			// The tests will pass through here and expect no agecy to be OK because their fidelity is so low. We should really exit here with alarm bells rining
			log.error("Establishing a workflow context without a patron agency -- TEST ONLY BEHAVIOUR.");
			return Mono.just(ctx);
		}

		// There is a problem here - as per getDataAgencyWithHostLms agencyRepository.findHostLmsById doesn't work directly
		return Mono.from(agencyRepository.findHostLmsIdById(ctx.getPatronAgency().getId()))
			.flatMap(hostLmsService::findById)
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
		log.info("resolvePickupLocationAgency ctx={} code={} hostLms={}",
			ctx.getPatronRequest().getPickupLocationCodeContext(),
			ctx.getPatronRequest().getPickupLocationCode(),
			ctx.getPatronRequest().getPatronHostlmsCode());

		// ToDo: WARNING - this is a short term bridge - for the "2-legged" scenario - we default the context of the pickup location code to the
		// System of the patron - assuming the patron will pick up from one of their "Local" libraries. This will NOT work for PUA requests
		// We set pickupSymbolContext to the explicit code in the patron request - if it's not there we fall back to the patrons home system code
		String pickupSymbolContext = ctx.getPatronRequest().getPickupLocationCodeContext();

		if ( pickupSymbolContext == null )
			pickupSymbolContext = ctx.getPatronRequest().getPatronHostlmsCode();

		String pickupSymbol = ctx.getPatronRequest().getPickupLocationCode();

		if ( pickupSymbol == null ) {
			// Should not happen in normal operation but can happen in tests :(
			log.error("pickupSymbol is null");
			return Mono.just(ctx);
		}

		if ( pickupSymbol.length() == 36 ) {
			// We've been passed a UUID in the pickup location symbol... try to unpick that
			// Convert the location UUID into a location, extract the code, find the agency for that code and context
			return locationService.findById(pickupSymbol)
					.flatMap(this::getAgencyDirectlyFromLocation)
					// We no longer go via the mapping table for pickup locations - the Location row in the DB MUST be directly attached to an agency
					// .flatMap( loc -> { return Mono.from(agencyForPickupLocationSymbol(pcs2, loc.getCode())); } )
					// .flatMap( rvm -> { return Mono.from(getDataAgencyWithHostLms(rvm.getToValue())); } )
				.flatMap(pickupAgency -> Mono.just(ctx.setPickupAgency(pickupAgency)))
				.flatMap(ctx2 -> Mono.just(ctx2.setPickupAgencyCode(ctx2.getPickupAgency().getCode())))
				.flatMap(ctx2 -> Mono.just(ctx2.setPickupSystemCode(ctx2.getPickupAgency().getHostLms().getCode())))
					.switchIfEmpty(Mono.error(new RuntimeException("No agency found for pickup location: %s".formatted(pickupSymbol))));
		}

		return agencyForPickupLocationSymbol(pickupSymbolContext, pickupSymbol)
			.switchIfEmpty(Mono.error(new RuntimeException("RWCH No mapping found for pickup location \""+pickupSymbolContext+":"+pickupSymbol+"\""))) 
			.flatMap(rvm -> Mono.from(getDataAgencyWithHostLms(rvm.getToValue())))
			.flatMap(pickupAgency -> Mono.just(ctx.setPickupAgency(pickupAgency)))
			.flatMap(ctx2 -> Mono.just(ctx2.setPickupAgencyCode(ctx2.getPickupAgency().getCode())))
			.flatMap(ctx2 -> Mono.just(ctx2.setPickupSystemCode(ctx2.getPickupAgency().getHostLms().getCode())))
			.switchIfEmpty(Mono.error(new RuntimeException("No agency found for pickup location: %s:%s".formatted(pickupSymbolContext,pickupSymbol))));
	}

	// If an agency has been directly attached to the location then return it by just walking the model
	private Mono<DataAgency> getAgencyDirectlyFromLocation(Location l) {
		return Mono.just(l.getAgency())
			.flatMap ( agency -> Mono.just(agency.getId()) )
			.flatMap ( agencyId -> Mono.from(agencyRepository.findById(agencyId)));
	}

	// Attempt to look up a data agency using the pickup location mappings instead of a direct reference
	private Mono<DataAgency> getAgencyFromPickupLocationMappingTable(String pickupSymbolContext, String pickupSymbol) {
		return agencyForPickupLocationSymbol(pickupSymbolContext, pickupSymbol)
      .switchIfEmpty(Mono.error(new RuntimeException("RWCH No mapping found for pickup location \""+pickupSymbolContext+":"+pickupSymbol+"\"")))
      .flatMap(rvm -> { return Mono.from(getDataAgencyWithHostLms(rvm.getToValue())); } );
	}

	private Mono<ReferenceValueMapping> agencyForPickupLocationSymbol(String pickupSymbolNamespace, String symbol) {
		if ( ( pickupSymbolNamespace != null ) && ( symbol != null ) ) {
      return locationToAgencyMappingService.findLocationToAgencyMapping(pickupSymbolNamespace,symbol);
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
                        return locationToAgencyMappingService.findLocationToAgencyMapping(symbol_components[0]);
                }
                else if ( symbol_components.length == 2 ) {
                        log.debug("Attempting scoped location lookup for {}",symbol);
                        return locationToAgencyMappingService.findLocationToAgencyMapping(symbol_components[0], symbol_components[1]);
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
			.flatMap(hostLmsService::findById)
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

