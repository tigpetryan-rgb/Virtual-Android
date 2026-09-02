# Universal Virtual Android — technical architecture v0.1

## Non-negotiable platform fact

A normal third-party Android APK cannot assume direct KVM/AVF access on stock devices.
The universal path must therefore be a user-space system emulator. Hardware acceleration is an optional privileged/OEM/dev path.

## Execution tiers

### Tier 0 — Stock APK / universal fallback

Host app sandbox
→ Native VMM process/library
→ QEMU-style ARM64 system emulation using TCG
→ `virt` machine
→ custom AOSP ARM64 guest

Properties:
- no root
- no bootloader unlock
- no `/dev/kvm` dependency
- works across ARM64 devices if native JIT/emulation prerequisites pass
- slowest path
- guest compromise is NOT a hypervisor-grade boundary because the emulator itself runs inside the host app sandbox

### Tier 1 — AVF privileged/preinstalled/dev path

Host UI APK
→ Android Virtualization Framework
→ virtualizationservice/crosvm/pKVM
→ Microdroid or allowed custom VM payload

Properties:
- hardware-backed isolation/performance
- requires restricted platform permissions
- not a general Play-installed third-party path

### Tier 2 — rooted/unlocked/vendor-specific KVM

Excluded from the universal product requirement. Can be supported later as an enthusiast backend.

## Host architecture

```
+-------------------------------------------------------+
| Android host OS                                       |
|                                                       |
|  +---------------- APK sandbox --------------------+  |
|  | UI / lifecycle / policy                         |  |
|  |   |                                             |  |
|  | CapabilityProbe                                 |  |
|  |   |                                             |  |
|  | EngineSelector                                  |  |
|  |   +--> SoftwareTcgEngine -------------------+   |  |
|  |   |      QEMU/TCG ARM64                      |   |  |
|  |   |      user-mode networking                |   |  |
|  |   |      qcow2/raw virtual disks             |   |  |
|  |   |      framebuffer/virtio-gpu bridge       |   |  |
|  |   +--> AvfEngine (restricted)                |   |  |
|  +-------------------------------------------------+  |
+-------------------------------------------------------+
                         |
                         v
+-------------------------------------------------------+
| Guest Android                                         |
|  custom kernel for QEMU `virt`                        |
|  system/vendor/product images                         |
|  guest init + servicemanager + SurfaceFlinger         |
|  privileged AI agent (guest-only)                     |
|  agent broker / policy daemon                         |
+-------------------------------------------------------+
```

## Guest virtual hardware (software path)

Start minimal and deterministic:
- Machine: ARM64 `virt`
- CPU: emulated ARMv8-A, initially `max`/compatible CPU model
- RAM: 512 MiB–3 GiB dynamically selected by host policy
- vCPU: 1–4
- Storage: virtio-blk, base image read-only + writable userdata overlay
- Network: virtio-net + user-mode NAT (SLIRP/libslirp-like), no TUN requirement
- Console: virtio-console/serial first
- Input: virtio-input later
- Graphics: virtio-gpu 2D first; host Surface bridge later
- Audio: deferred until Android boot/UI is stable

## Why user-mode networking

Untrusted Android apps cannot rely on opening `/dev/tun`. Therefore the universal backend should perform NAT entirely in userspace. Guest ports can be selectively forwarded to loopback sockets owned by the host app.

## Guest build strategy

Do not begin by trying to boot a random phone ROM or GSI. A phone vendor image expects that phone's HALs and hardware.

Build a dedicated AOSP product for the emulated machine:
1. ARM64 Linux kernel configured for QEMU `virt` and virtio devices.
2. Minimal Android ramdisk/init.
3. Custom vendor partition with virtual HAL implementations/stubs.
4. AOSP system/product partitions.
5. Guest-only platform signing keys for privileged components.

The first Android milestone should be headless boot to `sys.boot_completed=1`, not graphics.

## Privileged AI agent inside guest

Because we control the guest image, the agent can be a guest system component without granting host privileges.

Recommended split:
- `ai-agentd`: native/system daemon with narrowly-scoped SELinux domain.
- `AgentBroker`: Binder service exposed only inside guest.
- optional platform-signed `priv-app` UI/service.
- policy file declaring exactly which Android services/actions the agent may call.
- audit log written to a dedicated guest partition/file.

Avoid putting the model runtime directly into `system_server`. Keep crashes and model memory pressure isolated from core Android services.

## Host↔guest control plane

For software emulation, expose a private local transport rather than ADB as the production API.

Prototype:
- serial console for boot logs
- hostfwd TCP socket for ADB/debug only

Production:
- virtio-serial custom framed protocol or vsock-equivalent implemented by the VMM
- authenticated session handshake
- commands: boot state, clipboard, input, file import/export, agent RPC, shutdown/snapshot

## Storage model

```
base-system.img   read-only
base-vendor.img   read-only
userdata.img      writable per virtual device
metadata.img      writable
snapshot/overlay  optional copy-on-write layer
```

Never map host `/data` into the guest. File sharing should be explicit through a broker.

## Isolation semantics

The software backend creates strong logical separation but not the same security boundary as pKVM/KVM.
A guest kernel exploit must still escape the emulator to reach the host app process, and then it remains inside the Android app sandbox; nevertheless emulator escape bugs are possible.

The AVF/pKVM backend is the route for hardware-enforced VM isolation where platform permissions are available.

## Prototype milestones

### P0 — capability probe (this repository)
- ARM64/SDK/RAM/CPU detection
- AVF feature detection
- restricted permission detection
- `/dev/kvm`, `/dev/tun`, `/dev/vhost-vsock` probes
- anonymous executable-memory transition probe
- deterministic engine selection

### P1 — native emulator boots Linux
- integrate/build minimal QEMU/TCG backend for Android host
- boot an ARM64 Linux kernel + initramfs
- capture serial log in APK
- user-mode NAT
- start/stop cleanly

Acceptance: kernel reaches shell on at least 3 ARM64 vendor devices without root.

### P2 — headless AOSP guest
- boot custom Android kernel/ramdisk
- servicemanager, zygote, system_server
- ADB/debug transport

Acceptance: `sys.boot_completed=1` headless.

### P3 — display/input
- virtio-gpu 2D framebuffer
- Surface/ANativeWindow presentation
- touch/key input translation

### P4 — guest AI system agent
- `ai-agentd` + Binder broker
- guest SELinux policy
- audited privileged actions

### P5 — accelerated backend
- separate AVF-enabled flavor for preinstalled/dev/OEM environments
- same high-level VmEngine interface

## Hard constraints to preserve

1. Never require root for Tier 0.
2. Never require host bootloader unlock for Tier 0.
3. Do not depend on TUN, KVM, raw sockets, mount namespaces, or host system Binder privileges in Tier 0.
4. Treat AVF availability and AVF permission as separate facts.
5. Keep the guest image independent of the host OEM Android image.


## Current executable milestone

P1 is specified in `docs/P1_BRINGUP.md`. Its acceptance condition is a stock-app QEMU/TCG boot of an ARM64 Linux kernel until the tiny guest `/init` prints `VA_P1_GUEST_OK`.
