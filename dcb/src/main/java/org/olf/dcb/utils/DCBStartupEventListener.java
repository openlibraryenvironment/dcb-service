package org.olf.dcb.utils;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Grant;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.core.model.config.ConfigHostLms;
import org.olf.dcb.storage.GrantRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.StatusCodeRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;


@Slf4j
@Singleton
public class DCBStartupEventListener implements ApplicationEventListener<StartupEvent> {
	private final Environment environment;
	private final HostLmsRepository hostLmsRepository;
	private final StatusCodeRepository statusCodeRepository;
	private final GrantRepository grantRepository;
	private final ConfigHostLms[] configHosts;

	private static final String REACTOR_DEBUG_VAR = "REACTOR_DEBUG";

	public DCBStartupEventListener(Environment environment,
		HostLmsRepository hostLmsRepository, StatusCodeRepository statusCodeRepository,
		GrantRepository grantRepository, ConfigHostLms[] configHosts) {

		this.environment = environment;
		this.statusCodeRepository = statusCodeRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.grantRepository = grantRepository;
		this.configHosts = configHosts;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		log.info("Bootstrapping DCB - onApplicationEvent");

		if (environment.getProperty(REACTOR_DEBUG_VAR, String.class)
				.orElse("false").equalsIgnoreCase("true")) {
			log.info("Switching on operator debug mode");
			Hooks.onOperatorDebug();
		}
		else {
			log.info("operator debug not enabled");
		}

		final var keycloak_cert_url = environment
			.getProperty("KEYCLOAK_CERT_URL", String.class)
			.orElse(null);

		if (keycloak_cert_url == null) {
			log.error("KEYCLOAK_CERT_URL IS NOT SET. DCB will be unable to authenticate requests. Please fix your config and restart");
		} else {
			log.info("bearer tokens will be validated using {}", keycloak_cert_url);
		}

		// Enumerate any host LMS entries and create corresponding DB entries
		for (HostLms hostLms : configHosts) {
			log.debug("make sure {}/{}/{} exists in DB",hostLms.getId(),hostLms.getName(),hostLms);

			final var db_representation = DataHostLms.builder()
				.id(hostLms.getId())
				.code(hostLms.getCode())
				.name(hostLms.getName())
				.lmsClientClass(hostLms.getType().getName())
				.clientConfig(hostLms.getClientConfig())
				.build();

			// we don't want to proceed until this is done
			upsertHostLms(db_representation).block();
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
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "IDLE", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "PLACED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "MISSING", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "IDLE", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "PLACED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "IDLE", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "RET-TRANSIT", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "AVAILABLE", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "LOANED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "PICKUP_TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "HOLDSHELF", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "MISSING", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierItem", "TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierItem", "RECEIEVED", Boolean.TRUE)))
			// Grant all permissions on everything to anyone with the ADMIN role (And allow them to pass on grants)
			.flatMap( v -> Mono.from(saveOrUpdateGrant("%", "%", "%", "%", "role", "ADMIN", Boolean.TRUE)))
			.subscribe();
	}

	private Publisher<Grant> saveOrUpdateGrant( String resourceOwner,
		String resourceType, String resourceId, String grantedPerm,
		String granteeType, String grantee, Boolean grantOption) {

		final var g = Grant.builder()
			.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Grant:" + resourceOwner + ":" +
				resourceType + ":" + resourceId + ":" + grantedPerm + ":" + granteeType + ":" + grantee + ":" + grantOption))
			.grantResourceOwner(resourceOwner)
			.grantResourceType(resourceType)
			.grantResourceId(resourceId)
			.grantedPerm(grantedPerm)
			.granteeType(granteeType)
			.grantee(grantee)
			.grantOption(grantOption)
			.build();

		return grantRepository.saveOrUpdate(g);
	}

	private Publisher<StatusCode> saveOrUpdateStatusCode(String model, String code, Boolean tracked) {
		final var statusCode = StatusCode.builder()
			.id(nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "StatusCode:" + model + ":" + code))
			.model(model)
			.code(code)
			.tracked(tracked)
			.build();

		log.debug("upsert {}", statusCode);

		return statusCodeRepository.saveOrUpdate(statusCode);
	}
}
