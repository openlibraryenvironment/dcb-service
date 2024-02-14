package services.k_int.micronaut.concurrency;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;


@Data
@ToString
@EachProperty(ConcurrencyGroup.CONFIG_ROOT_KEY)
@Accessors(chain = true)
public class ConcurrencyGroup {
	
	protected static final String CONFIG_ROOT_KEY = "concurrency.groups";

	public static final String DEFAULT_GROUP_KEY = "default";
	protected static final ConcurrencyGroup DEFAULT_GROUP = new ConcurrencyGroup(DEFAULT_GROUP_KEY);
	
	private final String name;
  private int limit = 0;
  
  public ConcurrencyGroup(@NonNull @NotNull @Parameter String name) {
		this.name = name;
	}
}
