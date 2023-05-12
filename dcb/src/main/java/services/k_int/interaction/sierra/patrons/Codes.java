package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Serdeable
@Builder
@Data
public class Codes {
	@Nullable
	String pcode1;
	@Nullable
	String pcode2;
	@Nullable
	Integer pcode3;
	@Nullable
	Integer pcode4;
}
