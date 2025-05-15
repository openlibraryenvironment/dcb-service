package org.olf.dcb.utils;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import io.micronaut.management.endpoint.info.*;

import java.net.URL;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Grant;
import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.core.model.config.ConfigHostLms;
import org.olf.dcb.storage.GrantRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.StatusCodeRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import jakarta.annotation.PreDestroy;

import org.olf.dcb.core.AppConfig;

import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;

import org.olf.dcb.tracking.PollingConfig;
import org.olf.dcb.core.svc.AlarmsService;


@Slf4j
@Singleton
public class DCBStartupEventListener implements ApplicationEventListener<StartupEvent> {

	private final Environment environment;
	private final HostLmsRepository hostLmsRepository;
	private final StatusCodeRepository statusCodeRepository;
	private final GrantRepository grantRepository;
	private final Collection<ConfigHostLms> configHosts;
	private final HazelcastInstance hazelcastInstance;
	private final AppConfig appConfig;
	private final PollingConfig pollingConfig;
	private final AlarmsService alarmsService;

	@Value("${dcb.env.code:UNKNOWN}")
	String envCode;

	private static final String REACTOR_DEBUG_VAR = "REACTOR_DEBUG";

	public DCBStartupEventListener(Environment environment,
		HostLmsRepository hostLmsRepository, StatusCodeRepository statusCodeRepository,
		GrantRepository grantRepository, List<ConfigHostLms> configHosts,
		HazelcastInstance hazelcastInstance,
		AppConfig appConfig,
		PollingConfig pollingConfig,
    AlarmsService alarmsService) {

		this.environment = environment;
		this.statusCodeRepository = statusCodeRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.grantRepository = grantRepository;
		this.configHosts = configHosts;
		this.hazelcastInstance = hazelcastInstance;
		this.appConfig = appConfig;
		this.pollingConfig = pollingConfig;
		this.alarmsService = alarmsService;
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {

    String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    log.info("Configured system notification endpoints: {}",alarmsService.getEndpoints());

		if (environment.getProperty(REACTOR_DEBUG_VAR, String.class)
				.orElse("false").equalsIgnoreCase("true")) {
			log.info("Switching on operator debug mode");

			Hooks.onOperatorDebug();
		}
		else {
			log.info("operator debug not enabled");
		}

		Hooks.onErrorDropped( error -> {
			logAndReportError(error, Map.of("context","Hooks.OnErrorDropped"));
		});

		Hooks.onOperatorError((error, data) -> {
			logAndReportError(error, Map.of("context","Hooks.OnOperatorError"));
			return error;
		});

		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			logAndReportError(throwable, Map.of("context","Thread.setDefaultUncaughtExceptionHandler"));
			log.error("Uncaught exception in thread " + thread.getName() + ": " + throwable.getMessage());
		});

		final var keycloak_cert_url = environment
			.getProperty("KEYCLOAK_CERT_URL", String.class)
			.orElse(null);

		if (keycloak_cert_url == null) {
			log.error("KEYCLOAK_CERT_URL IS NOT SET. DCB will be unable to authenticate requests. Please fix your config and restart");
		} else {
			log.info("bearer tokens will be validated using {}", keycloak_cert_url);
		}

		// Make these clearly available in the startup log
		log.info("Starting with polling intervals: {}", pollingConfig.getDurations());

		// Enumerate any host LMS entries and create corresponding DB entries
		configHosts.forEach(this::saveConfigHostLms);

		bootstrapStatusCodes();

		registerNode();

    alarmsService.simpleAnnounce(envCode+":DCB Startup completed : "+timestamp)
      .subscribe();

