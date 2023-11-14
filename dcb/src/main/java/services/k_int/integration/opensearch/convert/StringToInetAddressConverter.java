package services.k_int.integration.opensearch.convert;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.opensearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton
@Requires(classes = RestClientBuilder.class)
public class StringToInetAddressConverter implements TypeConverter<CharSequence, InetAddress> {

	private static final Logger LOG = LoggerFactory.getLogger(StringToInetAddressConverter.class);

	@Override
	public Optional<InetAddress> convert(CharSequence object, Class<InetAddress> targetType, ConversionContext context) {
		String address = object.toString();
		try {
			return Optional.of(InetAddress.getByName(address));
		} catch (UnknownHostException e) {
			LOG.error(e.getMessage(), e);
			return Optional.empty();
		}
	}
}
