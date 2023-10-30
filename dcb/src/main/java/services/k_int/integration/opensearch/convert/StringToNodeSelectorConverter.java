package services.k_int.integration.opensearch.convert;

import java.util.Locale;
import java.util.Optional;

import org.opensearch.client.NodeSelector;
import org.opensearch.client.RestClientBuilder;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton
@Requires(classes = RestClientBuilder.class)
public class StringToNodeSelectorConverter implements TypeConverter<CharSequence, NodeSelector> {

	@Override
	public Optional<NodeSelector> convert(CharSequence object, Class<NodeSelector> targetType,
			ConversionContext context) {
		String nodeSelector = object.toString().toUpperCase(Locale.ENGLISH);
		switch (nodeSelector) {
		case "SKIP_DEDICATED_CLUSTER_MANAGERS":
		case "SKIP_DEDICATED_MASTERS":
			return Optional.of(NodeSelector.SKIP_DEDICATED_CLUSTER_MANAGERS);
		case "ANY":
			return Optional.of(NodeSelector.ANY);
		default:
			return Optional.empty();
		}
	}
}
