package services.k_int.interaction.sierra;

import java.util.Set;
import java.util.function.Consumer;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
@RecordBuilder.Options(addSingleItemCollectionBuilders = true, useImmutableCollections = true)
public record SierraBibParams (
	int limit,
	int offset,
	Set<String> fields,
	SierraDateTimeRange createdDate,
	SierraDateTimeRange updatedDate,
	SierraDateTimeRange deletedDate,
	Boolean deleted,
	Boolean suppressed,
	Set<String> locations
	) {
	
	public static SierraBibParamsBuilder builder() {
		return SierraBibParamsBuilder.builder();
	}
	
	public static SierraBibParams build( Consumer<SierraBibParamsBuilder> consumer ) {
		SierraBibParamsBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}