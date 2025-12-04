package io.quarkiverse.operatorsdk.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.api.model.apiextensions.v1.SelectableField;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.informer.FieldSelector;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusFieldSelector extends FieldSelector {
    private static final String METADATA_NAME = "metadata.name";
    private static final String METADATA_NAMESPACE = "metadata.namespace";
    private static final Set<String> DEFAULT_FIELD_SELECTOR_NAMES = Set.of(METADATA_NAME, METADATA_NAMESPACE);
    static final Map<String, Set<String>> knownValidFields = Map.of(
            Pod.class.getName(), Set.of(
                    "spec.nodeName", "spec.restartPolicy", "spec.schedulerName", "spec.serviceAccountName", "spec.hostNetwork",
                    "status.phase", "status.podIP", "status.podIPs", "status.nominatedNodeName"),
            Event.class.getName(), Set.of("involvedObject.kind",
                    "involvedObject.namespace",
                    "involvedObject.name",
                    "involvedObject.uid",
                    "involvedObject.apiVersion",
                    "involvedObject.resourceVersion",
                    "involvedObject.fieldPath",
                    "reason",
                    "reportingComponent",
                    "source",
                    "type"),
            Secret.class.getName(), Set.of("type"),
            Namespace.class.getName(), Set.of("status.phase"),
            ReplicaSet.class.getName(), Set.of("status.replicas"),
            ReplicationController.class.getName(), Set.of("status.replicas"),
            Job.class.getName(), Set.of("status.successful"),
            Node.class.getName(), Set.of("spec.unschedulable"),
            CertificateSigningRequest.class.getName(), Set.of("spec.signerName"));

    @RecordableConstructor
    public QuarkusFieldSelector(List<Field> fields) {
        super(fields);
    }

    public QuarkusFieldSelector(FieldSelector fieldSelector) {
        this(fieldSelector.getFields());
    }

    public static <R extends HasMetadata> QuarkusFieldSelector from(List<String> fieldSelectors, Class<R> resourceClass,
            BuildTimeConfigurationService configurationService) {
        if (fieldSelectors == null || fieldSelectors.isEmpty()) {
            return new QuarkusFieldSelector(List.of());
        }
        final var validFieldNames = getValidFieldNamesOrNullIfDefault(resourceClass, configurationService);
        final var fields = fieldSelectors.stream()
                .map(s -> from(s, validFieldNames, resourceClass))
                .filter(Objects::nonNull)
                .toList();
        return new QuarkusFieldSelector(fields);
    }

    static <R extends HasMetadata> Set<String> getValidFieldNamesOrNullIfDefault(Class<R> resourceClass,
            BuildTimeConfigurationService configurationService) {
        // first check if we have a known resource type as defined by:
        // https://kubernetes.io/docs/concepts/overview/working-with-objects/field-selectors/#list-of-supported-fields
        final var validNames = knownValidFields.get(resourceClass.getName());
        if (validNames == null) {

            if (CustomResource.class.isAssignableFrom(resourceClass)) {
                // check if the CRD defines selectable fields
                final var infos = configurationService.getCrdInfo().getCRDInfosFor(CRDUtils.crdNameFor(resourceClass));
                if (infos != null) {
                    final var info = infos.get(CRDUtils.DEFAULT_CRD_SPEC_VERSION);
                    if (info != null) {
                        try {
                            final var crd = CRDUtils.loadFrom(Path.of(info.getFilePath()));
                            return crd.getSpec().getVersions().stream()
                                    .map(CustomResourceDefinitionVersion::getSelectableFields)
                                    .filter(Objects::nonNull)
                                    .flatMap(fields -> fields.stream().map(SelectableField::getJsonPath))
                                    .collect(Collectors.toSet());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            // if not a CustomResource or one that doesn't define selectable fields, return null
            return null;
        } else {
            return validNames;
        }
    }

    public static FieldSelector.Field from(String fieldSelector, Set<String> validFieldNames, Class<?> resourceClass) {
        if (fieldSelector == null || fieldSelector.isEmpty()) {
            return null;
        }

        final var equalsIndex = fieldSelector.indexOf('=');
        illegalArgIf(equalsIndex == -1, fieldSelector);

        final var nameAndNegated = extractNameAndNegatedStatus(fieldSelector, equalsIndex);
        final var name = nameAndNegated.name();
        if (!DEFAULT_FIELD_SELECTOR_NAMES.contains(name)
                && (validFieldNames == null || !validFieldNames.contains(name))) {
            throw new IllegalArgumentException("'" + name + "' is not a valid field name for resource "
                    + resourceClass.getSimpleName());
        }

        final var value = extractValue(fieldSelector, equalsIndex);

        return new Field(name, value, nameAndNegated.negated());
    }

    private static NameAndNegated extractNameAndNegatedStatus(String fieldSelector, int equalsIndex) {
        var fieldNameWithPossibleBang = fieldSelector.substring(0, equalsIndex).trim();
        illegalArgIf(fieldNameWithPossibleBang.isEmpty(), fieldSelector);

        // check for possible ! as last character if the field is negated
        boolean negated = false;
        final var lastIndex = fieldNameWithPossibleBang.length() - 1;
        if (fieldNameWithPossibleBang.charAt(lastIndex) == '!') {
            negated = true;
            fieldNameWithPossibleBang = fieldNameWithPossibleBang.substring(0, lastIndex).trim();
        }
        return new NameAndNegated(fieldNameWithPossibleBang, negated);
    }

    private record NameAndNegated(String name, boolean negated) {
    }

    private static String extractValue(String fieldSelector, int equalsIndex) {
        var valueWithPossibleEquals = fieldSelector.substring(equalsIndex + 1).trim();
        illegalArgIf(valueWithPossibleEquals.isEmpty(), fieldSelector);

        // check for possible = as first character if the operator is == instead of simply =
        if (valueWithPossibleEquals.charAt(0) == '=') {
            valueWithPossibleEquals = valueWithPossibleEquals.substring(1);
        }
        return valueWithPossibleEquals;
    }

    private static void illegalArgIf(boolean predicate, String fieldSelector) {
        if (predicate) {
            throw new IllegalArgumentException(
                    "Field selector must be use the <field><op><value> format where 'op' is one of '=', '==' or '!='. Was: "
                            + fieldSelector);
        }
    }
}
