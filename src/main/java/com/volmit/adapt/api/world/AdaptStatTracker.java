package com.volmit.adapt.api.world;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdaptStatTracker
{
    private String stat;
    private double goal;
    private double reward;
    private String advancement;
}
