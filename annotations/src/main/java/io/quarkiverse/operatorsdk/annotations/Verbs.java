package io.quarkiverse.operatorsdk.annotations;

import java.util.ArrayList;
import java.util.Arrays;

public class Verbs {
    public static final String CREATE = "create";
    public static final String PATCH = "patch";
    public static final String UPDATE = "update";
    public static final String GET = "get";
    public static final String LIST = "list";
    public static final String WATCH = "watch";
    public static final String DELETE = "delete";
    public static final String[] UPDATE_VERBS = new String[] { PATCH, UPDATE };
    public static final String[] READ_VERBS = new String[] { GET, LIST, WATCH };
    public static final String[] ALL_COMMON_VERBS;

    static {
        final var verbs = new ArrayList<String>(READ_VERBS.length + UPDATE_VERBS.length + 2);
        verbs.addAll(Arrays.asList(READ_VERBS));
        verbs.addAll(Arrays.asList(UPDATE_VERBS));
        verbs.add(CREATE);
        verbs.add(DELETE);
        ALL_COMMON_VERBS = verbs.toArray(new String[0]);
    }

    private Verbs() {
    }
}
