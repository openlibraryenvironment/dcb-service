package services.k_int.jobs;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.reactivestreams.Publisher;

import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;


/**
 * Interface representing a class that is capable of processing a chunk of data for a Job.
 * 
 * The class are required to also be qualified with using the {@link ApplicableChunkTypes} annotation that
 * details the concrete type of chunks that the processor supports.
 * 
 * {@code 
 * @ApplicableChunkTypes(ChunkType.class)
 * public class SomeJobProcessor implements JobChunkProcessor {
 * 
 *   @Override
 *   @Transactional(propagation = Propagation.MANDATORY)
 *   public <T> Publisher<JobChunk<T>> processChunk(JobChunk<T> chunk) {
 *     ChunkType ct = (ChunkType)chunk;
 *     // Do your work. 
 *   }
 * }
 * 
 * @author Steve Osguthorpe
 */
public interface JobChunkProcessor {
	
	@Singleton
	@Qualifier
	@Retention(RetentionPolicy.RUNTIME)
	public @interface ApplicableChunkTypes {
    Class<? extends JobChunk<?>>[] value();
	}

	/**
	 * Return a publisher that handles the processing of the supplied chunk.
	 * Any error propagated will fail the entire chunk, and prevent the job runner
	 * from moving onto another chunk. If you want to gracefully drop single resources on error
	 * you should capture those and not allow them to propagate downstream.
	 * 
	 * @param <T> The type of resource this chunk is a collection of.
	 * @param chunk The chunk to be processed.
	 * @return Publisher of processed resources. 
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> Publisher<JobChunk<T>> processChunk ( JobChunk<T> chunk ); 
}
