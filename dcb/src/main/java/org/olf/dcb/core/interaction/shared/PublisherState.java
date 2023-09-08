package org.olf.dcb.core.interaction.shared;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import java.time.Instant;
import java.util.Map;

@Builder(toBuilder = true)
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
public class PublisherState {

	public final Map<String, Object> storred_state;

	@Builder.Default
	public boolean possiblyMore = false;

	@Builder.Default
	public int offset = 0;

	@Builder.Default
	public Instant since = null;

	@Builder.Default
	public long sinceMillis = 0;

	@Builder.Default
	public long request_start_time = 0;

	@Builder.Default
	public boolean error = false;

	@Builder.Default
	public int page_counter = 0;

}
