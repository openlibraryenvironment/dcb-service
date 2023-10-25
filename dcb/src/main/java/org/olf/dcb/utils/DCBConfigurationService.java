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


import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;


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

import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.core.model.ReferenceValueMapping;
import services.k_int.utils.UUIDUtils;

@Singleton
public class DCBConfigurationService {

	private HttpClient httpClient;
        private final NumericRangeMappingRepository numericRangeMappingRepository;
        private final ReferenceValueMappingRepository referenceValueMappingRepository;

	public DCBConfigurationService(
		HttpClient httpClient,
		ReferenceValueMappingRepository referenceValueMappingRepository,
		NumericRangeMappingRepository numericRangeMappingRepository) {
		this.httpClient = httpClient;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

        private static final Logger log = LoggerFactory.getLogger(DCBConfigurationService.class);

	public Mono<ConfigImportResult> importConfiguration(String profile, String url) {

		log.debug("importConfiguration({},{})",profile,url);

		return switch ( profile ) {
			case "numericRangeMappingImport" -> numericRangeImport(url);
			case "referenceValueMappingImport" -> referenceValueMappingImport(url);
			default -> Mono.just(ConfigImportResult.builder().message("ERROR").build());
		};
	}

	private Mono<ConfigImportResult> referenceValueMappingImport(String url) {
                HttpRequest<?> request = HttpRequest.GET(url);

                return Mono.from(httpClient.exchange(request, String.class))
                                .flatMapMany( this::extractData )
                                .concatMap( nrmr -> {
                                        log.debug("Process ref value mapping: {}",nrmr.toString());
                                        return processReferenceValueMapping(nrmr);
                                })
                                .collectList()
                                .map(recordList -> ConfigImportResult.builder()
                                        .message("OK")
                                        .recordsImported(Long.valueOf(recordList.size()))
                                        .build());
	}

	private Mono<ConfigImportResult> numericRangeImport(String url) {

		HttpRequest<?> request = HttpRequest.GET(url);

		return Mono.from(httpClient.exchange(request, String.class))
				.flatMapMany( this::extractData )
				.concatMap( nrmr -> {
					log.debug("Process range: {}",nrmr.toString());
					return processNumericRangeMapping(nrmr);
				})
				.collectList()
                                .map(recordList -> ConfigImportResult.builder()
                                        .message("OK")
                                        .recordsImported(Long.valueOf(recordList.size()))
                                        .build());
	}

	private Publisher<String[]> extractData(HttpResponse<String> httpResponse) {

		String s = httpResponse.body();
		Reader reader = new StringReader(s);
		CSVParser parser = new CSVParserBuilder().withSeparator('\t').build();
		CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).withCSVParser(parser).build();
		List<String[]> all_Rows = null;
                try { 
			all_Rows = csvReader.readAll();
			reader.close(); 
		} catch (Exception e) { }
                return Flux.fromIterable(all_Rows);
	}

	private Mono<ReferenceValueMapping> processReferenceValueMapping(String[] rvm) {
		ReferenceValueMapping rvmd= ReferenceValueMapping.builder()
			.id(UUIDUtils.dnsUUID(rvm[0]+":"+rvm[1]+":"+rvm[2]+":"+rvm[3]+":"+rvm[4]))
			.fromContext(rvm[0])
			.fromCategory(rvm[1])
			.fromValue(rvm[2])
			.toContext(rvm[3])
			.toCategory(rvm[4])
			.toValue(rvm[5])
			.build();
		return Mono.from(referenceValueMappingRepository.saveOrUpdate(rvmd));
	}

	private Mono<NumericRangeMapping> processNumericRangeMapping(String[] nrmr) {

                NumericRangeMapping nrm = NumericRangeMapping.builder()
                        .id(UUIDUtils.dnsUUID(nrmr[0]+":"+nrmr[1]+":"+nrmr[2]+":"+nrmr[5]))
                        .context(nrmr[0])
                        .domain(nrmr[1])
                        .lowerBound(Long.valueOf(nrmr[2]))
                        .upperBound(Long.valueOf(nrmr[3]))
                        .targetContext(nrmr[5])
                        .mappedValue(nrmr[4])
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
}
