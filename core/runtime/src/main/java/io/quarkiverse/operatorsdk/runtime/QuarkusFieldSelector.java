package io.quarkiverse.operatorsdk.runtime;

import java.util.List;

import io.javaoperatorsdk.operator.api.config.informer.FieldSelector;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusFieldSelector extends FieldSelector {
    @RecordableConstructor
    public QuarkusFieldSelector(List<Field> fields) {
        super(fields);
    }

    public QuarkusFieldSelector(FieldSelector fieldSelector) {
        this(fieldSelector.getFields());
    }
}
