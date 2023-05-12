package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import lombok.Data;
import services.k_int.interaction.sierra.items.Location;

@Data
@Serdeable
public class Result {
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
	String[] names;
	@Nullable
	String[] barcodes;
	@Nullable
	String expirationDate;
	@Nullable
	String birthDate;
	@Nullable
	String[] emails;
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
	Address[] addresses;
	@Nullable
	Phone[] phones;
	@Nullable
	String[] uniqueIds;
	@Nullable
	Integer moneyOwed;
	@Nullable
	String pMessage;
	@Nullable
	String langPref;
}
