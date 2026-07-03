package org.olf.dcb.request.lifecycle.ncip.peerauth;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("dcb.peer-auth")
public class DcbPeerAuthProperties {
	private boolean enabled;
	private Ncip ncip = new Ncip();
	private LocalIdentity localIdentity = new LocalIdentity();
	private List<TrustedPeerConfig> trustedPeers = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Ncip getNcip() {
		return ncip;
	}

	public void setNcip(Ncip ncip) {
		this.ncip = ncip != null ? ncip : new Ncip();
	}

	public LocalIdentity getLocalIdentity() {
		return localIdentity;
	}

	public void setLocalIdentity(LocalIdentity localIdentity) {
		this.localIdentity = localIdentity != null ? localIdentity : new LocalIdentity();
	}

	public List<TrustedPeerConfig> getTrustedPeers() {
		return trustedPeers;
	}

	public void setTrustedPeers(List<TrustedPeerConfig> trustedPeers) {
		this.trustedPeers = trustedPeers != null ? trustedPeers : new ArrayList<>();
	}

	public boolean isNcipEnabled() {
		return enabled && ncip.isEnabled();
	}

	public static class Ncip {
		private boolean enabled;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	public static class LocalIdentity {
		private String id = "dcb";
		private String issuer;
		private String subject;
		private Set<String> audiences = Set.of();
		private URI jwksUri;
		private String keyId;
		private String publicJwk;
		private String privateJwk;
		private Duration tokenLifetime = Duration.ofMinutes(5);

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getSubject() {
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}

		public Set<String> getAudiences() {
			return audiences;
		}

		public void setAudiences(Set<String> audiences) {
			this.audiences = audiences != null ? audiences : Set.of();
		}

		public URI getJwksUri() {
			return jwksUri;
		}

		public void setJwksUri(URI jwksUri) {
			this.jwksUri = jwksUri;
		}

		public String getKeyId() {
			return keyId;
		}

		public void setKeyId(String keyId) {
			this.keyId = keyId;
		}

		public String getPublicJwk() {
			return publicJwk;
		}

		public void setPublicJwk(String publicJwk) {
			this.publicJwk = publicJwk;
		}

		public String getPrivateJwk() {
			return privateJwk;
		}

		public void setPrivateJwk(String privateJwk) {
			this.privateJwk = privateJwk;
		}

		public Duration getTokenLifetime() {
			return tokenLifetime;
		}

		public void setTokenLifetime(Duration tokenLifetime) {
			this.tokenLifetime = tokenLifetime != null ? tokenLifetime : Duration.ofMinutes(5);
		}
	}

	public static class TrustedPeerConfig {
		private String peerId;
		private String issuer;
		private URI jwksUri;
		private Set<String> audiences = Set.of();
		private Set<String> subjects = Set.of();
		private String status = "ACTIVE";
		private Map<String, Object> jwks;
		private List<Binding> bindings = new ArrayList<>();

		public String getPeerId() {
			return peerId;
		}

		public void setPeerId(String peerId) {
			this.peerId = peerId;
		}

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public URI getJwksUri() {
			return jwksUri;
		}

		public void setJwksUri(URI jwksUri) {
			this.jwksUri = jwksUri;
		}

		public Set<String> getAudiences() {
			return audiences;
		}

		public void setAudiences(Set<String> audiences) {
			this.audiences = audiences != null ? audiences : Set.of();
		}

		public Set<String> getSubjects() {
			return subjects;
		}

		public void setSubjects(Set<String> subjects) {
			this.subjects = subjects != null ? subjects : Set.of();
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public Map<String, Object> getJwks() {
			return jwks;
		}

		public void setJwks(Map<String, Object> jwks) {
			this.jwks = jwks;
		}

		public List<Binding> getBindings() {
			return bindings;
		}

		public void setBindings(List<Binding> bindings) {
			this.bindings = bindings != null ? bindings : new ArrayList<>();
		}
	}

	public static class Binding {
		private String protocol;
		private String systemId;

		public String getProtocol() {
			return protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}

		public String getSystemId() {
			return systemId;
		}

		public void setSystemId(String systemId) {
			this.systemId = systemId;
		}
	}
}
