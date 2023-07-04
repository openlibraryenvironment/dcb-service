package org.olf.dcb.core.model.config;

import java.util.UUID;

import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@EachProperty("agencies")
public class ConfigAgency implements Agency {
	
	private final BeanContext beans;
	private final UUID id = UUID.randomUUID();
	private String code;
	private String name;
	private String hostLms;
	
	public ConfigAgency( @Parameter("code") String code, @Parameter("name") String name, BeanContext beanProvider ) {
		this.code = code;
		this.name = name;
		this.beans = beanProvider;
	}

	// Programmatically return the bean with the correct name qualifier
	@Override
	public HostLms getHostLms() {
		return beans.getBean(HostLms.class, Qualifiers.byName(this.hostLms));
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setHostLms( String hostLms ) {
		this.hostLms = hostLms;
	}
}
