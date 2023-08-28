package services.k_int.serde;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.marc4j.marc.impl.RecordImpl;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



// @Prototype
public class MarcRecordImplConverter implements TypeConverter<RecordImpl, JsonNode> {

    private final ConversionService conversionService;
    private final JsonMapper jsonMapper;
    private static Logger log = LoggerFactory.getLogger(MarcRecordImplConverter.class);


    public MarcRecordImplConverter(ConversionService conversionService,
                JsonMapper jsonMapper) {
        this.conversionService = conversionService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<JsonNode> convert(RecordImpl marcRecord, Class<JsonNode> targetType, ConversionContext context) {

        log.debug("CONVERT {}",marcRecord);
        try {
                return Optional.of(jsonMapper.writeValueToTree(marcRecord));
        }
        catch ( Exception e ) {
                e.printStackTrace();
        }

        return Optional.empty();
/*

See: https://docs.micronaut.io/latest/api/io/micronaut/json/JsonMapper.html

@NonNull
@NonNull JsonNode writeValueToTree(@Nullable
 @Nullable Object value)
*/

    }
}
