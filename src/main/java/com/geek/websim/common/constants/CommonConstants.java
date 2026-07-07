package com.geek.websim.common.constants;

import java.util.List;

public final class CommonConstants {
    public static final String CONFIG_FILE_EXTENSION = ".json";
    public static final List<String> RESERVED_PATH_PREFIXES = List.of("/admin", "/actuator", "/assets", "/favicon.ico");

    private CommonConstants() {
    }
}
