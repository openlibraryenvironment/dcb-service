package services.k_int.interaction.sierra.items;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import services.k_int.interaction.sierra.DateTimeRange;
import services.k_int.interaction.sierra.DateTimeRange.DateTimeRangeBuilder;

import java.util.Set;
import java.util.function.Consumer;
@Builder
@Data
public class Params {
	@Nullable
	Integer limit;
	@Nullable
	Integer offset;
	@Nullable
	@Singular("id")
	Set<String> id;
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
	@Singular
	Set<String> bibIds;
	@Nullable
	String status;
	@Nullable
	DateTimeRange duedate;
	@Nullable
	Boolean suppressed;
	@Nullable
	@Singular
	Set<String> locations;

	public static class ParamsBuilder {
		public ParamsBuilder createdDate(Consumer<DateTimeRangeBuilder> consumer) {
			createdDate = DateTimeRange.build(consumer);
			return this;
		}

		public ParamsBuilder updatedDate(Consumer<DateTimeRangeBuilder> consumer) {
			updatedDate = DateTimeRange.build(consumer);
			return this;
		}

		public ParamsBuilder deletedDate(Consumer<DateTimeRangeBuilder> consumer) {
			deletedDate = DateTimeRange.build(consumer);
			return this;
		}

		public ParamsBuilder duedate(Consumer<DateTimeRangeBuilder> consumer) {
			duedate = DateTimeRange.build(consumer);
			return this;
		}
	}

	public static Params build(Consumer<Params.ParamsBuilder> consumer) {
		Params.ParamsBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
