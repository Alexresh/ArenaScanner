package ru.obabok.common.model;

import java.util.ArrayList;

public class Whitelist {
    public ArrayList<WhitelistItem> whitelist;
    private String name;
    public Whitelist(ArrayList<WhitelistItem> _whitelist){
        this.whitelist = _whitelist;
    }
    public Whitelist(ArrayList<WhitelistItem> _whitelist, String name){
        this.whitelist = _whitelist;
        this.name = name;
    }

    @Override
    public String toString() {
        if(name != null){
            return name;
        }else return super.toString();
    }
}
