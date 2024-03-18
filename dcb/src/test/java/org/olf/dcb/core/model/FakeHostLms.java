package org.olf.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.ingest.IngestSource;

import lombok.Data;

/**
 * Used in place of either subtype (data or config) of HostLms in tests
 */
@Data
public class FakeHostLms implements HostLms {
	private final UUID Id;
	private final String code;
	private final String name;
	private final Class<? extends HostLmsClient> clientType;
	private final Class<? extends IngestSource> ingestSourceType;
	private final Map<String, Object> clientConfig;
	private final String suppressionRulesetName;
}
