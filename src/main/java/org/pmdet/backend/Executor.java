package org.pmdet.backend;

import org.pmdet.backend.exception.CreatorMissingException;
import org.pmdet.backend.exception.ParcelMismatchException;
import org.pmdet.backend.instrument.agent.LoopChecker;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URLClassLoader;

public class Executor {
    private static Class<?> parcelableClass;
    private static URLClassLoader myClassLoader;

//    static {}

    public static void fuzzerInitialize(String[] args) throws MalformedURLException, ClassNotFoundException {
        // args: 0: parcelableName;
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing args");
        }
        String parcelableName = args[0];
        String jarDir = System.getProperty("framework.dir");
        if (jarDir == null) {
            throw new IllegalArgumentException("Missing framework.dir");
        }
        myClassLoader = MyClassLoader.get(jarDir);

//        Thread.currentThread().setContextClassLoader(myClassLoader);

        parcelableClass = myClassLoader.loadClass(parcelableName);
        String classSourcePath = parcelableClass.getProtectionDomain().getCodeSource().getLocation().getPath();
//        if (!classSourcePath.startsWith(jarDir)) {
//            throw new ClassNotFoundException(parcelableName + "found, but not in framework.dir");
//        }

        if (!myClassLoader.loadClass("android.os.Parcelable").isAssignableFrom(parcelableClass)) {
            throw new IllegalArgumentException(parcelableName + " is not Parcelable");
        }
//        System.out.println(myClassLoader.loadClass("android.os.Parcel"));
//        System.out.println(Class.forName("android.os.Parcel", false, myClassLoader));
    }

    public static void fuzzerTestOneInput(byte[] marshalData) throws Throwable {
        if (marshalData.length % 4 != 0) {
            return;
        }

//        Object creator = parcelableClass.getField("CREATOR").get(null);
        Object creator;
        try {
            Field creatorField = parcelableClass.getField("CREATOR");
            creatorField.setAccessible(true);
            creator = creatorField.get(null);
        } catch (NoSuchFieldException | NullPointerException e) {
            throw new CreatorMissingException();
        }

        Class<?> ParcelClass = myClassLoader.loadClass("android.os.Parcel");
//        Class<?> BadParcelableException = myClassLoader.loadClass("android.os.BadParcelableException");

        Method Parcel_obtain = ParcelClass.getMethod("obtain");
        Object pcl = Parcel_obtain.invoke(null);

        Method Parcel_unmarshall = ParcelClass.getMethod("unmarshall", byte[].class, int.class, int.class);
        Method Parcel_marshall = ParcelClass.getMethod("marshall");
        Method Parcel_setDataPosition = ParcelClass.getMethod("setDataPosition", int.class);
        Method Parcel_recycle = ParcelClass.getMethod("recycle");
        Method Parcel_compareData = ParcelClass.getMethod("compareData", ParcelClass);
        Method Parcel_dataPosition = ParcelClass.getMethod("dataPosition");
        Method Parcel_dataSize = ParcelClass.getMethod("dataSize");
        Method Parcel_setDataSize = ParcelClass.getMethod("setDataSize", int.class);

//        Parcel pcl = Parcel.obtain();
//        pcl.unmarshall(marshalData, 0, marshalData.length);
        Parcel_unmarshall.invoke(pcl, marshalData, 0, marshalData.length);
//        pcl.setDataPosition(0);
        Parcel_setDataPosition.invoke(pcl, 0);
        Object parcelableObj;
        Method createFromParcel = creator.getClass().getMethod("createFromParcel", ParcelClass);
        createFromParcel.setAccessible(true);

        LoopChecker.reset();
        try {
//            parcelableObj = (Parcelable) creator.createFromParcel(pcl);  // emulate system_server read
            parcelableObj = createFromParcel.invoke(creator, pcl);
        } catch (InvocationTargetException e) {
            Throwable excption = e;
            while (excption != null) {
                if (excption instanceof ExceptionInInitializerError) {
                    String message = excption.getMessage();
                    if (message != null && message.contains("org.pmdet.backend.exception")) {
                        Parcel_recycle.invoke(pcl);
                        return;
                    }
                } else if (excption instanceof RuntimeException || excption instanceof AssertionError) {
                    Parcel_recycle.invoke(pcl);
                    return;
                }
                excption = excption.getCause();
            }

//            System.out.println("ERR CAUSE " + cause.getClass().getName());
            System.out.println("Unhandled Exception: " + e.getCause());
            System.out.println("With cause: " + (e.getCause().getCause() != null ? e.getCause().getCause() : "null"));
            throw e.getCause();
        }

//        int readSize = pcl.dataPosition();
        int readSize = (int) Parcel_dataPosition.invoke(pcl);
//        boolean readEnough = pcl.dataAvail() > 0;

        if (parcelableObj == null) { // read fuzz data fail
//            pcl.recycle(); // do I need this?
            Parcel_recycle.invoke(pcl);
//            throw new FuzzerSecurityIssueLow("may have try-catch inconsistency"); // handle later
            return;
        }

        Method writeToParcel = parcelableObj.getClass().getMethod("writeToParcel", ParcelClass, int.class);
        writeToParcel.setAccessible(true);
        // read fuzz data success
//        Parcel pcl2 = Parcel.obtain();
        Object pcl2 = Parcel_obtain.invoke(null);
        int randomOffset = (int) (Math.random() * 0x10) * 4;

//        randomOffset = 0;

        Parcel_setDataSize.invoke(pcl2, randomOffset);
        Parcel_setDataPosition.invoke(pcl2, randomOffset);
        LoopChecker.reset();
//        System.out.println("Before write, pcl2 data position: " + Parcel_dataPosition.invoke(pcl2));
        try {
//            parcelableObj.writeToParcel(pcl2, 0);    // emulate system_server write
            writeToParcel.invoke(parcelableObj, pcl2, 0);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
//            if (cause instanceof LargeMemCostException || BadParcelableException.isInstance(cause)) {
                Parcel_recycle.invoke(pcl);
                Parcel_recycle.invoke(pcl2);
                return;
            } else {
                throw cause;
            }
        }

