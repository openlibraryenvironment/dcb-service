package org.olf.dcb.configuration;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Parameter;


import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Accessors(chain = true)
@Data
@ExcludeFromGeneratedCoverageReport
@Serdeable
@ToString
@Introspected
@EachProperty("dcb.global.notifications")
public class NotificationEndpointDefinition {

    private final String name; // bound from property key like `myapp.webhooks.slack`

    private String url;
    private String profile = "slack"; // default


    public NotificationEndpointDefinition(@Parameter String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
}
