package org.olf.dcb.core.interaction.polaris;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.logging.LogLevel;
import jakarta.inject.Singleton;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadFactory;

@Singleton
public class PolarisClientConfig extends DefaultHttpClientConfiguration {

	// Polaris systems seem to have particular problems with read timeouts.
	// So let's wait for those slow Polarises. But not too long ...
	// Use .timeout() operator if you need a smaller timeout on an individual basis ...
	// can this be set environmentally?
	final Optional<Duration> polarisTimeout = Optional.of( Duration.ofSeconds(120) );
	HttpClientConfiguration defaultConfig;

	@Override
	public Optional<LogLevel> getLogLevel() {
		return defaultConfig.getLogLevel();
	}

	@Override
	public void setLogLevel(@Nullable LogLevel logLevel) {
		defaultConfig.setLogLevel(logLevel);
	}

	@Override
	public String getEventLoopGroup() {
		return defaultConfig.getEventLoopGroup();
	}

	@Override
	public void setEventLoopGroup(@NonNull String eventLoopGroup) {
		defaultConfig.setEventLoopGroup(eventLoopGroup);
	}

	@Override
	public SslConfiguration getSslConfiguration() {
		return defaultConfig.getSslConfiguration();
	}

	@Override
	public void setSslConfiguration(SslConfiguration sslConfiguration) {
		defaultConfig.setSslConfiguration(sslConfiguration);
	}

	@Override
	public @Nullable
	WebSocketCompressionConfiguration getWebSocketCompressionConfiguration() {
		return defaultConfig.getWebSocketCompressionConfiguration();
	}

	@Override
	public boolean isFollowRedirects() {
		return defaultConfig.isFollowRedirects();
	}

	@Override
	public boolean isExceptionOnErrorStatus() {
		return defaultConfig.isExceptionOnErrorStatus();
	}

	@Override
	public void setExceptionOnErrorStatus(boolean exceptionOnErrorStatus) {
		defaultConfig.setExceptionOnErrorStatus(exceptionOnErrorStatus);
	}

	@Override
	public Optional<String> getLoggerName() {
		return defaultConfig.getLoggerName();
	}

	@Override
	public void setLoggerName(@Nullable String loggerName) {
		defaultConfig.setLoggerName(loggerName);
	}

	@Override
	public void setFollowRedirects(boolean followRedirects) {
		defaultConfig.setFollowRedirects(followRedirects);
	}

	@Override
	public Charset getDefaultCharset() {
		return defaultConfig.getDefaultCharset();
	}

	@Override
	public void setDefaultCharset(Charset defaultCharset) {
		defaultConfig.setDefaultCharset(defaultCharset);
	}

	@Override
	public Map<String, Object> getChannelOptions() {
		return defaultConfig.getChannelOptions();
	}

	@Override
	public void setChannelOptions(Map<String, Object> channelOptions) {
		defaultConfig.setChannelOptions(channelOptions);
	}

	@Override
	public Optional<Duration> getReadIdleTimeout() {
		return defaultConfig.getReadIdleTimeout();
	}

	@Override
	public Optional<Duration> getConnectionPoolIdleTimeout() {
		return defaultConfig.getConnectionPoolIdleTimeout();
	}

	@Override
	public Optional<Duration> getConnectTimeout() {
		return defaultConfig.getConnectTimeout();
	}

	@Override
	public Optional<Duration> getConnectTtl() {
		return defaultConfig.getConnectTtl();
	}

	@Override
	public Optional<Duration> getShutdownQuietPeriod() {
		return defaultConfig.getShutdownQuietPeriod();
	}

	@Override
	public Optional<Duration> getShutdownTimeout() {
		return defaultConfig.getShutdownTimeout();
	}

	@Override
	public void setShutdownQuietPeriod(@Nullable Duration shutdownQuietPeriod) {
		defaultConfig.setShutdownQuietPeriod(shutdownQuietPeriod);
	}

	@Override
	public void setShutdownTimeout(@Nullable Duration shutdownTimeout) {
		defaultConfig.setShutdownTimeout(shutdownTimeout);
	}

	@Override
	public void setReadIdleTimeout(@Nullable Duration readIdleTimeout) {
		defaultConfig.setReadIdleTimeout(readIdleTimeout);
	}

