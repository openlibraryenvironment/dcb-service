package org.olf.dcb.core.interaction.foundation;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.foundation.strategies.CirculationStrategy;
import org.olf.dcb.core.interaction.foundation.strategies.PatronStrategy;
import org.olf.dcb.core.model.HostLms;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * A protocol-composed imperative HostLmsClient for libraries whose primitives
 * come from NCIP and/or SIP2, with per-operation customisations mixed in.
 *
 * This is the imperative implementation the borrowing/supplying placement
 * strategies resolve to for profiles A/B (NCIP-only, NCIP+API). It extends
 * {@link AbstractHostLmsClient} so every host operation DCB has not yet wired
 * for this integration returns Mono.empty() rather than null.
 */
@Slf4j
@Prototype
public class FoundationClient extends AbstractHostLmsClient {

	private final HostLms lms;
	private final BeanContext beanContext;
	private final CirculationStrategy circulationStrategy;
	private final PatronStrategy patronStrategy;

	public FoundationClient(@Parameter("hostLms") HostLms lms, BeanContext beanContext) {
		super(lms);
		this.lms = lms;
		this.beanContext = beanContext;

		// 1. Resolve the base protocol adaptor (e.g. NCIP).
		final ProtocolAdaptor baseAdapter = resolveBaseAdapter(lms, beanContext);

		// 2. Wire up the per-operation strategies, honouring any configured overrides.
		this.circulationStrategy = resolveStrategy("renew", CirculationStrategy.class, baseAdapter);
		this.patronStrategy = resolveStrategy("patron", PatronStrategy.class, baseAdapter);
	}

	private ProtocolAdaptor resolveBaseAdapter(HostLms lms, BeanContext context) {
		final var configured = ImperativeCapabilityConfig.setting(lms, "base-protocol");
		final String protocol = configured != null ? configured.toString() : "NCIP";

		if ("SIP2".equalsIgnoreCase(protocol)) {
			return context.createBean(Sip2Adaptor.class, lms);
		}

		// Default to NCIP.
		return context.createBean(NcipAdaptor.class, lms);
	}

	@SuppressWarnings("unchecked")
	private <T> T resolveStrategy(String configKey, Class<T> type, T defaultImpl) {
		final Map<String, String> overrides =
			ImperativeCapabilityConfig.setting(lms, "overrides") instanceof Map<?, ?> map
				? (Map<String, String>) map
				: null;

		if (overrides != null && overrides.containsKey(configKey)) {
			final String beanName = overrides.get(configKey);
			log.info("{} overriding {} with {}", lms.getCode(), configKey, beanName);

			return beanContext.findBean(type, Qualifiers.byName(beanName))
				.orElseThrow(() -> new IllegalStateException("Missing override bean: " + beanName));
		}

		return defaultImpl;
	}

	// ---- Delegations to the resolved strategies. Everything else inherits
	//      Mono.empty() from AbstractHostLmsClient until wired by a later slice. ----

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		return circulationStrategy.checkOutItem(checkoutItemCommand);
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		return circulationStrategy.renew(hostLmsRenewal);
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return patronStrategy.findPatron(localPatronId);
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		return patronStrategy.findVirtualPatron(patron);
	}
}
