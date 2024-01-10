package org.pmdet.backend.instrument.hook;

import com.code_intelligence.jazzer.api.HookType;
import com.code_intelligence.jazzer.api.MethodHook;

import java.lang.invoke.MethodHandle;

public final class LogHooks {
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "e")
    public static int logE(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        String tag = (String) arguments[0];
        String msg = (String) arguments[1];
//        System.out.println("Log.e: " + tag + " " + msg);
        if (tag.equals("Parcel")) {
//            Jazzer.reportFindingFromHook( new FuzzerSecurityIssueHigh(msg));
        }
        return 1;
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "w")
    public static int logW(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        String tag = (String) arguments[0];
        String msg = (String) arguments[1];
//        System.out.println("Log.w: " + tag + " " + msg);
        return 1;

    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "i")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "d")
    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "v")
    public static int logSuppress(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
//        String tag = (String) arguments[0];
//        String msg = (String) arguments[1];
//        System.out.println("Log: " + tag + " " + msg);
        return 1;
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "isLoggable")
    public static boolean isLoggable(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        return false;
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "println_native")
    public static int println_native(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        return 0;
    }

    @MethodHook(type = HookType.REPLACE, targetClassName = "android.util.Log", targetMethod = "logger_entry_max_payload_native")
    public static int logger_entry_max_payload_native(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
        return 1024;
    }

//
//    @MethodHook(type = HookType.REPLACE, targetClassName = "java.lang.System", targetMethod = "logW")
//    public static void slogW(MethodHandle method, Object thisObject, Object[] arguments, int hookId) {
//        String msg = (String) arguments[0];
////        System.out.println("System.logW: " + msg);
//    }

}