		log.info("Exit onApplicationEvent");
	}

	private void saveConfigHostLms(ConfigHostLms hostLms) {
		log.debug("make sure {}/{}/{} exists in DB", hostLms.getId(), hostLms.getName(), hostLms);

		final var db_representation = DataHostLms.builder()
			.id(hostLms.getId())
			.code(hostLms.getCode())
			.name(hostLms.getName())
			.lmsClientClass(tolerateNoType(hostLms.getType()))
			.ingestSourceClass(tolerateNoType(hostLms.getIngestSourceType()))
			.clientConfig(hostLms.getClientConfig())
			.suppressionRulesetName(hostLms.getSuppressionRulesetName())
			.itemSuppressionRulesetName(hostLms.getItemSuppressionRulesetName())
			.build();

		// we don't want to proceed until this is done
		hostLmsRepository.saveOrUpdate(db_representation).block();
		log.debug("Save complete");
	}

	private static String tolerateNoType(Class<?> type) {
		return Optional.ofNullable(type)
			.map(Class::getName)
			.orElse(null);
	}

	private void bootstrapStatusCodes() {
		log.debug("bootstrapStatusCodes");
		Mono.just ( "start" )
			// Supplier Request - the hold request at the supplying library
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "IDLE", Boolean.FALSE)))
			// II I think the next one should be removed
			// .flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "PLACED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "MISSING", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierRequest", "CONFIRMED", Boolean.TRUE)))

			// Supplier Request - the hold request at the patron library
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "IDLE", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "PLACED", Boolean.TRUE)))
			// II I think the next one should be removed
			// .flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "CANCELLED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("PatronRequest", "MISSING", Boolean.FALSE)))

			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "IDLE", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "RET-TRANSIT", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "AVAILABLE", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "REQUESTED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "LOANED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "PICKUP_TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "HOLDSHELF", Boolean.TRUE)))
			// Another folio oddity - track missing
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "MISSING", Boolean.TRUE)))
			// CREATED is a FOLIO oddity - somehow we are seeing this in the database
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "CREATED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("VirtualItem", "OPEN", Boolean.TRUE)))

			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierItem", "AVAILABLE", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierItem", "LOANED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierItem", "TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("SupplierItem", CanonicalItemState.RECEIVED.toString(), Boolean.TRUE)))

			// The INTERNAL DCB State model - used to progress DCB requests according to OUR state model
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "SUBMITTED_TO_DCB", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "PATRON_VERIFIED", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "RESOLVED", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "NOT_SUPPLIED_CURRENT_SUPPLIER", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "NO_ITEMS_SELECTABLE_AT_ANY_AGENCY", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "REQUEST_PLACED_AT_SUPPLYING_AGENCY", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "REQUEST_PLACED_AT_BORROWING_AGENCY", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "CONFIRMED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "PICKUP_TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "RECEIVED_AT_PICKUP", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "READY_FOR_PICKUP", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "LOANED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "RETURN_TRANSIT", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "CANCELLED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "COMPLETED", Boolean.TRUE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "FINALISED", Boolean.FALSE)))
			.flatMap( v -> Mono.from(saveOrUpdateStatusCode("DCBRequest", "ERROR", Boolean.FALSE)))

			// Grant all permissions on everything to anyone with the ADMIN role (And allow them to pass on grants)
//			.flatMap( v -> Mono.from(saveOrUpdateGrant("%", "%", "%", "%", "role", "ADMIN", Boolean.TRUE)))
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

	/**
	 * Create a distributed map controlled by hazelcast and add this node to the node info structure.
	 */
	private void registerNode() {
		try {
			IMap<String,Map<String,String>> dcbNodeInfo = hazelcastInstance.getMap("DCBNodes");
			String thisNodeUUID = hazelcastInstance.getCluster().getLocalMember().getUuid().toString();

			Map<String,String> nodeInfo = new HashMap();
			nodeInfo.put("name",hazelcastInstance.getName().toString());
			nodeInfo.put("nodeStart",new java.util.Date().toString());
			nodeInfo.put("scheduledTasks", String.valueOf(appConfig.getScheduledTasks().isEnabled()));

			dcbNodeInfo.put(thisNodeUUID, nodeInfo);
		}
		catch ( Exception e ) {
			log.error("problem",e);
		}
	}

	@PreDestroy
	private void preDestroy() {
		try {
			log.info("preDestroy");
			IMap<String,Map<String,String>> dcbNodeInfo = hazelcastInstance.getMap("DCBNodes");
			String thisNodeUUID = hazelcastInstance.getCluster().getLocalMember().getUuid().toString();
			dcbNodeInfo.remove(thisNodeUUID);
		}
		catch ( Exception e ) {
			log.info("Unable to deregister - likely last instance {}",e.getMessage());
			// log.error("pre-destroy problem",e);
			// If this was the last HZ instance, an exception is thrown - lets not pollute the logs as this is OK
		}
	}


	private void logAndReportError(Throwable error, Map<String,String> additional) {
    String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		log.error("Unhandled Reactor exception: {} {}",additional,error);
    alarmsService.simpleAnnounce(envCode+" UNCAUGHT EXCEPTION "+error.getClass().getName()+" @ "+timestamp+": "+error.getMessage()+" "+additional)
     .subscribe();
	}

}
