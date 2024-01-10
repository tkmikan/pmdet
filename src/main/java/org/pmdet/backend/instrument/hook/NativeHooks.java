package org.pmdet.backend.instrument.hook;

import com.code_intelligence.jazzer.api.HookType;
import com.code_intelligence.jazzer.api.MethodHook;
import org.pmdet.backend.exception.LargeMemCostException;
import org.pmdet.backend.PropLoader;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;

public final class NativeHooks {

    private static String getProperty(String key) {
        return PropLoader.getProperty(key);
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemProperties", targetMethod = "native_get", targetMethodDescriptor = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    public static String native_get(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        String property = getProperty((String) arguments[0]);
        return property != null ? property : (String) arguments[1];
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemProperties", targetMethod = "native_get_int", targetMethodDescriptor = "(Ljava/lang/String;I)I")
    public static int native_get_int(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        String property = getProperty((String) arguments[0]);
        if (property != null && !property.isEmpty()) {
            return Integer.parseInt(property);
        }
        return (int) arguments[1];
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemProperties", targetMethod = "native_get_long", targetMethodDescriptor = "(Ljava/lang/String;J)J")
    public static long native_get_long(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        String property = getProperty((String) arguments[0]);
        if (property != null && !property.isEmpty()) {
            return Long.parseLong(property);
        }
        return (long) arguments[1];
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemProperties", targetMethod = "native_get_boolean", targetMethodDescriptor = "(Ljava/lang/String;Z)Z")
    public static boolean native_get_boolean(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        String property = getProperty((String) arguments[0]);
        if (property != null && !property.isEmpty()) {
            return Boolean.parseBoolean(property);
        }
        return (boolean) arguments[1];
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "elapsedRealtime")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "elapsedRealtimeNanos")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "currentThreadTimeMillis")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "currentThreadTimeMicro")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "currentTimeMicro")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "uptimeNanos")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.os.SystemClock", targetMethod = "uptimeMillis")
    public static long elapsedRealtime(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        return 123456;
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "dalvik.system.VMRuntime", targetMethod = "newUnpaddedArray")
    public static Object newUnpaddedArray(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        Class<?> klass = (Class<?>) arguments[0];
        int size = (int) arguments[1];
        if (size > 0x10000) {
            throw new LargeMemCostException("new huge array");
        }
        return Array.newInstance(klass, size);
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "dalvik.system.VMRuntime", targetMethod = "is64Bit")
    public static boolean is64Bit(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        return true;
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.system.Os", targetMethod = "getpid")
    public static int getpid(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        return 1000;
    }

}
