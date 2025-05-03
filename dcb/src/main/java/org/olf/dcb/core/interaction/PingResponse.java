package org.olf.dcb.core.interaction;

import java.util.UUID;
import java.util.List;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import java.time.Duration;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class PingResponse {

  String target;
  String status;
  String additional;
  Duration pingTime;

}

