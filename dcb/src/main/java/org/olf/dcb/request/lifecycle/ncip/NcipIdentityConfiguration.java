package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("dcb.ncip")
public class NcipIdentityConfiguration {
	private String systemId;
	private String agencyId;

	public String getSystemId() {
		return requireText(systemId, "dcb.ncip.system-id");
	}

	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}

	public String getAgencyId() {
		return requireText(agencyId, "dcb.ncip.agency-id");
	}

	public void setAgencyId(String agencyId) {
		this.agencyId = agencyId;
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required");
		}
		return value;
	}
}
