package org.olf.dcb.request.lifecycle.ncip.profile.application;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("dcb.profile-registration")
public class DcbProfileRegistrationProperties {
	private String nodeName = "DCB";
	private URI publicBaseUrl;
	private boolean allowHttp;
	private boolean allowPrivateAddresses;
	private Duration invitationTtl = Duration.ofMinutes(30);
	private Duration syncInterval = Duration.ofMinutes(15);

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public URI getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(URI publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}

	public boolean isAllowHttp() {
		return allowHttp;
	}

	public void setAllowHttp(boolean allowHttp) {
		this.allowHttp = allowHttp;
	}

	public boolean isAllowPrivateAddresses() {
		return allowPrivateAddresses;
	}

	public void setAllowPrivateAddresses(boolean allowPrivateAddresses) {
		this.allowPrivateAddresses = allowPrivateAddresses;
	}

	public Duration getInvitationTtl() {
		return invitationTtl;
	}

	public void setInvitationTtl(Duration invitationTtl) {
		this.invitationTtl = invitationTtl;
	}

	public Duration getSyncInterval() {
		return syncInterval;
	}

	public void setSyncInterval(Duration syncInterval) {
		this.syncInterval = syncInterval;
	}
}
