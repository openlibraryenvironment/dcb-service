package org.olf.reshare.dcb.core.interaction;

import java.util.List;
import java.util.Map;

import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;

import org.olf.reshare.dcb.core.model.PatronIdentity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.PatronResult;
import services.k_int.interaction.sierra.patrons.QueryResultSet;
import services.k_int.interaction.sierra.patrons.Result;

public interface HostLmsClient {

	HostLms getHostLms();

	Flux<Map<String, ?>> getAllBibData();

	Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode);

	Mono<String> postPatron(String uniqueId, Integer patronType);

	Mono<String> patronFind(String uniqueId);

        // Flux<?> getAllAgencies();
}
