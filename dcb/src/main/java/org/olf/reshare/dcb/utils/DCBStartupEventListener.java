package org.olf.reshare.dcb.utils;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import reactor.core.publisher.Hooks;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import java.util.UUID;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.olf.reshare.dcb.core.model.HostLms;

@Singleton
public class DCBStartupEventListener implements ApplicationEventListener<StartupEvent> {

	private final Environment environment;
	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;
	private final HostLms[] confHosts;

	private static final String REACTOR_DEBUG_VAR = "REACTOR_DEBUG";

        private static Logger log = LoggerFactory.getLogger(DCBStartupEventListener.class);

	public DCBStartupEventListener(Environment environment, 
                                       AgencyRepository agencyRepository,
                                       HostLmsRepository hostLmsRepository,
                                       HostLms[] confHosts) {
		this.environment = environment;
		this.agencyRepository = agencyRepository;
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


		// log.debug("CREATE");
		// DataHostLms dhl1 = new DataHostLms(UUID.randomUUID(),"test1","test1","",new java.util.HashMap<String,Object>());
		// log.debug("SUBSCRIBE");
		// upsertHostLms(dhl1).subscribe();

		// Enumerate any host LMS entries and create corresponding DB entries
		for ( HostLms hostLms : confHosts ) {
			log.debug("make sure {} exists in DB",hostLms.getId());
			DataHostLms db_representation = new DataHostLms()
								.builder()
								.id(hostLms.getId())
								.code(hostLms.getCode())
								.name(hostLms.getName())
								.lmsClientClass(hostLms.getType().getClass().getName())
								.clientConfig(hostLms.getClientConfig())
								.build();
		} 

		log.info("Exit onApplicationEvent");
	}

        
        private Mono<DataHostLms> upsertHostLms(DataHostLms hostLMS) {
		log.debug("upsertHostLms {}",hostLMS);
                return Mono.from(hostLmsRepository.existsById(hostLMS.getId()))
                        .flatMap(exists -> Mono.fromDirect(exists ? hostLmsRepository.update(hostLMS) : hostLmsRepository.save(hostLMS)));
        }

}
