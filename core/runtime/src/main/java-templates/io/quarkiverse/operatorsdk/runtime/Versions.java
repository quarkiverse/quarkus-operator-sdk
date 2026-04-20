package io.quarkiverse.operatorsdk.runtime;

import java.time.Instant;
import java.util.Date;

public final class Versions {
  public static final String QUARKUS = "${quarkus.version}";
  public static final String BUILD_VERSION = "${project.version}";
  public static final String COMMIT = "${buildNumber}".substring(0, 6);
  public static final String BRANCH = "${scmBranch}";
  public static final Date BUILD_TIME = Date.from(Instant.ofEpochMilli(${timestamp}L));
}
