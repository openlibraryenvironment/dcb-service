package services.k_int.interaction.alma.types;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class AlmaUserBlock {

	/**
	 * The type of the block (e.g., CASH, GENERAL).
	 */
	private CodeValuePair block_type;

	/**
	 * The description/reason for the block.
	 */
	private CodeValuePair block_description;

	/**
	 * The status of the block. Expected values: ACTIVE, INACTIVE.
	 */
	private String block_status;

	/**
	 * Free text note attached to the block.
	 */
	private String block_note;

	/**
	 * The user who created the block.
	 */
	private String created_by;

	/**
	 * The date the block was created (usually in ISO 8601 UTC format).
	 */
	private String created_date;

	/**
	 * Indicates whether the block is Internal or External.
	 */
	private String segment_type;
}
