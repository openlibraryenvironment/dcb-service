package org.olf.dcb.core.svc;


import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URL;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.NoHostException;
import org.olf.dcb.core.model.Syslog;
import org.olf.dcb.storage.SyslogRepository;
import reactor.util.function.Tuples;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Value;
import java.util.UUID;

@Slf4j
@Singleton
public class SyslogService {

	private final SyslogRepository syslogRepository;
  private final UUID instanceUUID = UUID.randomUUID();


	public SyslogService(
		SyslogRepository syslogRepository
	){
		this.syslogRepository = syslogRepository;
	}

  public String getSystemInstanceId() {
    return instanceUUID.toString();
  }

  @Transactional
	public Mono<Syslog> log(Syslog syslog) {
		log.info("syslog {}",syslog);
		return Mono.from(
			syslogRepository.save(syslog)
		);
	}	

}
