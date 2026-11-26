package org.olf.dcb.metrics;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import reactor.core.observability.SignalListener;
import reactor.core.publisher.SignalType;
import reactor.util.context.ContextView;
import static org.olf.dcb.metrics.MetricTags.*;

class MetricsListener<T> implements SignalListener<T> {
	
	private static final Tags DEFAULT_VALUE_TAGS = Tags.of(Tag.of(TAG_STATUS, TAG_VALUE_SUCCESS));

	private final MeterRegistry registry;
	private final String name;
	private final Tags generalTags;
	private final AtomicLong lastSignal = new AtomicLong();
	private final Function<T, Tags> valueTagsGenerator;

	MetricsListener(@NonNull ContextView context, @NonNull MeterRegistry registry, @NonNull final String name,
			@NonNull final Tags generalTags, @Nullable Function<T, Tags> valueTagsGenerator) {
		this.registry = registry;
		this.name = name;
		this.generalTags = generalTags;
		this.valueTagsGenerator = valueTagsGenerator;
	}

	@Override
	public void doOnSubscription() throws Throwable {

		// Start.
		lastSignal.set(System.nanoTime());
	}

	@Override
	public void doOnNext(T value) throws Throwable {
		// Generate the tags for the value using the generato is applicable.
		final Tags valueTags = (valueTagsGenerator == null) ? DEFAULT_VALUE_TAGS : valueTagsGenerator.apply(value);
		
		// Prometheus expects the same number of tags per meter.
		// Always ensure we add the "exception" tag, in this case with the default "none" value.
		record(valueTags.and(getErrorTags(null)));
	}

	private void record(Tags tags) {
		// Immediately grab the timestamp and change it to now.
		final var now = System.nanoTime();
		final var last = lastSignal.getAndSet(now);

		final var elapsed = now - last;
		var tagSet = generalTags.and(tags);

		registry.timer(name, tagSet).record(elapsed, TimeUnit.NANOSECONDS);
	}
	

	@Override
	public void doOnError(Throwable error) throws Throwable {
		// Prometheus expects the same number of tags per meter.
		// Always ensure we add the "status" tag, in this case with the default "error" value.
		record( Tags.of(TAG_STATUS, TAG_VALUE_ERROR)
				.and(getErrorTags(error)));
	}

	private Tags getErrorTags(@Nullable Throwable throwable) {
		
		if (throwable == null) {
			return Tags.of(TAG_EXCEPTION, TAG_VALUE_NONE);
		}
		return Tags.of(TAG_EXCEPTION, throwable.getClass().getSimpleName());
	}

	@Override
	public void doFinally(SignalType terminationType) throws Throwable {
		// Potentially cleanup if there is any.
	}

	@Override
	public void doFirst() throws Throwable {
	}

	@Override
	public void doOnFusion(int negotiatedFusion) throws Throwable {
	}

	@Override
	public void doOnRequest(long requested) throws Throwable {
	}

	@Override
	public void doOnCancel() throws Throwable {
	}

	@Override
	public void doOnComplete() throws Throwable {
	}

	@Override
	public void doAfterComplete() throws Throwable {
	}

	@Override
	public void doAfterError(Throwable error) throws Throwable {
	}

	@Override
	public void doOnMalformedOnNext(T value) throws Throwable {
	}

	@Override
	public void doOnMalformedOnError(Throwable error) throws Throwable {
	}

	@Override
	public void doOnMalformedOnComplete() throws Throwable {
	}

	@Override
	public void handleListenerError(Throwable listenerError) {
	}
}
