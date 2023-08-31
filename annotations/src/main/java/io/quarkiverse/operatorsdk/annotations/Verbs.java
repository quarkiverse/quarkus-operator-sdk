package io.quarkiverse.operatorsdk.annotations;

import java.util.ArrayList;
import java.util.Arrays;

public class Verbs {
    public static final String CREATE_VERB = "create";
    public static final String PATCH_VERB = "patch";
    public static final String[] UPDATE_VERBS = new String[] { PATCH_VERB, "update" };
    public static final String DELETE_VERB = "delete";
    public static final String[] READ_VERBS = new String[] { "get", "list", "watch" };
    public static final String[] ALL_VERBS;

    static {
        final var verbs = new ArrayList<String>(READ_VERBS.length + UPDATE_VERBS.length + 2);
        verbs.addAll(Arrays.asList(READ_VERBS));
        verbs.addAll(Arrays.asList(UPDATE_VERBS));
        verbs.add(CREATE_VERB);
        verbs.add(DELETE_VERB);
        ALL_VERBS = verbs.toArray(new String[0]);
    }

    private Verbs() {
    }
}
