package org.olf.dcb.core.events;

import java.time.Instant;

import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class RulesetCacheInvalidator implements ApplicationEventListener<RulesetRelatedDataChangedEvent>{
	
	private final Flux<Instant> invalidatorFlux = Flux.create(this::setSink)
		.share();
	
	private FluxSink<Instant> sink;
	
	@Override
	public void onApplicationEvent(RulesetRelatedDataChangedEvent event) {
		log.info("Invalidating caches");
		invalidate();
	}
	
	private void setSink(FluxSink<Instant> sink) {
		synchronized (invalidatorFlux) {
			this.sink = sink;
		}
	}
	
	private void invalidate() {
		// Sink only get's set when the first subscription is made to the flux.
		// If no listeners exist, there will be no sink, nad therefore no need
		// to publish anyway. So it is safe to ignore when null.
		synchronized (invalidatorFlux) {
			if (sink == null) return;
			
			sink.next(Instant.now());
		}
	}
	
	/**
	 * Creates a publisher that completes when the cache is invalidated.
	 * Allows for easy "Invalidate when event fires"
	 * There is a shared source publisher that fronts all the monos returned by
	 * this object, which means new subscriptions do not get previous emissions
	 * replayed. This prevents instantly completing monos.
	 * 
	 * @param _ruleset
	 * @return Invalidating mono
	 */
	public Mono<Void> getInvalidator( Object cachedValue ) {
		synchronized (invalidatorFlux) {
			log.info("Caching value [{}]", cachedValue);
			return this.invalidatorFlux
				.next()
				.then();
		}
	}

}
