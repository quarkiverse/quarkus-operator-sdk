package io.quarkiverse.operatorsdk.it;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface CustomRateConfiguration {

    int value();
}
