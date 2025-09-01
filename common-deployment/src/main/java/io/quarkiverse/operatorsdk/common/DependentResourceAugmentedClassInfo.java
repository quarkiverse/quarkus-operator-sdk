package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.util.JandexUtil;

public class DependentResourceAugmentedClassInfo extends ResourceAssociatedAugmentedClassInfo {
    private static final DotName CRD_CHECKING_CONDITION = DotName.createSimple(CRDPresentActivationCondition.class);
    private static final DotName OPTIONAL_ANNOTATION = DotName.createSimple(CSVMetadata.Optional.class);
    private final AnnotationInstance dependentAnnotationFromController;
    private boolean optional;

    public DependentResourceAugmentedClassInfo(ClassInfo classInfo) {
        this(classInfo, null, null);
    }

    private DependentResourceAugmentedClassInfo(ClassInfo classInfo, AnnotationInstance dependentAnnotationFromController,
            String reconcilerName) {
        super(classInfo, DEPENDENT_RESOURCE, 2,
                Optional.ofNullable(dependentAnnotationFromController)
                        .map(a -> a.value("name"))
                        .map(AnnotationValue::asString)
                        .filter(Predicate.not(String::isBlank))
                        // note that this should match DependentResource.getDefaultNameFor implementation)
                        .orElse(classInfo.name().toString()),
                reconcilerName);
        this.dependentAnnotationFromController = dependentAnnotationFromController;
    }

    public static DependentResourceAugmentedClassInfo createFor(ClassInfo classInfo,
            AnnotationInstance dependentAnnotationFromController, IndexView index, Logger log,
            Map<String, Object> context, String reconcilerName) {
        final var info = new DependentResourceAugmentedClassInfo(classInfo, dependentAnnotationFromController, reconcilerName);
        info.augmentIfKept(index, log, context);
        return info;
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        super.doAugment(index, log, context);

        // only check if dependent has an activation condition that derives from the CRD checking one here
        final var activationConditionType = getDependentAnnotationFromController().value("activationCondition");
        if (activationConditionType != null) {
            final var conditionInfo = ConfigurationUtils.getClassInfoForInstantiation(activationConditionType, Condition.class,
                    index);
            try {
                optional = CRD_CHECKING_CONDITION.equals(conditionInfo.name())
                        || JandexUtil.isSubclassOf(index, conditionInfo, CRD_CHECKING_CONDITION);
            } catch (BuildException e) {
                optional = false;
            }
        }
    }

    /**
     * Determines whether the associated dependent is defined as optional either because it uses an activation condition that
     * derives from {@link io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition} or because
     * it was explicitly annotated as such using {@link io.quarkiverse.operatorsdk.annotations.CSVMetadata.Optional}
     *
     * @return whether the associated dependent is defined as optional
     * @since 7.3.0
     */
    public boolean isOptional() {
        // if we have a matching condition, we're done, otherwise check if the dependent is explicitly marked optional
        // note that this is done here to minimize potentially useless processing in doAugment if this information is not needed
        if (!optional) {
            optional = Optional.ofNullable(classInfo().annotation(OPTIONAL_ANNOTATION))
                    .map(ai -> Optional.ofNullable(ai.value()).map(AnnotationValue::asBoolean).orElse(true))
                    .orElse(false);
        }

        return optional;
    }

    public AnnotationInstance getDependentAnnotationFromController() {
        if (dependentAnnotationFromController == null) {
            throw new IllegalStateException("Should only be called if this instance was manually created");
        }

        return dependentAnnotationFromController;
    }
}
