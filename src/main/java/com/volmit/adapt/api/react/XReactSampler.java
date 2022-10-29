package com.volmit.adapt.api.react;

import com.volmit.adapt.api.react.sampler.SampleAdaptTasksPerSecond;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XReactSampler {
    Class<?>[] forceLoad = {SampleAdaptTasksPerSecond.class};

    String id();
    int interval() default 50;
    String suffix() default "";
}