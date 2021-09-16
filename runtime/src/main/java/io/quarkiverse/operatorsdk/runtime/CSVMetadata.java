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
}
