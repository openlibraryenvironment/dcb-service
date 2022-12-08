package services.k_int.interaction.sierra;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.Nullable;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record SierraDateTimeRange(
		@NotNull LocalDateTime from,
		@Nullable LocalDateTime to) {
	
	@Override
	public String toString() {
		
		final String fromStr = from.truncatedTo(ChronoUnit.SECONDS).toString();
		final String toStr = Objects.toString(to != null ? to.truncatedTo(ChronoUnit.SECONDS) : null, null);
		
		if ( toStr == null ) {
			return fromStr;
		}
		
		return String.format("[%s,%s]", fromStr, toStr);
	}
	
	public static SierraDateTimeRangeBuilder builder() {
		return SierraDateTimeRangeBuilder.builder();
	}
	
	public static SierraDateTimeRange build( Consumer<SierraDateTimeRangeBuilder> consumer ) {
		SierraDateTimeRangeBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
