package org.foxesworld.kalitech.engine;

public final class KalitechLauncher {
    public static void main(String[] args) {

        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        java.util.logging.LogManager.getLogManager().reset();
        //SLF4JBridgeHandler.install();
        //SLF4JBridgeHandler.removeHandlersForRootLogger();

        KalitechApplication app = new KalitechApplication();
        app.start();
    }
}