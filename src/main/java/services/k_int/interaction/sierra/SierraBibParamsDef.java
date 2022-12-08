package services.k_int.interaction.sierra;

import java.util.Set;
import java.util.function.Consumer;

import org.immutables.value.Value;

import jakarta.annotation.Nullable;


@Value.Immutable
@Value.Style(typeImmutable = "*", typeAbstract = {"*Def"})
public interface SierraBibParamsDef {
	
	@Nullable Integer limit();
	@Nullable Integer offset();
	@Nullable Set<String> fields();
	@Nullable SierraDateTimeRange createdDate();
	@Nullable SierraDateTimeRange updatedDate();
	@Nullable SierraDateTimeRange deletedDate();
	@Nullable Boolean deleted();
	@Nullable Boolean suppressed();
	@Nullable Set<String> locations();
	
	public static class Builder extends SierraBibParams.Builder {
		public Builder createdDate( Consumer<SierraDateTimeRangeDef.Builder> consumer ) {
			createdDate( SierraDateTimeRange.build(consumer) );
			return this;
		}
		
		public Builder updatedDate( Consumer<SierraDateTimeRangeDef.Builder> consumer ) {
			updatedDate( SierraDateTimeRange.build(consumer) );
			return this;
		}
		
		public Builder deletedDate( Consumer<SierraDateTimeRangeDef.Builder> consumer ) {
			deletedDate( SierraDateTimeRange.build(consumer) );
			return this;
		}
	}
	
	public static SierraBibParams build( Consumer<Builder> consumer ) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
	
	public static Builder builder() {
		return new Builder();
	}
}