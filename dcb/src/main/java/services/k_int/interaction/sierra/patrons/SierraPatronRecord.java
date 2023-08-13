package services.k_int.interaction.sierra.patrons;

import java.util.List;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.items.Location;

@Data
@Serdeable
public class SierraPatronRecord {
	@Nullable
	Integer id;
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
	List<String> names;
	@Nullable
	List<String> barcodes;
	@Nullable
	String expirationDate;
	@Nullable
	String birthDate;
	@Nullable
	List<String> emails;
	@Nullable
	Integer patronType;
	@Nullable
	Codes patronCodes;
	@Nullable
	String homeLibraryCode;
	@Nullable
	Location homeLibrary;
	@Nullable
	Message message;
	@Nullable
	Block blockInfo;
	@Nullable
	Block autoBlockInfo;
	@Nullable
	List<Address> addresses;
	@Nullable
	List<Phone> phones;
	@Nullable
	List<String> uniqueIds;
	@Nullable
	Integer moneyOwed;
	@Nullable
	String pMessage;
	@Nullable
	String langPref;
	@Nullable
	Map<Integer, FixedField> fixedFields;
}
