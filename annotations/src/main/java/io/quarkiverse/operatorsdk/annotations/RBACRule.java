package io.quarkiverse.operatorsdk.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@SuppressWarnings("unused")
public @interface RBACRule {
    String[] apiGroups() default {};

    String[] verbs();

    String[] resources() default {};

    String[] resourceNames() default {};

    String[] nonResourceURLs() default {};
}
