package org.foxesworld.kalitech.core;

public final class KalitechPlatform {
    public static String java() {
        return System.getProperty("java.version");
    }

    public static String os() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    private KalitechPlatform() {}
}