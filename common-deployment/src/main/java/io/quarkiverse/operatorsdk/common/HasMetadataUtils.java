package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.ConfigurationUtils.annotationValueOrDefault;

import java.util.Locale;

import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.fabric8.kubernetes.api.Pluralize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

public class HasMetadataUtils {

    private static final DotName GROUP = DotName.createSimple(Group.class.getName());
    private static final DotName PLURAL = DotName.createSimple(Plural.class.getName());
    private static final DotName SINGULAR = DotName.createSimple(Singular.class.getName());
    private static final DotName KIND = DotName.createSimple(Kind.class.getName());

    private static final DotName VERSION = DotName.createSimple(Version.class.getName());

    public static String getFullResourceName(ClassInfo resourceCI) {
        return HasMetadata.getFullResourceName(getPlural(resourceCI), getGroup(resourceCI));
    }

    public static String getPlural(ClassInfo resourceCI) {
        return annotationValueOrDefault(resourceCI.classAnnotation(PLURAL),
                "value",
                value -> value.asString().toLowerCase(Locale.ROOT),
                () -> Pluralize.toPlural(getSingular(resourceCI)));
    }

    public static String getGroup(ClassInfo resourceCI) {
        return annotationValueOrDefault(resourceCI.classAnnotation(GROUP), "value",
                AnnotationValue::asString, () -> null);
    }

    public static String getSingular(ClassInfo resourceCI) {
        return annotationValueOrDefault(resourceCI.classAnnotation(SINGULAR), "value",
                AnnotationValue::asString, () -> getKind(resourceCI).toLowerCase(Locale.ROOT));
    }

    public static String getKind(ClassInfo resourceCI) {
        return annotationValueOrDefault(resourceCI.classAnnotation(KIND),
                "value", AnnotationValue::asString, resourceCI::simpleName);
    }

    public static String getVersion(ClassInfo resourceCI) {
        return annotationValueOrDefault(resourceCI.classAnnotation(VERSION), "value",
                AnnotationValue::asString, () -> null);
    }
}
