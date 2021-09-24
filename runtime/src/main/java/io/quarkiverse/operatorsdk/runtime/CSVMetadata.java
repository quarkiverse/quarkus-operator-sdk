package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface CSVMetadata {
    String name() default "";

    String description() default "";

    String displayName() default "";

    String[] keywords() default "";

    String maturity() default "";

    String version() default "";

    String replaces() default "";

    Maintainer[] maintainers() default {};

    Provider provider();

    @interface Maintainer {
        String email() default "";

        String name() default "";
    }

    @interface Provider {
        String name() default "";

        String url() default "";
    }
}
