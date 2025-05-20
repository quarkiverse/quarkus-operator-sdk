package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.crdv2.generator.CRDPostProcessor;
import io.fabric8.kubernetes.api.model.HasMetadata;

public class LabelAdderCRDPostProcessor implements CRDPostProcessor {

    public static final String LABEL_NAME = "foo";
    public static final String LABEL_VALUE = "bar";

    @Override
    public HasMetadata process(HasMetadata crd, String crdSpecVersion) {
        final var meta = crd.getMetadata().edit().addToLabels(LABEL_NAME, LABEL_VALUE).build();
        crd.setMetadata(meta);
        return crd;
    }
}
