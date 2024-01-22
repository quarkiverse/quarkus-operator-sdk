package io.quarkiverse.operatorsdk.common;

public class CLIConstants {
    public static final String API_DESCRIPTION = "Creates a Kubernetes API based on a Fabric8 CustomResource along with the associated JOSDK Reconciler";

    public static final char API_KIND_SHORT = 'k';
    public static final String API_KIND = "kind";
    public static final String API_KIND_DESCRIPTION = "Your API's kind, used to generate a CustomResource class with associated spec, status and Reconciler classes";

    public static final char API_VERSION_SHORT = 'v';
    public static final String API_VERSION = "version";
    public static final String API_VERSION_DESCRIPTION = "Your API's version, e.g. v1beta1";

    public static final char API_GROUP_SHORT = 'g';
    public static final String API_GROUP = "group";
    public static final String API_GROUP_DESCRIPTION = "Your API's group, e.g. halkyon.io, this will also be used, reversed, as package name for the generated classes";

    private CLIConstants() {
    }
}
