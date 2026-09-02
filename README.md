# Virtual Android

> **READ BEFORE WORK:** This repository is governed by [`AGENTS.md`](./AGENTS.md), [`VIRTUAL_ANDROID_MASTER_PLAN.md`](./VIRTUAL_ANDROID_MASTER_PLAN.md), and [`PROJECT_STATE.json`](./PROJECT_STATE.json). Every new chat/agent/session must read them before changing code, documentation, CI, issues, or release state.

## Canonical product direction

Build a universal-as-practical ARM64 Android APK that runs an isolated secondary AOSP-based Android environment without requiring root or bootloader unlock, using hardware virtualization when safely available and a mandatory software-emulation fallback otherwise.

## Status

The 13 parallel workstreams have archived RESULT artifacts. They are integration inputs, not proof that the release is complete. Canonical integration proceeds strictly by the gates in `VIRTUAL_ANDROID_MASTER_PLAN.md`.
