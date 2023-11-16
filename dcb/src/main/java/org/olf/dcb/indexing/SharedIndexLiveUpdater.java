package org.olf.dcb.indexing;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

@Requires(bean = SharedIndexService.class)
@ExecuteOn(TaskExecutors.BLOCKING)
@Singleton
public class SharedIndexLiveUpdater implements ApplicationEventListener<StartupEvent>, EntityEventListener<ClusterRecord> {
	
	private final SharedIndexService sharedIndexService;
	
	private static final Logger log = LoggerFactory.getLogger(SharedIndexLiveUpdater.class);
	
	public SharedIndexLiveUpdater(SharedIndexService sharedIndexService) {
		this.sharedIndexService = sharedIndexService;
	}

	@Override
	public void postPersist(@NonNull EntityEventContext<ClusterRecord> context) {
		sharedIndexService.add(context.getEntity().getId());
	}

	@Override
	public void postUpdate(@NonNull EntityEventContext<ClusterRecord> context) {
		sharedIndexService.update(context.getEntity().getId());
	}

	@Override
	public void postRemove(@NonNull EntityEventContext<ClusterRecord> context) {
		sharedIndexService.delete(context.getEntity().getId());
	}

	@Override
	public void onApplicationEvent(StartupEvent event) {
		log.debug("Initializing shared index service...");
		sharedIndexService.initialize();
	}
	
}
