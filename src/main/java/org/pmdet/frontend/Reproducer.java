package org.pmdet.frontend;

import org.pmdet.backend.Executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class Reproducer {
    public static void main(String[] args) throws Throwable {
        if (args.length == 2) {
            String className = args[0];
            String sampleFile = args[1];
            byte[] data;
            try {
                data = Files.readAllBytes(Paths.get(sampleFile));
            } catch (IOException e) {
                System.out.println("read file error, " + e.toString());
                return;
            }
            Executor.fuzzerInitialize(new String[]{className});
            Executor.fuzzerTestOneInput(data);
        }
    }
}