package org.olf.reshare.dcb.utils;

import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.StatusCode;
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.storage.StatusCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import org.reactivestreams.Publisher;
import services.k_int.utils.UUIDUtils;
import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;


@Singleton
public class DCBStartupEventListener implements ApplicationEventListener<StartupEvent> {

	private final Environment environment;
	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;
	private final StatusCodeRepository statusCodeRepository;
	private final HostLms[] confHosts;

	private static final String REACTOR_DEBUG_VAR = "REACTOR_DEBUG";

        private static Logger log = LoggerFactory.getLogger(DCBStartupEventListener.class);

	public DCBStartupEventListener(Environment environment, 
                                       AgencyRepository agencyRepository,
                                       HostLmsRepository hostLmsRepository,
                                       StatusCodeRepository statusCodeRepository,
                                       HostLms[] confHosts) {
		this.environment = environment;
		this.agencyRepository = agencyRepository;
		this.statusCodeRepository = statusCodeRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.confHosts = confHosts;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		log.info("Bootstrapping DCB - onApplicationEvent");

		if ( environment.getProperty(REACTOR_DEBUG_VAR, String.class).orElse("false").equalsIgnoreCase("true") ) {
			log.info("Switching on operator debug mode");
			Hooks.onOperatorDebug();
		}
		else {
			log.info("operator debug not enabled");
		}

                String keycloak_cert_url = environment.getProperty("KEYCLOAK_CERT_URL", String.class).orElse(null);
                if ( keycloak_cert_url == null ) {
                        log.error("KEYCLOAK_CERT_URL IS NOT SET. DCB will be unable to authenticate requests. Please fix your config and restart");
                }
                else {
                        log.info("bearer tokens will be validated using {}",keycloak_cert_url);
                }

		// log.debug("CREATE");
		// DataHostLms dhl1 = new DataHostLms(UUID.randomUUID(),"test1","test1","",new java.util.HashMap<String,Object>());
		// log.debug("SUBSCRIBE");
		// upsertHostLms(dhl1).subscribe();

		// Enumerate any host LMS entries and create corresponding DB entries
		for ( HostLms hostLms : confHosts ) {
			log.debug("make sure {}/{} exists in DB",hostLms.getId(),hostLms.getName());
			DataHostLms db_representation = new DataHostLms()
								.builder()
								.id(hostLms.getId())
								.code(hostLms.getCode())
								.name(hostLms.getName())
								.lmsClientClass(hostLms.getType().getName())
								.clientConfig(hostLms.getClientConfig())
								.build();

			// we don't want to proceed until this is done
			upsertHostLms(db_representation).block(); // subscribe( i -> log.info("Saved hostlms {}",i.getId()) );
			log.debug("Save complete");
		} 
                bootstrapStatusCodes();

		log.info("Exit onApplicationEvent");
	}
	private Mono<DataHostLms> upsertHostLms(DataHostLms hostLMS) {
		log.debug("upsertHostLms {}",hostLMS);
  		return Mono.from(hostLmsRepository.existsById(hostLMS.getId()))
               .flatMap(exists -> Mono.fromDirect(exists ? hostLmsRepository.update(hostLMS) : hostLmsRepository.save(hostLMS)));
	}

	private void bootstrapStatusCodes() {
		log.debug("bootstrapStatusCodes");
		Mono.just ( "start" )
			.flatMap( v -> { return Mono.from(saveOrUpdateStatusCode("SupplierRequest", "IDLE", Boolean.FALSE)); } )
			.flatMap( v -> { return Mono.from(saveOrUpdateStatusCode("SupplierRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", Boolean.TRUE)); } )
			.flatMap( v -> { return Mono.from(saveOrUpdateStatusCode("SupplierRequest", "PLACED", Boolean.TRUE)); } )
			.flatMap( v -> { return Mono.from(saveOrUpdateStatusCode("SupplierRequest", "MISSING", Boolean.FALSE)); } )
			.flatMap( v -> { return Mono.from(saveOrUpdateStatusCode("PatronRequest", "IDLE", Boolean.FALSE)); } )
			.subscribe();
	}

	private Publisher<StatusCode> saveOrUpdateStatusCode(String model, String code, Boolean tracked) {
		StatusCode sc = new StatusCode(
					UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "StatusCode:"+model+":"+code),
					model,
					code,
					null,
					tracked);
		log.debug("upsert {}",sc);
		return statusCodeRepository.saveOrUpdate(sc);
	}
}
