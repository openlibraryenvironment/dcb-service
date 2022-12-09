package services.k_int.interaction.sierra.bibs;

import java.util.Set;
import java.util.function.Consumer;

import org.immutables.value.Value.Immutable;

import jakarta.annotation.Nullable;
import services.k_int.interaction.DefaultImmutableStyle;
import services.k_int.interaction.sierra.DateTimeRange;
import services.k_int.interaction.sierra.DateTimeRange;

@Immutable
@DefaultImmutableStyle
public interface BibParams {
	
	@Nullable Integer limit();
	@Nullable Integer offset();
	@Nullable Set<String> fields();
	@Nullable DateTimeRange createdDate();
	@Nullable DateTimeRange updatedDate();
	@Nullable DateTimeRange deletedDate();
	@Nullable Boolean deleted();
	@Nullable Boolean suppressed();
	@Nullable Set<String> locations();
	
	public static class Builder extends BibParamsImpl.Builder {
		public Builder createdDate( Consumer<DateTimeRange.Builder> consumer ) {
			createdDate( DateTimeRange.build(consumer) );
			return this;
		}
		
		public Builder updatedDate( Consumer<DateTimeRange.Builder> consumer ) {
			updatedDate( DateTimeRange.build(consumer) );
			return this;
		}
		
		public Builder deletedDate( Consumer<DateTimeRange.Builder> consumer ) {
			deletedDate( DateTimeRange.build(consumer) );
			return this;
		}
	}
	
	public static BibParams build( Consumer<Builder> consumer ) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
	
	public static Builder builder() {
		return new Builder();
	}
}