package io.quarkiverse.operatorsdk.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Repeatable(AdditionalRBACRules.class)
@SuppressWarnings("unused")
public @interface RBACRule {
    /**
     * Represents a wildcard string that matches any RBAC-related value (verb, resource, etc…).
     */
    String ALL = "*";

    String[] apiGroups() default {};

    String[] verbs();

    String[] resources() default {};

    String[] resourceNames() default {};

    String[] nonResourceURLs() default {};
}
