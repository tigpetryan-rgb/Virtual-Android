# P2 boot-stage diagnostics

v0.7 records deterministic milestones instead of reporting every failure as
"framework marker missing".

Order:

```text
STARTING
LINUX                  <- kernel serial contains "Booting Linux"
ANDROID_INIT            <- VA_P2_ANDROID_INIT_OK
SERVICEMANAGER          <- init.svc.servicemanager=running
ZYGOTE                  <- init.svc.zygote=running
FRAMEWORK_READY         <- sys.boot_completed=1
```

`latest-p2.json` format 2 records `lastStage` and a timestamp for each observed
milestone. If QEMU exits or the watchdog fires, the last stage narrows the
failure domain:

- before `LINUX`: QEMU machine/kernel/initrd/command line;
- `LINUX` only: ramdisk/Android early-init;
- `ANDROID_INIT`: mounts, SELinux, native services;
- `SERVICEMANAGER`: zygote/runtime startup;
- `ZYGOTE`: system_server/framework boot;
- `FRAMEWORK_READY`: P2 PASS.
