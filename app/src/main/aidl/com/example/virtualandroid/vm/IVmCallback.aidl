package com.example.virtualandroid.vm;

interface IVmCallback {
    void onStateChanged(String state, String detail);
    void onLogLine(String line);
}
