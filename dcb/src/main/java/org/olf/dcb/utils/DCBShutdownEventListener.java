package org.olf.dcb.utils;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static services.k_int.utils.UUIDUtils.nameUUIDFromNamespaceAndString;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Grant;
import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.core.model.config.ConfigHostLms;
import org.olf.dcb.storage.GrantRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.StatusCodeRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import jakarta.annotation.PreDestroy;

import org.olf.dcb.ingest.IngestService;

import org.olf.dcb.core.AppConfig;
import org.olf.dcb.core.AppState;
import org.olf.dcb.core.AppState.AppStatus;
import io.micronaut.context.annotation.Value;

@Slf4j
@Singleton
public class DCBShutdownEventListener implements ApplicationEventListener<ApplicationShutdownEvent> {

	private final Environment environment;
	private final HazelcastInstance hazelcastInstance;
	private final AppConfig appConfig;
	private final IngestService ingestService;
	private final AppState appState;
	private final Long maxWaitTime;

	public DCBShutdownEventListener(Environment environment,
		HazelcastInstance hazelcastInstance,
		AppConfig appConfig,
		IngestService ingestService,
		AppState appState,
		@Value("${dcb.shutdown.maxwait:0}") Long maxWaitTime) {
		this.environment = environment;
		this.hazelcastInstance = hazelcastInstance;
		this.appConfig = appConfig;
		this.ingestService = ingestService;
		this.appState = appState;
		this.maxWaitTime = maxWaitTime;
	}

	@Override
	public void onApplicationEvent(ApplicationShutdownEvent event) {
		log.info("Shutdown DCB - onApplicationEvent");

		// Allow other services to know that we are shutting down
		appState.setRunStatus(AppStatus.SHUTTING_DOWN);

		long shutdownStartTime = System.currentTimeMillis();
		while ( 
			ingestService.isIngestRunning() &&
			( ( System.currentTimeMillis() - shutdownStartTime ) < maxWaitTime.longValue() ) ) {
			log.debug("Waiting for ingest to end");
			try { Thread.sleep(1000); }		catch ( Exception e ) { }
		}

		// https://github.com/micronaut-projects/micronaut-core/issues/2664 suggests that this is the place to signal
		// our services to gracefully terminate any in-flight proceses - in particular to not fetch any more data and wait for
		// the mutex on IngestService to indicate that the running task has cleanly shut down.
		if ( ingestService.isIngestRunning() ) {
			log.warn("INGEST IS RUNNING AT SHUTDOWN TIME");
		}

		log.info("Exit onApplicationEvent (SHUTDOWN)");
	}

}