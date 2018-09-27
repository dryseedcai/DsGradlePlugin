package com.dryseed.dsgradle.utils;

public final class Log {

    private Log() {

    }

    public static int i(String msg) {
        System.out.println("[INFO][=======MMM=======] " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("[INFO][" + tag + "] " + msg);
        return 0;
    }

    public static int e(String msg) {
        System.err.println("[ERROR][=======MMM=======] " + msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.err.println("[ERROR][" + tag + "] " + msg);
        return 0;
    }
}
