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

// @Prototype


public class MarcRecordImplConverter implements TypeConverter<RecordImpl, JsonNode> {

    private final ConversionService  conversionService;

    public MarcRecordImplConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<JsonNode> convert(RecordImpl marcRecord, Class<JsonNode> targetType, ConversionContext context) {



        Optional<Integer> day = conversionService.convert(propertyMap.get("day"), Integer.class);
        Optional<Integer> month = conversionService.convert(propertyMap.get("month"), Integer.class);
        Optional<Integer> year = conversionService.convert(propertyMap.get("year"), Integer.class);
        if (day.isPresent() && month.isPresent() && year.isPresent()) {
            try {
                return Optional.of(LocalDate.of(year.get(), month.get(), day.get())); // (3)
            } catch (DateTimeException e) {
                context.reject(propertyMap, e); // (4)
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
