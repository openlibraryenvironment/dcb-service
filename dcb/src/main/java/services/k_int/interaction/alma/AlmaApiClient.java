package services.k_int.interaction.alma;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micronaut.json.tree.JsonNode;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.olf.dcb.utils.CollectionUtils.nullIfEmpty;

public interface AlmaApiClient {
	String CONFIG_ROOT = "alma.client";
	Logger log = LoggerFactory.getLogger(AlmaApiClient.class);
	
	URI getRootUri();

}
