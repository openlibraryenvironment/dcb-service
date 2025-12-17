package org.olf.dcb;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;

@Slf4j
@TypeHint(value = { Instant[].class, ZonedDateTime[].class, URI[].class, URL[].class })
@OpenAPIDefinition(info = @Info(title = "DCB", description = "Direct Consortial Borrowing Service", version = "1.0.0"))
public class Application {

  private static final int BACKGROUND_POOL_QUEUE_PER_THREAD = 100_000;
	private static final int BACKGROUND_POOL_COEFFICIENT = 10;

	// Ian: Comment this out unless you know what you're up to
  // static {
  //   BlockHound.install();
  // }

	public static void main(String[] args) {
		
		
		defaultReactorSystemProps( args );
		// Run the full application after.
		Micronaut.run(Application.class, args);
	}
	
	public static void defaultReactorSystemProps (String[] args) {
		final var appContext = Micronaut.build(args)
				.classes(Application.class)
				.bootstrapEnvironment(false);
			
		// Start the environment only, and stop it afterwards.
		// We need this in order to resolve properties from the various
		// places.
		final var env = appContext.build().getEnvironment().start();
		
		// Virtual threads should be used if the JVM is 21+
		final int majorVersion = Runtime.version().feature();
		final boolean hasVirtualThreadSupport = majorVersion >= 21;
		
		// We now allow for a scaling factor to be explicitly set.
		// Default to the number of processors, with a min of 2. 
		final int scalingFactor = env.get("scaling.factor", Integer.class, 
			Math.max(Runtime.getRuntime().availableProcessors(), 2));
		
		
		final int backgroundThreadPoolSize = scalingFactor * BACKGROUND_POOL_COEFFICIENT;
		final int backgroundThreadPoolQueueSize = scalingFactor * BACKGROUND_POOL_COEFFICIENT * BACKGROUND_POOL_QUEUE_PER_THREAD;
		final int threadPoolSize = scalingFactor;
		
		System.setProperty("reactor.schedulers.defaultPoolSize", "" + threadPoolSize);
		System.setProperty("reactor.schedulers.defaultBoundedElasticSize", "" + backgroundThreadPoolSize);
		System.setProperty("reactor.schedulers.defaultBoundedElasticQueueSize", "" + backgroundThreadPoolQueueSize);
		System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", Boolean.toString( hasVirtualThreadSupport ));

		log.info("Using scale factor of [{}]", scalingFactor);
		log.info("Computed threadPoolSize: [{}]", threadPoolSize);
		log.info("Computed backgroundThreadPoolSize: [{}]", backgroundThreadPoolSize);
		log.info("Computed backgroundThreadPoolQueueSize: [{}]", backgroundThreadPoolQueueSize);
		log.info("Computed virtualThreads: [{}]", hasVirtualThreadSupport);

		log.trace("Constant [DEFAULT_BOUNDED_ELASTIC_SIZE] equals [{}]", Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE);
		log.trace("Constant [DEFAULT_BOUNDED_ELASTIC_QUEUESIZE] equals [{}]", Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE);
		log.trace("Constant [DEFAULT_BOUNDED_ELASTIC_ON_VIRTUAL_THREADS] equals [{}]", Schedulers.DEFAULT_BOUNDED_ELASTIC_ON_VIRTUAL_THREADS);
		log.trace("Constant [DEFAULT_POOL_SIZE] equals [{}]", Schedulers.DEFAULT_POOL_SIZE);

		env.stop();
	}
}
