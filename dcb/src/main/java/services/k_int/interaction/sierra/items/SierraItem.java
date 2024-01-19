package services.k_int.interaction.sierra.items;

import java.util.Map;
import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.VarField;

@Data
@Builder
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
	@Nullable
  Map<Integer, FixedField> fixedFields;
	@Nullable
	List<VarField> varFields;
}
