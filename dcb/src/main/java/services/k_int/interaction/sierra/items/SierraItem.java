package services.k_int.interaction.sierra.items;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import services.k_int.interaction.sierra.FixedField;
import java.util.Map;

@Data
@Serdeable
public class SierraItem {
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
        Map<Integer, FixedField> fixedFields;

}
