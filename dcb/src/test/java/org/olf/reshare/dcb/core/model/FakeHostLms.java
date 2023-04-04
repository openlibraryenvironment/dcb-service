package org.olf.reshare.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;

import lombok.Data;

/**
 * Used in place of either subtype (data or config) of HostLms in tests
 */
@Data
public class FakeHostLms implements HostLms {
	private final UUID Id;
	private final String code;
	private final String name;
	private final Class<? extends HostLmsClient> type;
	private final Map<String, Object> clientConfig;
}
