package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class PatronQueryBody {
	private Target target;
	private Expr expr;
	@Data
	@Serdeable
	public static class Target {
		private Record record;
		private Field field;
	}
	@Data
	@Serdeable
	public static class Record {
		private String type;
	}
	@Data
	@Serdeable
	public static class Field {
		private String tag;
	}
	@Data
	@Serdeable
	public static class Expr {
		private String op;
		private String[] operands;
	}
}
