package services.k_int.jobs;

import java.util.UUID;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.json.tree.JsonNode;
import services.k_int.utils.UUIDUtils;

/**
 * Represents a class that produces chunks of data based on a "resumption" checkpoint object  
 * 
 * @author Steve Osguthorpe
 * @param <T> The type of resource this job produces chunks of.
 */
public interface Job<T> {
	
	final static UUID NS = UUIDUtils.nameUUIDFromNamespaceAndString(UUIDUtils.NAMESPACE_DNS, "k-int.jobs");
	
	default UUID getId() {
		return UUIDUtils.nameUUIDFromNamespaceAndString(NS, getName());
	}
	
	/**
	 * Save/Updates to the checkpoint are based on a UUID 5 generated from this value by default,
	 * and so the name must represent the job instance uniquely.
	 * 
	 * @return A unique name for this job instance.
	 */
	@NonNull
	String getName();
	
	/**
	 * Produce the corresponding "next" chunk of data based on the supplied Checkpoint.
	 * @param lastCheckpoint JSON representation of a resumption token or "checkpoint"
	 * @return Publisher (single) of a JobChunk.
	 */
	@SingleResult
	Publisher<JobChunk<T>> resume ( JsonNode lastCheckpoint );
	
	/**
	 * Produce the first chunk of data (No checkpoint).
	 * @return Publisher (single) of a JobChunk.
	 */
	@SingleResult
	Publisher<JobChunk<T>> start();
}