	@Override
	public void setConnectionPoolIdleTimeout(@Nullable Duration connectionPoolIdleTimeout) {
		defaultConfig.setConnectionPoolIdleTimeout(connectionPoolIdleTimeout);
	}

	@Override
	public void setConnectTimeout(@Nullable Duration connectTimeout) {
		defaultConfig.setConnectTimeout(connectTimeout);
	}

	@Override
	public void setConnectTtl(@Nullable Duration connectTtl) {
		defaultConfig.setConnectTtl(connectTtl);
	}

	@Override
	public OptionalInt getNumOfThreads() {
		return defaultConfig.getNumOfThreads();
	}

	@Override
	public void setNumOfThreads(@Nullable Integer numOfThreads) {
		defaultConfig.setNumOfThreads(numOfThreads);
	}

	@Override
	public Optional<Class<? extends ThreadFactory>> getThreadFactory() {
		return defaultConfig.getThreadFactory();
	}

	@Override
	public void setThreadFactory(Class<? extends ThreadFactory> threadFactory) {
		defaultConfig.setThreadFactory(threadFactory);
	}

	@Override
	public int getMaxContentLength() {
		return defaultConfig.getMaxContentLength();
	}

	@Override
	public void setMaxContentLength(int maxContentLength) {
		defaultConfig.setMaxContentLength(maxContentLength);
	}

	@Override
	public Proxy.Type getProxyType() {
		return defaultConfig.getProxyType();
	}

	@Override
	public void setProxyType(Proxy.Type proxyType) {
		defaultConfig.setProxyType(proxyType);
	}

	@Override
	public Optional<SocketAddress> getProxyAddress() {
		return defaultConfig.getProxyAddress();
	}

	@Override
	public void setProxyAddress(SocketAddress proxyAddress) {
		defaultConfig.setProxyAddress(proxyAddress);
	}

	@Override
	public Optional<String> getProxyUsername() {
		return defaultConfig.getProxyUsername();
	}

	@Override
	public void setProxyUsername(String proxyUsername) {
		defaultConfig.setProxyUsername(proxyUsername);
	}

	@Override
	public Optional<String> getProxyPassword() {
		return defaultConfig.getProxyPassword();
	}

	@Override
	public void setProxyPassword(String proxyPassword) {
		defaultConfig.setProxyPassword(proxyPassword);
	}

	@Override
	public void setProxySelector(ProxySelector proxySelector) {
		defaultConfig.setProxySelector(proxySelector);
	}

	@Override
	public Optional<ProxySelector> getProxySelector() {
		return defaultConfig.getProxySelector();
	}

	@Override
	public Proxy resolveProxy(boolean isSsl, String host, int port) {
		return defaultConfig.resolveProxy(isSsl, host, port);
	}

	@Override
	public @NonNull
	HttpVersionSelection.PlaintextMode getPlaintextMode() {
		return defaultConfig.getPlaintextMode();
	}

	@Override
	public void setPlaintextMode(@NonNull HttpVersionSelection.PlaintextMode plaintextMode) {
		defaultConfig.setPlaintextMode(plaintextMode);
	}

	@Override
	public @NonNull
	List<String> getAlpnModes() {
		return defaultConfig.getAlpnModes();
	}

	@Override
	public void setAlpnModes(@NonNull List<String> alpnModes) {
		defaultConfig.setAlpnModes(alpnModes);
	}

	@Override
	public boolean isAllowBlockEventLoop() {
		return defaultConfig.isAllowBlockEventLoop();
	}

	@Override
	public void setAllowBlockEventLoop(boolean allowBlockEventLoop) {
		defaultConfig.setAllowBlockEventLoop(allowBlockEventLoop);
	}

	@Override
	public int hashCode() {
		return defaultConfig.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return defaultConfig.equals(obj);
	}

	@Override
	public String toString() {
		return defaultConfig.toString();
	}

	public PolarisClientConfig(DefaultHttpClientConfiguration defaultConfig) {
		this.defaultConfig = defaultConfig;
	}

	@Override
	public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
		return defaultConfig.getConnectionPoolConfiguration();
	}

	@Override
	public Optional<Duration> getReadTimeout() {
		return polarisTimeout;
	}


	// implement all the methods BORING
}
