package com.fsecure.deeplinkabuser;

import java.util.ArrayList;

public class HackedInfo {
    public String pinCode;
    public ArrayList<String> devices;

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public ArrayList<String> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<String> devices) {
        this.devices = devices;
    }
}
