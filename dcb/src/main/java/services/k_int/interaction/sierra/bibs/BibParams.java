package services.k_int.interaction.sierra.bibs;

import java.util.Set;
import java.util.function.Consumer;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import services.k_int.interaction.sierra.DateTimeRange;

// Serde reads a checkpoint through this builder, so it has one setter per property: a Consumer
// overload stops every harvest resuming from its checkpoint.
@Builder(toBuilder = true)
@Data
@Serdeable
public class BibParams {

	@Nullable
	Integer limit;

	@Nullable
	Integer offset;

	@Nullable
	@Singular
	Set<String> fields;

	@Nullable
	DateTimeRange createdDate;

	@Nullable
	DateTimeRange updatedDate;

	@Nullable
	DateTimeRange deletedDate;

	@Nullable
	Boolean deleted;

	@Nullable
	Boolean suppressed;

	@Nullable
	@Singular
	Set<String> locations;

	public static class BibParamsBuilder {
		public BibParamsBuilder createdDate(DateTimeRange range) {
			createdDate = range;
			return this;
		}
		
		public BibParamsBuilder updatedDate(DateTimeRange range) {
			updatedDate = range;
			return this;
		}
		
		public BibParamsBuilder deletedDate(DateTimeRange range) {
			deletedDate = range;
			return this;
		}
	}

	public static BibParams build(Consumer<BibParamsBuilder> consumer) {
		BibParamsBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
