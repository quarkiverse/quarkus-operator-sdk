package io.quarkiverse.operatorsdk.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Only added to support deprecation of {@link RBACCRoleRef}
 *
 * @deprecated Use {@link AdditionalRBACRoleRefs} instead
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public @interface AdditionalRBACCRoleRefs {
    RBACCRoleRef[] value();
}
