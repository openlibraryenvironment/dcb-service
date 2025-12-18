package services.k_int.micronaut;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;
//import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
//import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
//import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
//import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Singleton
public class PublisherTransformationService {

	private final BeanContext beanContext;
//  private final Supplier<ExecutorService> blockingExecutor;
	
	private static Logger log = LoggerFactory.getLogger(PublisherTransformationService.class);

	public PublisherTransformationService(@NonNull BeanContext beanContext,
			@Named(TaskExecutors.BLOCKING) BeanProvider<ExecutorService> blockingExecutor) {
		this.beanContext = beanContext;
//		this.blockingExecutor = SupplierUtil.memoized(blockingExecutor::get);
	}

	@NonNull
	public <T> Publisher<T> applyTransformations(@NonNull String type, @NonNull Publisher<T> pub) {
		Function<Publisher<T>, Publisher<T>> chain = getTransformationChain(type);
		return chain.apply(pub);
	}

	@SuppressWarnings("unchecked")
	@NonNull
	public <T> Function<Publisher<T>, Publisher<T>> getTransformationChain(String type) {
		log.info("getTransformationChain {}", type);

		var hooks = beanContext.getBeansOfType(PublisherTransformation.class, Qualifiers.byName(type));
		Function<Publisher<T>, Publisher<T>> chain = (pub) -> pub;
		for (var hook : hooks) {
			log.info("Adding publisher transformation {} : {}", type, hook.getClass().getName());
			chain = chain.andThen(hook::apply);
		}

		return chain::apply;
	}
	
//	private Scheduler blockingScheduler;
//	public Scheduler getBlockingScheduler() {
//		if (blockingScheduler == null) {
//			synchronized (blockingExecutor) {
//				if (blockingScheduler == null) {
//					blockingScheduler = Schedulers.fromExecutorService(blockingExecutor.get());
//				}
//			}
//		}
//		return blockingScheduler;
//	}

	public<T> Publisher<T> executeOnBlockingThreadPool ( @NonNull Publisher<T> pub ) {
		
		var f = Mono.just( pub )
			.publishOn(Schedulers.boundedElastic())
			.flatMapMany( Function.identity() )
			.subscribeOn(Schedulers.boundedElastic());
		
		if ( Publishers.isSingle(pub.getClass()) ) {
			return f.singleOrEmpty();
		}
		
		return f;
	}
}
