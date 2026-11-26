package org.olf.dcb.metrics;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import jakarta.inject.Singleton;
import reactor.core.observability.SignalListener;
import reactor.core.publisher.Flux;
import reactor.util.context.ContextView;

@Singleton
@RequiresMetrics
public class MetricDecorator {
	private final MeterRegistry meterRegistry;
	public MetricDecorator(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	<T> Publisher<T> instrumentPublisher(@NonNull Publisher<T> pub, @NonNull final String name,
			@NonNull final Tags tags, @Nullable Function<T, Tags> valueTagsGenerator) {
		var instrumented = Flux.from(pub).tap(getListenerCreator(name, tags, valueTagsGenerator));

		return Publishers.isSingle(pub.getClass()) ? instrumented.singleOrEmpty() : instrumented;
	}
	
	 private <T> Function<ContextView, SignalListener<T>> getListenerCreator(@NonNull final String name,
			@NonNull final Tags tags, @Nullable Function<T, Tags> valueTagsGenerator) {

		return context -> new MetricsListener<>(context, meterRegistry, name, tags, valueTagsGenerator);
	}
}
