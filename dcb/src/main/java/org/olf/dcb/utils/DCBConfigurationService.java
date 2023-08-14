package org.olf.dcb.utils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Singleton;


import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.CsvBindByName;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Reader;
import java.io.IOException;
import java.util.List;


import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.Micronaut;
import io.micronaut.core.annotation.Introspected;

import java.io.InputStream;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.olf.dcb.core.model.NumericRangeMapping;
import services.k_int.utils.UUIDUtils;

@Singleton
public class DCBConfigurationService {

	private HttpClient httpClient;
        private final NumericRangeMappingRepository numericRangeMappingRepository;

	public DCBConfigurationService(
		HttpClient httpClient,
		NumericRangeMappingRepository numericRangeMappingRepository) {
		this.httpClient = httpClient;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

        private static final Logger log = LoggerFactory.getLogger(DCBConfigurationService.class);

	public Mono<ConfigImportResult> importConfiguration(String profile, String url) {

		log.debug("importConfiguration({},{})",profile,url);

		return switch ( profile ) {
			case "numericRangeMappingImport" -> numericRangeImport(url);
			default -> Mono.just(ConfigImportResult.builder().message("ERROR").build());
		};
	}

	private Mono<ConfigImportResult> numericRangeImport(String url) {

		HttpRequest<?> request = HttpRequest.GET(url);

		return Mono.from(httpClient.exchange(request, String.class))
				.flatMapMany( this::extractData )
				.flatMap( nrmr -> {
					log.debug("Process range: {}",nrmr);
					return process(nrmr);
				})
				.collectList()
                                .map(recordList -> ConfigImportResult.builder()
                                        .message("OK")
                                        .recordsImported(Long.valueOf(recordList.size()))
                                        .build());
	}

	private Publisher<NumericRangeMappingRow> extractData(HttpResponse<String> httpResponse) {

        	HeaderColumnNameMappingStrategy<NumericRangeMappingRow> ms = new HeaderColumnNameMappingStrategy<NumericRangeMappingRow>();
	        ms.setType(NumericRangeMappingRow.class);

		String s = httpResponse.body();
		Reader reader = new StringReader(s);

                CsvToBean<NumericRangeMappingRow> cb = new CsvToBeanBuilder<NumericRangeMappingRow>(reader)
                	.withSeparator('\t')
                        .withType(NumericRangeMappingRow.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .withMappingStrategy(ms)
                        .build();
                List<NumericRangeMappingRow> r = cb.parse();
                try { reader.close(); } catch (Exception e) { }
                return Flux.fromIterable(r);
	}

	private Mono<NumericRangeMapping> process(NumericRangeMappingRow nrmr) {

                NumericRangeMapping nrm = NumericRangeMapping.builder()
                        .id(UUIDUtils.dnsUUID(nrmr.getContext()+":"+":"+nrmr.getDomain()+":"+nrmr.getTargetContext()+":"+nrmr.getLowerBound()))
                        .context(nrmr.getContext())
                        .domain(nrmr.getDomain())
                        .lowerBound(nrmr.getLowerBound())
                        .upperBound(nrmr.getUpperBound())
                        .targetContext(nrmr.getTargetContext())
                        .mappedValue(nrmr.getTargetValue())
                        .build();

		return Mono.from(numericRangeMappingRepository.saveOrUpdate(nrm));
	}

        @Data
        @Builder
        @Serdeable
	@AllArgsConstructor
        @NoArgsConstructor
        @Introspected
        public static class ConfigImportResult {
		String message;
                Long recordsImported;
        }

	@Data
        @Builder
        @Serdeable
	@AllArgsConstructor
	@NoArgsConstructor
        @Introspected
        public static class NumericRangeMappingRow {
	 	@CsvBindByName(column = "context")
                String context;
	 	@CsvBindByName(column = "domain")
                String domain;
	 	@CsvBindByName(column = "lowerBound")
		Long lowerBound;
	 	@CsvBindByName(column = "upperBound")
		Long upperBound;
	 	@CsvBindByName(column = "targetContext")
		String targetContext;
	 	@CsvBindByName(column = "targetValue")
		String targetValue;
        }

}
