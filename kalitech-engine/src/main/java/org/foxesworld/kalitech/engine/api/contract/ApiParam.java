package org.foxesworld.kalitech.engine.api.contract;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface ApiParam {

    int index();

    String name() default "";

    boolean notNull() default false;

    boolean finite() default false;

    /**
     * Numeric range for Number types.
     * If you don't want range checking, keep defaults.
     */
    double min() default Double.NEGATIVE_INFINITY;

    double max() default Double.POSITIVE_INFINITY;
}