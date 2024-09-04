package services.k_int.jobs;

import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


@Value
@Builder
@ToString
@Serdeable
@MappedEntity
@EqualsAndHashCode
@ExcludeFromGeneratedCoverageReport
public class JobCheckpoint {

	@Id
	@NonNull
	@NotNull
	@TypeDef( type = DataType.UUID )
	private final UUID id;
	
	@Nullable
	@TypeDef(type = DataType.JSON)
	private final JsonNode value;
	
}
