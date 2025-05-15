package org.olf.dcb.metrics;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Singleton
public class InstrumentedHttpClient {

    private final HttpClient delegate;
    private final MeterRegistry meterRegistry;

    public InstrumentedHttpClient(@Client("/") HttpClient delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    public <I, O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        long start = System.nanoTime();
        Publisher<HttpResponse<O>> response = delegate.exchange(request, bodyType);
        // Record timing later via a reactive subscriber or a custom wrapper if needed
        long duration = System.nanoTime() - start;

        URI uri = request.getUri();
        String host = uri.getHost() != null ? uri.getHost() : "unknown";

        meterRegistry.timer("http.client.requests", "host", host).record(duration, TimeUnit.NANOSECONDS);
        return response;
    }

    // Add more wrappers as needed, e.g. retrieve, etc.
}

