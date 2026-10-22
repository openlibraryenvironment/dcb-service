package org.olf.dcb.metrics;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import reactor.core.Scannable;
import reactor.function.TupleUtils;

@Singleton
public class MetricService {

	static final String METER_NAME_PREFIX = "dcb.";
	private final BeanProvider<MetricDecorator> decorator;
	private final boolean enabled;
	
	public MetricService(BeanProvider<MetricDecorator> decorator) {
		this.enabled = decorator.isResolvable();
		this.decorator = decorator;
	}

	public <T> Function<Publisher<T>, Publisher<T>> timedPublisher(@NonNull String name, @NonNull Tags tags,
			@Nullable Function<T, Tags> valueTagsGenerator) {
		
		if (!enabled) return Function.identity();
		return pub -> decorator.get().instrumentPublisher(pub, METER_NAME_PREFIX + name, tags, valueTagsGenerator);
	}

	public <T> Function<Publisher<T>, Publisher<T>> timedPublisher(@NonNull String name,
			@Nullable Function<T, Tags> valueTagsGenerator) {
		
		if (!enabled) return Function.identity();
		return timedPublisher(name, Tags.empty(), valueTagsGenerator);
	}

	public <T> Function<Publisher<T>, Publisher<T>> timedPublisher(@NonNull String name, @NonNull Tags tags) {
		
		if (!enabled) return Function.identity();
		return timedPublisher(name, tags, null);
	}

	public <T> Function<Publisher<T>, Publisher<T>> timedPublisher(@NonNull String name) {
		
		if (!enabled) return Function.identity();
		return timedPublisher(name, Tags.empty(), null);
	}

	public <T> Function<Publisher<T>, Publisher<T>> timedPublisher(@Nullable Function<T, Tags> valueTagsGenerator) {
		
		if (!enabled) return Function.identity();
		return pub -> {
			final var state = Scannable.from(pub);
			if (!state.isScanAvailable()) {
				throw new IllegalStateException(
						"Attempting to activate metrics but the upstream is not Scannable. You might want to use `name()` (and optionally `tags()`) right before transforming");
			}

			final String name = METER_NAME_PREFIX + state.name();
			final Tags tags = Tags.of(state.tags().map(TupleUtils.function(Tag::of)).toList());

			return decorator.get().instrumentPublisher(pub, name, tags, valueTagsGenerator);
		};
	}

	public <T> Function<Publisher<T>, Publisher<T>> timedPublisher() {
		
		if (!enabled) return Function.identity();
		return timedPublisher((Function<T, Tags>) null);
	}
}
