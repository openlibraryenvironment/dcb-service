package services.k_int.interaction.sierra;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Consumer;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
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

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/bibs/Get_a_list_of_bibs_get_1
		// Sierra seems to want the Z specifier, so add it in explicit
		return String.format("[%sZ,%sZ]", fromStr, toStr);
	}

	public static DateTimeRange build(Consumer<DateTimeRangeBuilder> consumer) {
		DateTimeRangeBuilder builder = DateTimeRange.builder();
		consumer.accept(builder);
		return builder.build();
	}

	public static class DateTimeRangeBuilder {
		public DateTimeRangeBuilder() {
		}
		// Lombok will fill in the fields and methods
	}
}
