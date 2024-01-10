package org.pmdet.backend.instrument.agent;

import org.pmdet.backend.exception.LargeMemCostException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ArraySizeChecker {
    public static void check(int size) {
        check(size, "array size is too large: ");
    }

    public static void check(int size, String message) {
        if (size < 0 || size >= 0x10000) {
            throw new LargeMemCostException(message + size);
        }
    }

    public static void checkParcelTop(Object p, String message) {
//        int currentPos = p.dataPosition();
//        int size = p.readInt();
//        if (size >= 0x10000) {
//            throw new LargeMemCostException(message + size);
//        }
//        p.setDataPosition(currentPos);

        Class<?> ParcelClass = p.getClass();
        try {
            Method Parcel_dataPosition = ParcelClass.getMethod("dataPosition");
            Method Parcel_readInt = ParcelClass.getMethod("readInt");
            Method Parcel_setDataPosition = ParcelClass.getMethod("setDataPosition", int.class);
            int currentPos = (int) Parcel_dataPosition.invoke(p);
            int size = (int) Parcel_readInt.invoke(p);
            if (size >= 0x10000) {
                throw new LargeMemCostException(message + size);
            }
            Parcel_setDataPosition.invoke(p, currentPos);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
