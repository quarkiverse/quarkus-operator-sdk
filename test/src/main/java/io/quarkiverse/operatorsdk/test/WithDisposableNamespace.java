package io.quarkiverse.operatorsdk.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.test.common.QuarkusTestResource;

@QuarkusTestResource(value = DisposableNamespaceTestResource.class, restrictToAnnotatedClass = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WithDisposableNamespace {
    String UNSET_VALUE = "";

    boolean preserveOnError() default false;

    int waitAtMostSecondsForDeletion() default 0;

    String namespace() default UNSET_VALUE;

    Fixture[] fixtures() default {};

    int fixturesReadinessTimeoutSeconds() default 60;

    @interface Fixture {
        Class<FixtureFactory> factory() default FixtureFactory.class;

        String fromYAMLResource() default UNSET_VALUE;
    }
}
