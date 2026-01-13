package org.foxesworld.kalitech.engine.api.contract;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiMethod {

    ApiThreadRule thread() default ApiThreadRule.ANY;

    /**
     * If true and thread()==JME, AbstractApiModule helpers will execute body via onJmeVoid/onJmeSync automatically.
     */
    boolean sync() default false;

    /**
     * Optional per-method override.
     * DEFAULT means "use module/global mode".
     */
    Mode mode() default Mode.DEFAULT;

    ApiFlag[] flags() default {};

    ApiCostHint cost() default ApiCostHint.NORMAL;

    /**
     * Optional "all-in-header" param contracts.
     * If provided, they are applied in addition to parameter-level annotations.
     */
    ApiParam[] params() default {};

    enum Mode {
        DEFAULT,
        OFF,
        STRICT,
        CLAMP
    }
}