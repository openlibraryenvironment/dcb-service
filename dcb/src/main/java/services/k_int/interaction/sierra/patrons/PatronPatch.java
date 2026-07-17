package services.k_int.interaction.sierra.patrons;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

/**
 * Sierra applies PUT /patrons/{id} as a partial update, so unset fields must be
 * absent from the body rather than serialized as nulls that would overwrite the
 * patron record. Serde omits nulls by default, so this only pins that behaviour
 * against a change to the global inclusion setting.
 */
@Data
@Serdeable
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatronPatch {
	List<String> emails;
	List<String> names;
	String pin;
	Integer patronType;
	List<String> uniqueIds;
	String homeLibraryCode;
	List<String> barcodes;
	String expirationDate;
}
