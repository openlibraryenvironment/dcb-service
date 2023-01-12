package services.k_int.interaction.sierra;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import org.immutables.value.Value.Immutable;

import jakarta.annotation.Nullable;
import services.k_int.interaction.DefaultImmutableStyle;

@Immutable
@DefaultImmutableStyle
public abstract class DateTimeRange {

	@NotNull
	abstract LocalDateTime fromDate();

	@Nullable
	abstract LocalDateTime to();

	@Override
	public String toString() {

		final String fromStr = fromDate().truncatedTo(ChronoUnit.SECONDS).toString();
		final String toStr = Objects.toString(to() != null ? to().truncatedTo(ChronoUnit.SECONDS) : null, null);

		if (toStr == null) {
			return fromStr;
		}

		return String.format("[%s,%s]", fromStr, toStr);
	}
	
	public static class Builder extends DateTimeRangeImpl.Builder {}
	
	public static DateTimeRange build( Consumer<Builder> consumer ) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
	
	public static Builder builder() {
		return new Builder();
	}
}
