package services.k_int.interaction.alma;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

import java.net.URI;

import services.k_int.interaction.alma.types.*;
import services.k_int.interaction.alma.types.items.*;
import services.k_int.interaction.alma.types.holdings.*;
import services.k_int.interaction.alma.types.userRequest.*;

public interface AlmaApiClient {
	String CONFIG_ROOT = "alma.client";
	URI getRootUri();

	// https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/3234496514/ALMA+Integration

	// https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJz/
	Mono<AlmaUser> createPatron(AlmaUser patron);

	// https://developers.exlibrisgroup.com/alma/apis/users/
  Mono<AlmaUser> getAlmaUserByUserId(String user_id);

  Mono<String> deleteAlmaUser(String user_id);

	Mono<AlmaHoldings> getHoldings(String mms_id);

	Mono<AlmaItems> getItemsForHolding(String mms_id, String holding_id);

	// This one is a gamble.. may need to be implemented with the above + a filter
  Mono<AlmaItem> getItemForPID(String mms_id, String holdingId, String pid);


	Mono<AlmaItems> getAllItems(String mms_id, String holding_id);

	Mono<String> test();

  Mono<AlmaRequest> placeHold(AlmaRequest almaRequest);

	Mono<AlmaBib> createBib(String bib);


	Mono<AlmaItem> createItem(String mmsId, String holdingId, AlmaItem aid);

	Publisher<String> deleteBib(String id);

	Mono<AlmaHolding> createHolding(String bibId, String almaHolding);

	Mono<String> deleteItem(String id, String holdingsId, String mmsId);

	Mono<String> deleteHolding(String holdingsId, String mmsId);
}
