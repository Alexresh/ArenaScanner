package ru.obabok.client.util;

import fi.dy.masa.malilib.event.InitializationHandler;
import ru.obabok.client.AreaScannerClient;
import ru.obabok.client.Config;
import ru.obabok.client.handlers.InitHandler;

public class AreaScannerMalilibHelper {
    public static boolean shouldUpdateRealtime() {
        if (AreaScannerClient.isMaliLibLoaded) {
            return Config.Generic.REALTIME_UPDATE.getBooleanValue();
        }
        return false;
    }

    public static void initMalilib(){
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }
}
