package org.olf.dcb.test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@Retention(RetentionPolicy.RUNTIME)
@MicronautTest(transactional = false)
public @interface DcbTest {
}
