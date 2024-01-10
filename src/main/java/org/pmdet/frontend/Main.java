package org.pmdet.frontend;

import org.apache.commons.cli.*;
import org.pmdet.backend.MyClassLoader;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Main {
    private static String libPath;
    private static String jarDir;
    private static String dbPath;
    private static int timeout = 30;
    private static MyClassLoader myClassLoader;
    private static final Set<String> parcelableClassNames = new HashSet<>();

    public static void main(String[] args) throws Exception {

        Options options = new Options();
        options.addOption(Option.builder("v").longOpt("androidVer")
                .argName("androidVer").hasArg().required(true).desc("android version").build());
        options.addOption(Option.builder("d").longOpt("bootclassdir")
                .argName("bootclassdir").hasArg().required(true).desc("dir to the boot classes").build());
        options.addOption(Option.builder("t").longOpt("timeout")
                .argName("timeout").hasArg().required(false).desc("the timeout seconds for each class, default to 30; 0 for no timeout").build());
        options.addOption(Option.builder("o").longOpt("output")
                .argName("output").hasArg().required(false).desc("the output (sqlite db)").build());
        options.addOption(Option.builder("c").longOpt("class")
                .argName("class").hasArg().required(false).desc("the single class to fuzz. if omitted, all Parcelable classes are fuzzed").build());

        CommandLine cmd = null;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("Usage:", options);
            System.exit(0);
        }

        jarDir = new File(cmd.getOptionValue("bootclassdir")).getAbsolutePath();
        String androidVer = cmd.getOptionValue("androidVer");

        String thisJarDir;
        try {
            thisJarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        libPath = thisJarDir + "/libs/" + androidVer + "/lib64/";
        if (!new File(libPath).exists()) {
            throw new RuntimeException("Library path " + libPath + " not find");
        }

        dbPath = cmd.getOptionValue("output");
        if (dbPath != null) {
            try (Connection conn = getConnection()) {
                conn.createStatement().execute("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, classname TEXT, status INTEGER, error TEXT)");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        decompileJarsIfDex();
        if (cmd.hasOption("timeout")) {
            timeout = Integer.parseInt(cmd.getOptionValue("timeout"));
            if (timeout < 0) {
                throw new IllegalArgumentException("timeout should be non-negative");
            }
        }

        File rep = new File("./reproducers");
        if (!rep.exists()) {
            rep.mkdir();
        }

        if (cmd.hasOption("class")) {
            String classname = cmd.getOptionValue("class");
            System.out.println("Fuzzing a single class: " + classname);
            fuzzOneClass(classname, dbPath == null);
            return;
        }

        myClassLoader = MyClassLoader.get(jarDir);
        collectParcelableClasses();

        ExecutorService executor = Executors.newFixedThreadPool(8);
//        List<String> parcelableNeedFuzz = new ArrayList<>();
        List<String> parcelableNeedFuzz = new ArrayList<>(parcelableClassNames);
        Collections.sort(parcelableNeedFuzz);

//        try (Connection conn = getConnection()) {
//            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, classname TEXT, status INTEGER, error TEXT)");
//            for (String parcelableClassName : parcelableClassNames) {
//                if (!checkHasResult(parcelableClassName, conn)) {
//                    parcelableNeedFuzz.add(parcelableClassName);
//                }
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }



        System.out.println(parcelableNeedFuzz.size() + " parcelable classes need to fuzz");
        for (int i = 0; i < parcelableNeedFuzz.size(); i++) {
            final int index = i;
            executor.execute(() -> {
                System.out.printf("Fuzzing %d/%d %s\n", index + 1, parcelableNeedFuzz.size(), parcelableNeedFuzz.get(index));
                fuzzOneClass(parcelableNeedFuzz.get(index), dbPath == null);
            });
        }
        executor.shutdown();
    }

    private static Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean checkHasResult(String className, Connection conn) {
        try {
            String sql = "SELECT status FROM tasks WHERE classname = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, className);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int stat = rs.getInt("status");
                if (stat == TaskStatus.FINISHED.ordinal() || stat == TaskStatus.MISMTACH.ordinal()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void decompileJarsIfDex() {
        File dir = new File(jarDir);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                if (child.getName().endsWith(".jar")) {
                    String jar = child.getAbsolutePath();
                    try (JarFile jarFile = new JarFile(jar)) {
                        if (jarFile.getEntry("classes.dex") == null) {
                            continue;
                        }
                        jarFile.close();
                        System.out.println("Decompiling " + jar);
                        Runtime.getRuntime().exec("d2j-dex2jar.sh " + jar + " --force -o /tmp/tmp.jar").waitFor();
                        Runtime.getRuntime().exec("d2j-class-version-switch.sh 8 /tmp/tmp.jar " + jar).waitFor();
                        Runtime.getRuntime().exec("rm /tmp/tmp.jar").waitFor();
                        System.out.println("Decompiled " + jar);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

    }

    private static void collectParcelableClasses() {
        URL[] jarURLs = myClassLoader.getURLs();
        for (URL jarURL : jarURLs) {
            iterateClasses(jarURL);
        }
        System.out.println("Found " + parcelableClassNames.size() + " parcelable classes");
    }

    private static ProcessBuilder getProcessBuilder(String className) {
        String thisJar;
        String thisJarDir;
        try {
            thisJar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            thisJarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(
                thisJarDir + "/jazzer",
                "-timeout=5",
                "-max_total_time=" + (timeout > 1 ? timeout - 1 : timeout),
                "-artifact_prefix=./reproducers/" + className + '-',
                "--reproducer_path=./reproducers/",
                "--jvm_args=-Xmx1024m:-Xverify\\:none:-Dframework.dir=" + jarDir,
                "--additional_jvm_args=-javaagent\\:" + thisJar,
                "--cp=" + thisJar,
//                "--dump_classes_dir=dump",
                "--target_class=org.pmdet.backend.Executor",
                "--instrumentation_excludes=org.pmdet.backend.MyClassLoader:org.pmdet.backend.instrument.agent.*",
                "--custom_hooks=org.pmdet.backend.instrument.hook.LogHooks:org.pmdet.backend.instrument.hook.NativeHooks",
                "--disabled_hooks=com.code_intelligence.jazzer.sanitizers.ClojureLangHooks:com.code_intelligence.jazzer.sanitizers.Deserialization:com.code_intelligence.jazzer.sanitizers.ExpressionLanguageInjection:com.code_intelligence.jazzer.sanitizers.LdapInjection:com.code_intelligence.jazzer.sanitizers.NamingContextLookup:com.code_intelligence.jazzer.sanitizers.OsCommandInjection:com.code_intelligence.jazzer.sanitizers.ReflectiveCall:com.code_intelligence.jazzer.sanitizers.RegexInjection:com.code_intelligence.jazzer.sanitizers.RegexRoadblocks:com.code_intelligence.jazzer.sanitizers.ScriptEngineInjection:com.code_intelligence.jazzer.sanitizers.ServerSideRequestForgery:com.code_intelligence.jazzer.sanitizers.SqlInjection:com.code_intelligence.jazzer.sanitizers.XPathInjection",
                "--target_args=" + className
        );
        Map<String, String> env = processBuilder.environment();
        env.put("LD_LIBRARY_PATH", libPath);
        return processBuilder;
    }

    private static void fuzzOneClass(String className, boolean printOnly) {
        ProcessBuilder processBuilder = getProcessBuilder(className);

        ArrayList<String> lines = new ArrayList<>();
        boolean hasException = false;
//        boolean hasMismatch = false;
        String errorMsg = "";
        TaskStatus stat = TaskStatus.PENDING;
        try {
            if (printOnly) {
                processBuilder.inheritIO().start().waitFor();
                return;
            }

            Process proc = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (line.startsWith("== Java Exception: ")) {
                    System.out.println(line);
                    errorMsg = line.substring("== Java Exception: ".length());
                    hasException = true;
                    if (line.contains("Find mismatch")) {
                        stat = TaskStatus.MISMTACH;
                    } else if (line.contains("CreatorMissingException")) {
                        stat = TaskStatus.NO_CREATOR;
                    } else {
                        stat = TaskStatus.FAILED;
                    }
                } else if (hasException) {
                    System.out.println(line);
                }
            }
            int exitCode = proc.waitFor();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
//        System.out.println("End " + className);

        if (hasException) {
            // save to file
//            File file = new File("./artifact/" + className + ".log");
//            try (FileWriter fileWriter = new FileWriter(file)) {
//                for (String s : lines) {
//                    fileWriter.write(s + "\n");
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

        } else {
            stat = TaskStatus.FINISHED;
        }
        // insert log with stat finish
        try (Connection conn = getConnection()) {
            String sql1 = "UPDATE tasks SET status=?, error=? WHERE classname=?";
            String sql2 = "INSERT INTO tasks (classname, status, error) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql1);
            stmt.setString(3, className);
            stmt.setInt(1, stat.ordinal());
            stmt.setString(2, errorMsg);
            if (stmt.executeUpdate() == 0) {
                stmt = conn.prepareStatement(sql2);
                stmt.setString(1, className);
                stmt.setInt(2, stat.ordinal());
                stmt.setString(3, errorMsg);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void iterateClasses(URL jarURL) {
        try (JarFile jarFile = new JarFile(jarURL.getFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    String className = entryName.replace('/', '.').substring(0, entryName.lastIndexOf('.'));
                    if (isParcelable(className)) {
//                        System.out.println("Parcelable class: " + className);
                        parcelableClassNames.add(className);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isParcelable(String className) throws IOException, ClassNotFoundException {
        URL url = myClassLoader.findResource(className.replace('.', '/') + ".class");

        InputStream inputStream = url.openStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[inputStream.available()];
        int bytesRead = inputStream.read(buffer);
        byteArrayOutputStream.write(buffer, 0, bytesRead);

        String classData = byteArrayOutputStream.toString("latin1");
        inputStream.close();
        byteArrayOutputStream.close();

        if (!classData.contains("android/os/Parcelable")) {
            return false;
        }
        Class<?> maybeParcelable;
        Class<?> ParcelableInterface;
        try {
            maybeParcelable = myClassLoader.loadClass(className);
            if (maybeParcelable.isInterface() || java.lang.reflect.Modifier.isAbstract(maybeParcelable.getModifiers())) {
                return false;
            }
            ParcelableInterface = myClassLoader.loadClass("android.os.Parcelable");
        } catch (ClassFormatError e) {
            e.printStackTrace();
            return false;
        }
        return ParcelableInterface.isAssignableFrom(maybeParcelable);
//        try {
//            Parcelable.Creator<?> creator = (Parcelable.Creator<?>) maybeParcelable.getField("CREATOR").get(null);
//        } catch (IllegalAccessException | NoSuchFieldException e) {
//            return false;
//        }
    }
}
