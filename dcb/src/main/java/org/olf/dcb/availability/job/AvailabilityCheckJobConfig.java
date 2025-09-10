package org.olf.dcb.availability.job;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("dcb.jobs.availability")
public interface AvailabilityCheckJobConfig {
	
  @Bindable(defaultValue = "150")
	int getPageSize();
	
	@NonNull
	@NotNull
	Concurrency getConcurrency();

  @ConfigurationProperties("concurrency")
  interface Concurrency {

  	@Bindable(defaultValue = "2")
    int getPerSource();

  	// @Bindable(defaultValue = "#{ T(Math).max( T(Runtime).getRuntime().availableProcessors() / 4, 5) }")
  	// Bindable doesn't seem to work with an expression here. Maybe will post-upgrade.
  	// Leaving this here for info, but will mimic this in the job class, and make this optional.
  	Optional<Integer> getInstanceWide();
  }

	@NonNull
	@NotNull
	@Bindable(defaultValue = "P7D")
  Duration getRecheckGracePeriod();
}
