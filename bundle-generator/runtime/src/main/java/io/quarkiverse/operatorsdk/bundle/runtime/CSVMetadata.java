package io.quarkiverse.operatorsdk.bundle.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@SuppressWarnings("unused")
public @interface CSVMetadata {
    String name() default "";

    String description() default "";

    String displayName() default "";

    String[] keywords() default "";

    String maturity() default "";

    String version() default "";

    String replaces() default "";

    Maintainer[] maintainers() default {};

    Provider provider() default @Provider;

    InstallMode[] installModes() default {};

    PermissionRule[] permissionRules() default {};

    @interface Maintainer {
        String email() default "";

        String name() default "";
    }

    @interface Provider {
        String name() default "";

        String url() default "";
    }

    @interface InstallMode {
        String type();

        boolean supported() default true;
    }

    @interface PermissionRule {
        String[] apiGroups();

        String[] resources();

        String[] verbs() default { "get", "list", "watch", "create", "delete", "patch", "update" };

        /**
         * @return the service account name for the permission rule. If not provided, it will use the default service account
         *         name.
         */
        String serviceAccountName() default "";
    }
}
