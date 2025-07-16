package org.olf.dcb.core.interaction.polaris;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
@Serdeable
class PolarisError {
	@JsonProperty("ErrorCode")
	Integer errorCode;
	@JsonProperty("Message")
	String message;
	@JsonProperty("MessageDetail")
	String messageDetail;
	@JsonProperty("StackTrace")
	String stackTrace;
	@JsonProperty("InnerException")
	String innerException;
	@JsonProperty("Data")
	String data;
}
