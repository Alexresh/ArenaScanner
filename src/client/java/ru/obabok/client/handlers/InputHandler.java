package ru.obabok.client.handlers;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import ru.obabok.client.Config;
import ru.obabok.client.gui.screens.ConfigGui;
import ru.obabok.client.util.RenderUtil;
import ru.obabok.common.References;

public class InputHandler implements IKeybindProvider {
    private static final InputHandler INSTANCE = new InputHandler();

    private InputHandler()
    {
        super();
        setsCallbacks();
    }

    @Override
    public void addKeysToMap(IKeybindManager iKeybindManager) {
        Config.HOTKEYS.forEach(iHotkey -> iKeybindManager.addKeybindToMap(iHotkey.getKeybind()));
    }

    @Override
    public void addHotkeys(IKeybindManager iKeybindManager) {
        iKeybindManager.addHotkeysForCategory(References.MOD_ID, "areascanner.hotkeys.category.generic_hotkeys", Config.HOTKEYS);
    }

    public static InputHandler getInstance()
    {
        return INSTANCE;
    }
    private void setsCallbacks() {
        Config.Generic.MAIN.getKeybind().setCallback((action, key) -> {
            GuiBase.openGui(new ConfigGui());
            return true;
        });
        Config.Generic.LOOK_RANDOM_SELECTED_BLOCK.getKeybind().setCallback((action, key) -> {
            RenderUtil.lookRandomSelectedBlock();
            return true;
        });
    }
}
