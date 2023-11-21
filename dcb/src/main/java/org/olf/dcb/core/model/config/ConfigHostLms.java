package org.olf.dcb.core.model.config;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.interaction.HostLmsClient;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import services.k_int.utils.UUIDUtils;

@Serdeable
@Getter
@EachProperty("hosts")
public class ConfigHostLms {
	private Map<String, Object> clientConfig;
	private final UUID id;
	private final String name;
	private final String code;
	private Class<? extends HostLmsClient> type;

	public ConfigHostLms(@Parameter("code") @NonNull String code, @Parameter("name") String name) {
		this.code = code;
		this.name = name;
		
		final String concat = String.format("config:lms:%s", code);
		id =  UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	public void setClient(Map<String, Object> clientConfig) {
		this.clientConfig = clientConfig;
	}

	public void setType(Class<? extends HostLmsClient> type) {
		this.type = type;
	}
}
