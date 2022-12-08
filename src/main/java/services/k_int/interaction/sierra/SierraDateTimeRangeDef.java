package services.k_int.interaction.sierra;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import org.immutables.value.Value;

import jakarta.annotation.Nullable;

@Value.Immutable
@Value.Style(typeImmutable = "*", typeAbstract = {"*Def"})
public abstract class SierraDateTimeRangeDef {

	@NotNull
	abstract LocalDateTime from();

	@Nullable
	abstract LocalDateTime to();

	@Override
	public String toString() {

		final String fromStr = from().truncatedTo(ChronoUnit.SECONDS).toString();
		final String toStr = Objects.toString(to() != null ? to().truncatedTo(ChronoUnit.SECONDS) : null, null);

		if (toStr == null) {
			return fromStr;
		}

		return String.format("[%s,%s]", fromStr, toStr);
	}

	
	public static class Builder extends SierraDateTimeRange.Builder {
		
	}
	
	public static SierraDateTimeRange build( Consumer<Builder> consumer ) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
	
	public static Builder builder() {
		return new Builder();
	}

//	public static SierraDateTimeRangeBuilder builder() {
//		return SierraDateTimeRangeBuilder.builder();
//	}
//	
//	public static SierraDateTimeRange build( Consumer<SierraDateTimeRangeBuilder> consumer ) {
//		SierraDateTimeRangeBuilder builder = builder();
//		consumer.accept(builder);
//		return builder.build();
//	}
}
