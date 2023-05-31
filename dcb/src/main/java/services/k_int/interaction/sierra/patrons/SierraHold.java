package services.k_int.interaction.sierra.patrons;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import services.k_int.interaction.sierra.items.Location;
import services.k_int.interaction.sierra.SierraCodeTuple;

@Data
@Serdeable
public class SierraHold {

	@Nullable
	Integer id;

	@Nullable
	String record;

	@Nullable
	String patron;

	@Nullable
	Boolean frozen;

	@Nullable
	String placed;

	@Nullable
	String notNeededAfterDate;

	@Nullable
	String notWantedBeforeDate;

	@Nullable
	String pickupByDate;

	@Nullable
	SierraCodeTuple status;

	@Nullable
        SierraCodeTuple pickupLocation;

	@Nullable
        SierraCodeTuple location;

	@Nullable
	String recordType;

	@Nullable
	Integer priority;

	@Nullable
	Integer priorityQueueLength;

	@Nullable
	String note;

	@Nullable
	Boolean canFreeze;
}
