package services.k_int.jobs;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.tree.JsonNode;

/**
 * Represents a single processable "chunk" of data to be processed as a single operation.
 * 
 * @author Steve Osguthorpe
 * @param <T> The type of items collected and represented by this implementation.
 */
public interface JobChunk<T> extends Iterable<T> {

	@NonNull
	UUID getJobId();
	
	@NonNull
	Collection<T> getData();
	
	@NonNull
  JsonNode getCheckpoint();
	
	boolean isLastChunk();
  
  @NonNull
  default int getSize() {
  	return getData().size();
  }
  
  @NonNull
  @Override
  default Iterator<T> iterator() {
  	return getData().iterator();
  }
}