//        int writeSize = pcl2.dataSize();
//        System.out.println("After write, pcl2 data position: " + Parcel_dataPosition.invoke(pcl2));
        int writeSize = (int) Parcel_dataPosition.invoke(pcl2) - randomOffset;
        byte[] writed = (byte[]) Parcel_marshall.invoke(pcl2);

//        try {
//            Files.write(Paths.get("/tmp/parcelable"), Arrays.copyOfRange(writed, randomOffset, randomOffset + writeSize));
//        } catch (IOException e) {
//            System.out.println("write file error, " + e.toString());
//            return;
//        }

        if (writeSize == readSize) {
            // if data is same, no need to read again
//            if (Parcel.compareData(pcl, 0, pcl2, 0, readSize) == 0) {      // A13
//            if (pcl.compareData(pcl2) == 0) {
//            if ((int) Parcel_compareData.invoke(pcl, pcl2) == 0) {
////                pcl.recycle();
////                pcl2.recycle();
//                Parcel_recycle.invoke(pcl);
//                Parcel_recycle.invoke(pcl2);
////                System.out.println("same data wr");
//                return;
//            }
        }

//        pcl.recycle();
        Parcel_recycle.invoke(pcl);
//        pcl2.setDataSize(writeSize + 0x100);
//        pcl2.setDataPosition(0);
        Parcel_setDataSize.invoke(pcl2, randomOffset + writeSize + 0x100);
        Parcel_setDataPosition.invoke(pcl2, randomOffset);
//        System.out.println("Before read, pcl2 data position: " + Parcel_dataPosition.invoke(pcl2));

        LoopChecker.reset();

        try {
//            creator.createFromParcel(pcl2); // emulate victim app read
            createFromParcel.invoke(creator, pcl2);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
//            if (cause instanceof LargeMemCostException || BadParcelableException.isInstance(cause)) {
                Parcel_recycle.invoke(pcl2);
                return;
            } else {
                throw cause;
            }
        }
//        System.out.println("After read, pcl2 data position: " + Parcel_dataPosition.invoke(pcl2));

//        int victimReadSize = pcl2.dataPosition();
        int victimReadSize = (int) Parcel_dataPosition.invoke(pcl2) - randomOffset;
//        System.out.println("readSize: " + readSize + " writeSize: " + writeSize + " victimReadSize: " + victimReadSize);
//        pcl2.recycle();
        Parcel_recycle.invoke(pcl2);

        if (victimReadSize != writeSize) {
            // read fuzz, write, read mismatch
            String message = "Find mismatch of " + parcelableClass.getName() + "! readSize: " + readSize + " writeSize: " + writeSize + " victimReadSize: " + victimReadSize;
            throw new ParcelMismatchException(message);
        }
        // else: read fuzz, write mismatch, but read again success
        // no security issue
//        System.out.println("no mismatch");
    }
}
