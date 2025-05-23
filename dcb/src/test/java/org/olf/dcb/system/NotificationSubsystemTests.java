package org.olf.dcb.system;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.configuration.NotificationEndpointDefinition;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.core.svc.AlarmsService;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import io.micronaut.context.annotation.Property;

import lombok.extern.slf4j.Slf4j;

@Property(name = "dcb.global.notifications.slack.url", value = "https://hooks.slack.com/a")
@Property(name = "dcb.global.notifications.slack.profile", value = "LOG")
@Property(name = "dcb.global.notifications.teams.url", value = "https://outlook.office.com/webhook/abc")
@Property(name = "dcb.global.notifications.teams.profile", value = "LOG")
@Property(name = "dcb.global.notifications.log.profile", value = "LOG")
@Slf4j
@DcbTest
class NotificationSubsystemTests {

  @Inject
  AlarmsService alarmsService;

  @Test
  public void testAlarmsService() {
    log.info("Tesing alarms service::");
    alarmsService.debugConfig();

    List<NotificationEndpointDefinition> nil = alarmsService.getEndpoints();

    log.info("AlarmService endpoints : {}",nil);

    assert(nil.size() == 3);
  }
}
