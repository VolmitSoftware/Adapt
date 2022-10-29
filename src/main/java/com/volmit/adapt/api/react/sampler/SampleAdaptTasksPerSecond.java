package com.volmit.adapt.api.react.sampler;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.react.XReactSampler;

@XReactSampler(id = "adapt-tasks-per-second", interval = 50, suffix = "/s")
public class SampleAdaptTasksPerSecond {
    public double sample() {
        return Adapt.instance.getTicker().getTasksPerSecond();
    }
}
