package services.k_int.jobs;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.olf.dcb.storage.JobCheckpointRepository;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.jobs.JobChunkProcessor.ApplicableChunkTypes;

/**
 * Responsible for processing {@link JobChunk} instances obtained from a {@link Job} instance.
 * This class manages the saving of the provided Checkpoint value and uses that to obtain the "next"
 * chunk of data. Failure in processing results in the checkpoint not being saved, ensuring proper resumption.  
 * 
 * @author Steve Osguthorpe
 */
@Slf4j
@Singleton
public class ReactiveJobRunnerService {
	
	private final Map<Class<? extends JobChunk<?>>, JobChunkProcessor> processorsCache = new ConcurrentHashMap<>();

	private final JobCheckpointRepository checkpoints;
	private final BeanContext context;
	
	public ReactiveJobRunnerService(JobCheckpointRepository checkpoints, BeanContext context) {
		this.checkpoints = checkpoints;
		this.context = context;
	}
	
	private <T extends JobChunk<?>> Predicate<BeanDefinition<JobChunkProcessor>> processorApplicableFor( final Class<T> type ) {
		return beanDef -> {
			return Stream.of( beanDef.classValues(ApplicableChunkTypes.class) )
				.anyMatch( type::equals );
		};
	}
	
	@NonNull
	private <T extends JobChunk<?>> JobChunkProcessor getProcessorForChunkType( Class<T> type ) {
		if (processorsCache.containsKey(type)) {
			var theBean = processorsCache.get(type);
			log.debug("Returning processor [{}] from cache", theBean);
			return theBean;
		}

		var chunkProcessorBeanDef = context.getBeanDefinitions(JobChunkProcessor.class, Qualifiers.byStereotype( ApplicableChunkTypes.class ))
			.stream()
			.filter( processorApplicableFor( type ) )
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No [JobChunkProcessor] instance found annotated with [ApplicableChunkTypes] for [%s]".formatted(type)) );
		
		var theBean = context.getBean(chunkProcessorBeanDef);
		
		if ( chunkProcessorBeanDef.isSingleton() ) {

			log.debug("Cacheing singelton processor [{}]", theBean);
			processorsCache.put(type, theBean);
		}
		
		log.debug("Created processor instance [{}], for type [{}]", theBean, type);
		
		return theBean;
	}
	
	@Transactional(readOnly = true)
	protected <T extends Job<?>> Mono<Optional<JsonNode>> readLastCheckpointData ( final T job ) {
		return Mono.from( checkpoints.findCheckpointByJobId( job.getId() ))
			.map( Optional::of )
			.defaultIfEmpty( Optional.empty() );
	}
	
	/**
	 * Begin/Resume the supplied {@link Job}
	 * 
	 * @param <CT> Type of resource represented by the {@link JobChunk}s provided by the supplied jobInstance.
	 * @param jobInstance The job to begin/resume
	 * @return Publisher of chunks processed
	 */
	public <CT> Flux<JobChunk<CT>> processJobInstance ( final Job<CT> jobInstance ) {
		return Mono.defer( () -> readLastCheckpointData(jobInstance) )
			.flatMap( checkPoint -> {
				final var pub = checkPoint.isPresent() ? jobInstance.resume(checkPoint.get()) : jobInstance.start();
				return Mono.from(pub);
			})
			.expand( chunk -> {
				
				return processChunkAndSaveCheckpoint(chunk)
					.flatMap(theChunk -> {
						if (theChunk.isLastChunk()) {
							log.info("Ending job run as chunk was marked as the last one");
							return Mono.empty();
						}
						final var checkpoint = theChunk.getCheckpoint();
						return Mono.from( jobInstance.resume( checkpoint ) )
								.doOnNext( cp -> log.trace("Get next chunk using checkpoint [{}]", cp));
					});
				
//				if (chunk.isLastChunk()) {
//					log.info("Ending job run as chunk was marked as the last one");
//					return Mono.empty();
//				}
//				
//				final var checkpoint = chunk.getCheckpoint();
//				return Mono.from( jobInstance.resume( checkpoint ) )
//						.doOnNext( cp -> log.trace("Get next chunk using checkpoint [{}]", cp));
			});
	}

	@Transactional(readOnly = true)
	protected Flux<JobInstanceProvider> fetchAllProviderData( List<JobInstanceProvider> providers ) {
		return Flux.fromIterable(providers);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected <CT> Mono<JobChunk<CT>> processChunkAndSaveCheckpoint( JobChunk<CT> chunk ) {
		
		// Ensure we have a job processor
		JobChunkProcessor processor = getProcessorForChunkType( chunk.getClass() );
		
		// Save publisher 
		Mono<JsonNode> saveCheckpoint = Mono.from(checkpoints.saveCheckpointForJobId( chunk.getJobId(), chunk.getCheckpoint() ))
				.doOnSuccess( savedCheckpoint -> log.info("Save checkpoint [{}]", savedCheckpoint.getValue()));
		
		return Mono.from(processor.processChunk(chunk))
			.then( saveCheckpoint )
			.thenReturn(chunk);
	}
}
