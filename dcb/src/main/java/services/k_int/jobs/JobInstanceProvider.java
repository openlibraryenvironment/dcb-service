package services.k_int.jobs;

import java.util.List;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;

/**
 * Provides {@link Job} instances  
 * 
 * @author Steve Osguthorpe
 */
public interface JobInstanceProvider {
	
	@NonNull
	List<Class<? extends Job<?>>> getProvidedTypes();
	
	@NonNull
	<T extends Job<?>> Publisher<T> getJobInstancesForType(@NonNull Class<T> type);
	
	@NonNull
	@SingleResult
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Publisher<JobChunk<T>> processChunk( JobChunk<T> chunk );
}
