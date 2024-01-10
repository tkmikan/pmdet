package org.pmdet.backend;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class MyClassLoader extends URLClassLoader {
    private boolean libcoreLoaded = false;
    public MyClassLoader(URL[] urls) {
        super(urls);
    }

    public static MyClassLoader get(String jarDir) throws MalformedURLException {
        List<URL> jarURLs = new ArrayList<>();
        File dir = new File(jarDir);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                if (child.getName().endsWith(".jar")) {
                    jarURLs.add(child.toURI().toURL());
                }
            }
        }
        return new MyClassLoader(jarURLs.toArray(new URL[0]));
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.startsWith("java.")) {
            return super.loadClass(name);
        }

//        System.out.println("MyClassLoader: loading " + name);

        String[] useParent = {
                "android.os.Parcel", "android.os.Parcel$SquashReadHelper", "android.os.Parcel$ReadWriteHelper",
                "android.os.Parcelable", "android.os.Parcelable$Creator", "android.os.BadParcelableException",
                "android.os.Bundle"
        };
        for (String s : useParent) {
            if (s.equals(name)) {
                return super.loadClass(name);
            }
        }
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        try {
            c = findClass(name);
        } catch (ClassNotFoundException e) {
//            System.out.println(e.getMessage());
            c = super.loadClass(name);
        }
//        if (!libcoreLoaded && name.startsWith("libcore.")) {
//            System.out.println("MyClassLoader: Loading libjavacore: " + name);
//            System.loadLibrary("javacore");
//            System.out.println("MyClassLoader: Loaded libjavacore");
//            libcoreLoaded = true;
//        }
        return c;
    }
}