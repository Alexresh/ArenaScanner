package ru.obabok.client.models;

import com.google.gson.JsonElement;
import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigBase;

public class ConfigSplitter implements IConfigBase {
    @Override
    public ConfigType getType() {
        return null;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getComment() {
        return "";
    }

    @Override
    public String getTranslatedName() {
        return "";
    }

    @Override
    public void setPrettyName(String prettyName) {

    }

    @Override
    public void setTranslatedName(String translatedName) {

    }

    @Override
    public void setComment(String comment) {

    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public void markDirty() {

    }

    @Override
    public void markClean() {

    }

    @Override
    public void checkIfClean() {

    }

    @Override
    public void setValueFromJsonElement(JsonElement element) {

    }

    @Override
    public JsonElement getAsJsonElement() {
        return null;
    }
}
