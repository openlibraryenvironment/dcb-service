package services.k_int.interaction.alma.types;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class UserList {
	private List<User> user;
	private Long total_record_count;
}
