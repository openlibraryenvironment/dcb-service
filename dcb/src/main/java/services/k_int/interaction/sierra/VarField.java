package services.k_int.interaction.sierra;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.Builder;
import java.util.List;

@Builder
@Serdeable
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VarField {
	@Nullable
	String fieldTag;
	@Nullable
	String marcTag;
	@Nullable
	String ind1;
	@Nullable
	String ind2;
	@Nullable
	String content;
	@Nullable
	List<SubField> subfields;
}

