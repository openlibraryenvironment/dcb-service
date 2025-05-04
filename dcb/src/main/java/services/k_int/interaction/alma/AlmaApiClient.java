package services.k_int.interaction.alma;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import io.micronaut.json.tree.JsonNode;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.olf.dcb.utils.CollectionUtils.nullIfEmpty;

import services.k_int.interaction.alma.types.*;
import services.k_int.interaction.alma.types.holdings.*;

public interface AlmaApiClient {
	String CONFIG_ROOT = "alma.client";
	URI getRootUri();

	// https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/3234496514/ALMA+Integration

	// https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJz/
	Mono<AlmaUser> createPatron(AlmaUser patron);

	// https://developers.exlibrisgroup.com/alma/apis/users/
  Mono<AlmaUser> getAlmaUserByUserId(String user_id);

  Mono<Void> deleteAlmaUser(String user_id);

	Mono<AlmaHoldings> getHoldings(String mms_id);

	Mono<String> test();
	
}
