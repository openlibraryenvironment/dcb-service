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

        // location (Location, optional): the code of the location from which to fill the hold, 
        // if the hold is set for "Limit to Location" (does not apply to item-level holds),
        // pickupLocation (Location, optional): the location code of the hold's pickup location,

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
