package com.example.virtualandroid.vm;

import com.example.virtualandroid.vm.IVmCallback;

interface IVmService {
    void registerCallback(IVmCallback callback);
    void unregisterCallback(IVmCallback callback);
    void startP1Guest(int memoryMiB, int vcpus);
    void startP2Guest(int memoryMiB, int vcpus);
    void startP3Guest(int memoryMiB, int vcpus);
    void stopVm();
    String getState();
}
