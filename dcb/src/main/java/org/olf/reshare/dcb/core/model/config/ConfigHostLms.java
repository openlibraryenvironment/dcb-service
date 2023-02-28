package org.olf.reshare.dcb.core.model.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.Agency;
import org.olf.reshare.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(excludes = {"agencies"})
@EachProperty("hosts")
public class ConfigHostLms implements HostLms {
	
	private final BeanContext beans;
	private Map<String, Object> clientConfig;
	private final UUID id = UUID.randomUUID();
	private String name;
	private Class<? extends HostLmsClient> type;

	private List<Agency> agencies;

	public ConfigHostLms( @Parameter("name") String name, BeanContext beanContext ) {
		this.name = name;
		this.beans = beanContext;
	}

	@Override
	public Map<String, Object> getClientConfig() {
		return clientConfig;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	} 
	
	@Override
	public Class<? extends HostLmsClient> getType() {
		return type;
	}

	public void setClient( Map<String, Object> clientConfig ) {
		this.clientConfig = clientConfig;
	}

	public void setType( Class<? extends HostLmsClient> type ) {
		this.type = type;
	}
}
