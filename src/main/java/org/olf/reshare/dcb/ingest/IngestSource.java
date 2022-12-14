package org.olf.reshare.dcb.ingest;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import services.k_int.utils.UUIDUtils;

public interface IngestSource extends Function<Instant, Publisher<IngestRecord>> {
	
	/**
	 * Take in an Instant representing the point in time to use as the changed since
	 * and return a publisher of IngestRecords
	 *
	 * @param changedSince Instant representing the point in time for the delta
	 * @return A Publisher of IngestRecords 
	 */
	@Override
	Publisher<IngestRecord> apply(@Nullable Instant changedSince);
	
	static final Map<Class<? extends IngestSource>, UUID> namespaces = new HashMap<>();
	
	default <T extends IngestSource> UUID getUUID5ForId( @NotNull final String nativeId ) {
		
		Class<? extends IngestSource> clazz = this.getClass();
		
		// Get from the cache
		UUID namespace = namespaces.get(clazz);
		if (namespace == null) {
			namespace = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, clazz.getSimpleName());
			namespaces.put(clazz, namespace);
		}
		
		// Generate the ID.
		return UUIDUtils.nameUUIDFromNamespaceAndString(namespace, nativeId);
	}
}
