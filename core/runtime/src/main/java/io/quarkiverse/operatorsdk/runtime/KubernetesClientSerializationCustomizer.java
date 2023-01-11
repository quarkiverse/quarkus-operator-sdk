package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

@Qualifier
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KubernetesClientSerializationCustomizer {

    final class Literal extends AnnotationLiteral<KubernetesClientSerializationCustomizer>
            implements KubernetesClientSerializationCustomizer {

        public static final Literal INSTANCE = new Literal();
        private static final long serialVersionUID = 1L;

        private Literal() {
        }
    }
}
