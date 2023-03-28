package services.k_int.interaction.sierra.items;

import jakarta.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
@Data
@Serdeable
public class Result {
	@Nullable
	String id;
	@Nullable
	String updatedDate;
	@Nullable
	String createdDate;
	@Nullable
	String deletedDate;
	@Nullable
	Boolean deleted;
	@Nullable
	Boolean suppressed;
	@Nullable
	String[] bibIds;
	@Nullable
	Location location;
	@Nullable
	Status status;
	@Nullable
	String[] volumes;
	@Nullable
	String barcode;
	@Nullable
	String callNumber;
	@Nullable
	String itemType;
	@Nullable
	TransitInfo transitInfo;
	@Nullable
	Integer copyNo;
	@Nullable
	Integer holdCount;
//	fixedFields (map[integer, FixedField]): the fixed-length fields from the item record,
//	varFields (array[VarField]): the variable-length fields from the item record
}
