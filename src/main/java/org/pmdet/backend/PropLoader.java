package org.pmdet.backend;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class PropLoader {
    private static final Map<String, String> propMap = new HashMap<>();

    static {
        init();
    }

    private static void init() {
        String jarDir = System.getProperty("framework.dir");
        File buildProp = new File(jarDir + "/build.prop");
        // read build.prop line by line and parse

        try (Stream<String> stream = Files.lines(buildProp.toPath())) {
            stream.forEach(line -> {
                String[] kv = line.split("=");
                if (kv.length == 2) {
                    propMap.put(kv[0], kv[1]);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // android.os.Build need this
        propMap.putIfAbsent("ro.product.cpu.abilist64", "arm64-v8a");
        propMap.putIfAbsent("ro.product.cpu.abilist", "arm64-v8a");
    }

    public static String getProperty(String key) {
        return propMap.get(key);
    }
}
