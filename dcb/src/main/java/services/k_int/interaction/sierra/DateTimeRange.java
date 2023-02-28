package services.k_int.interaction.sierra;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import jakarta.annotation.Nullable;

@lombok.Builder
public class DateTimeRange {

	@NotNull
	LocalDateTime fromDate;

	@Nullable
	LocalDateTime to;

	@Override
	public String toString() {

		final String fromStr = fromDate.truncatedTo(ChronoUnit.SECONDS).toString();
		final String toStr = Objects.toString(to != null ? to.truncatedTo(ChronoUnit.SECONDS) : null, null);

		if (toStr == null) {
			return fromStr;
		}

		return String.format("[%s,%s]", fromStr, toStr);
	}

	public static DateTimeRange build(Consumer<DateTimeRangeBuilder> consumer) {
		DateTimeRangeBuilder builder = DateTimeRange.builder();
		consumer.accept(builder);
		return builder.build();
	}
}
