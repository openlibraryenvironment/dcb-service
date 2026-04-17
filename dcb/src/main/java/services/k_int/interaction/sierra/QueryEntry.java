package services.k_int.interaction.sierra;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class QueryEntry {

	@JsonProperty("target")
	private Target target;

	@JsonProperty("expr")
	private Expr expr;

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class Target {

		@JsonProperty("record")
		private Record record;

		@JsonProperty("field")
		private Field field;

		@Builder
		@Data
		@AllArgsConstructor
		@Serdeable
		public static class Record {
			@JsonProperty("type")
			private String type;
		}

		@Builder
		@Data
		@AllArgsConstructor
		@Serdeable
		public static class Field {
			@JsonProperty("tag")
			private String tag;
		}
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	@JsonInclude(NON_NULL)
	public static class Expr {

		@JsonProperty("op")
		private String op;

		// Used by patron query
		@JsonProperty("operands")
		private List<String> operands;

		// Used by item barcode query
		@JsonProperty("operand")
		private String operand;
	}

	/*
	shortcut to building a common query for finding a patron by their 'uniqueId'
	{
		"target": {
			"record": {"type": "patron"},
			"field": {"tag": "u"}
		},
		"expr": {
			"op": "equals",
			"operands": ["exampleLocalId@exampleAgencyCode"]
		}
	}
	*/
	public static QueryEntry buildPatronQuery(String uniqueId) {
		return QueryEntry.builder()
			.target(
				QueryEntry.Target.builder()
					.record(QueryEntry.Target.Record.builder()
						.type("patron")
						.build())
					.field(QueryEntry.Target.Field.builder()
						.tag("u")
						.build())
					.build())
			.expr(QueryEntry.Expr.builder()
				.op("equals")
				.operands(List.of(uniqueId))
				.build())
			.build();
	}

	public static QueryEntry buildItemBarcodeQuery(String barcode) {
		return QueryEntry.builder()
			.target(
				QueryEntry.Target.builder()
					.record(QueryEntry.Target.Record.builder().type("item").build())
					.field(QueryEntry.Target.Field.builder().tag("b").build())
				.build())
			.expr(QueryEntry.Expr.builder()
				.op("equals")
				.operand(barcode)
				.build())
			.build();
	}
}

