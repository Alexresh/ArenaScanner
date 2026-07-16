package ru.obabok.client.handlers;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import ru.obabok.client.Config;
import ru.obabok.common.References;

public class InitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(References.MOD_ID, new Config());
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
    }
}
