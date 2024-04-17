package io.quarkiverse.operatorsdk.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface RBACCRoleRef {
    String RBAC_API_GROUP = "rbac.authorization.k8s.io";

    RoleKind kind() default RoleKind.Role;

    String name() default "";

    enum RoleKind {

        ClusterRole,
        Role;

    }
}
