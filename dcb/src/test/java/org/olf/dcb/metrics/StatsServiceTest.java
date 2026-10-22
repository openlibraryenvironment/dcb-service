package org.olf.dcb.metrics;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;


import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Stats service has been factored out in favour of a more generic
 * Metrics collection. The class still exists to publish cluster information.
 */
@DcbTest
@Property(name = MICRONAUT_METRICS_ENABLED, value = "true")
class MetricsServiceTest {
	
	private Tags TAGS_NO_EXCEPTION = Tags.of(MetricTags.TAG_EXCEPTION, MetricTags.TAG_VALUE_NONE);
	private Tags TAGS_STATUS_SUCCESS = Tags.of(MetricTags.TAG_STATUS, MetricTags.TAG_VALUE_SUCCESS);
	
	// Simulate a small workload by delaying each omission
	private static final Flux<Integer> SOURCE = Flux.just(1,2)
			.delayElements(Duration.ofMillis(100));
	
	@Inject
	MetricService metrics;
	
	@Inject
	MeterRegistry registry;

	@Test
	void testAddingMetricsToNamedPublisher() {
		
		final String name = "testing";
		
		final var named = SOURCE
			.name(name)
			.transform(metrics.timedPublisher());

		// Verify the completion.
	  StepVerifier.create(named)
	    .expectNext(1) 
	    .expectNext(2)
	  .verifyComplete();
	  
	  // Check that the metrics exists.
	  final String metricName = MetricService.METER_NAME_PREFIX + name;
	  
	  var snapshot = registry.timer(metricName, TAGS_STATUS_SUCCESS.and(TAGS_NO_EXCEPTION)).takeSnapshot();
	  
	  assertThat("2 total timings were registered", snapshot.count(), is(2L));
	  assertThat("Average timing is >= than the delay", snapshot.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	}
	
	@Test
	void testAddingMetricsToNamedExplicitName() {
		
		final String name = "explicitName";
		final Tags tags = Tags.of("some", "value");
		
		
		final var named = SOURCE
			.transform(metrics.timedPublisher(
					name, tags));

		// Verify the completion.
	  StepVerifier.create(named) 
	    .expectNext(1) 
	    .expectNext(2)
	  .verifyComplete();
	  
	  // Check that the metrics exists.
	  final String metricName = MetricService.METER_NAME_PREFIX + name;
	  
	  var snapshot = registry.timer(metricName, TAGS_STATUS_SUCCESS.and(TAGS_NO_EXCEPTION).and(tags)).takeSnapshot();
	  
	  assertThat("2 total timings were registered", snapshot.count(), is(2L));
	  assertThat("Average timing is >= than the delay", snapshot.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	}
	
	private static Tags tagDerriver(Integer value) {
		return value == 1 ? Tags.of(MetricTags.TAG_STATUS, "one") : Tags.of(MetricTags.TAG_STATUS, "two");
	}
	
	@Test
	void testAddingMetricsToNamedPublisherWithDerrivedTags() {
		
		final String name = "derrivedTags";
		
		final var named = SOURCE
			.name(name)
			.transform(metrics.timedPublisher(MetricsServiceTest::tagDerriver));

		// Verify the completion.
	  StepVerifier.create(named) 
	    .expectNext(1) 
	    .expectNext(2)
	  .verifyComplete();
	  
	  // Check that the metrics exists.
	  final String metricName = MetricService.METER_NAME_PREFIX + name;

	  var snapshot1 = registry.timer(metricName, Tags.of(MetricTags.TAG_STATUS, "one").and(TAGS_NO_EXCEPTION)).takeSnapshot();
	  var snapshot2 = registry.timer(metricName, Tags.of(MetricTags.TAG_STATUS, "two").and(TAGS_NO_EXCEPTION)).takeSnapshot();
	  
	  assertThat("1 timing in meter tagged 'one'", snapshot1.count(), is(1L));
	  assertThat("1 timing in meter tagged 'two'", snapshot2.count(), is(1L));
	  
	  assertThat("Average timing is >= than the delay for meter 'one'", snapshot1.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	  assertThat("Average timing is >= than the delay for meter 'two'", snapshot2.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	}
	
	@Test
	void testExceptionIsCaptured() {
		
		final String name = "error";
		
		final var named = SOURCE
			.concatWith(Mono.error(new IllegalArgumentException("Kablam"))
					.delaySubscription(Duration.ofMillis(100))
					.cast(Integer.class))
			.name(name)
			.transform(metrics.timedPublisher(MetricsServiceTest::tagDerriver));

		// Verify the completion.
	  StepVerifier.create(named)
	    .expectNext(1) 
	    .expectNext(2)
	    .expectErrorMessage("Kablam") 
	  .verify();
	  
	  // Check that the metrics exists.
	  final String metricName = MetricService.METER_NAME_PREFIX + name;

	  var snapshot1 = registry.timer(metricName, Tags.of(MetricTags.TAG_STATUS, "one").and(TAGS_NO_EXCEPTION)).takeSnapshot();
	  var snapshot2 = registry.timer(metricName, Tags.of(MetricTags.TAG_STATUS, "two").and(TAGS_NO_EXCEPTION)).takeSnapshot();
	  var snapshot3 = registry.timer(metricName, Tags.of(MetricTags.TAG_STATUS, MetricTags.TAG_VALUE_ERROR)
	  		.and(Tags.of(MetricTags.TAG_EXCEPTION, "IllegalArgumentException"))).takeSnapshot();
	  
	  assertThat("1 timing in meter tagged 'one'", snapshot1.count(), is(1L));
	  assertThat("1 timing in meter tagged 'two'", snapshot2.count(), is(1L));
	  assertThat("1 timing in meter tagged 'error' has the correct exception type", snapshot3.count(), is(1L));
	  
	  assertThat("Average timing is >= than the delay for meter 'one'", snapshot1.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	  assertThat("Average timing is >= than the delay for meter 'two'", snapshot2.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	  assertThat("Average timing is >= than the delay for meter 'two'", snapshot3.mean(TimeUnit.MILLISECONDS), greaterThanOrEqualTo(100D));
	}
}
