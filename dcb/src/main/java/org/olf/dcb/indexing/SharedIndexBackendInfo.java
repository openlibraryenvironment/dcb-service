package org.olf.dcb.indexing;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

/**
 * Records which search backend the shared index is actually talking to.
 *
 * <p>Exists because the answer was surprisingly hard to obtain. Establishing the
 * Elasticsearch or OpenSearch version behind a deployment previously meant having
 * credentials for that cluster, and client/server version compatibility is exactly the
 * kind of thing that breaks on a framework upgrade -- an Elasticsearch 9 client cannot
 * talk to an 8.x server at all. Having each deployment state its own backend at startup,
 * and expose it on the info endpoint, removes that guesswork.
 *
 * <p>Always present as a bean, even when no shared index is configured, so that
 * consumers do not need to be conditional. When there is no index the value is simply
 * empty.
 */
@Singleton
public class SharedIndexBackendInfo {

	private final AtomicReference<String> distribution = new AtomicReference<>();
	private final AtomicReference<String> version = new AtomicReference<>();

	/**
	 * @param distribution e.g. "opensearch", or "elasticsearch"
	 * @param version the server version, e.g. "2.14.0"
	 */
	public void record(String distribution, String version) {
		this.distribution.set(distribution);
		this.version.set(version);
	}

	public Optional<String> getDistribution() {
		return Optional.ofNullable(distribution.get());
	}

	public Optional<String> getVersion() {
		return Optional.ofNullable(version.get());
	}
}
