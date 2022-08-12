package com.volmit.adapt.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShittyGsonDataClass {

    @Getter
    @Setter
    public class Snippets {

        @Getter
        @Setter
        public class SkillsGUI {
            private String Level;
            private String Knowledge;
            private String PowerUsed;
        }
    }

    

}