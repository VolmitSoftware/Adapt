package com.volmit.adapt.api.world;

import com.volmit.adapt.api.xp.XPMultiplier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AdaptServerData {
    private List<XPMultiplier> multipliers = new ArrayList<>();
}
