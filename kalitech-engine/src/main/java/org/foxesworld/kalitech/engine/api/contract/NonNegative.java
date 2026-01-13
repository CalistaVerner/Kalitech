package org.foxesworld.kalitech.engine.api.contract;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface NonNegative {
}