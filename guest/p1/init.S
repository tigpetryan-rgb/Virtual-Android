/*
 * Tiny AArch64 /init for the P1 boot proof.
 * No libc, no dynamic linker, no Android userspace dependencies.
 * Linux AArch64 syscall ABI: x8=syscall number, x0..x5=args.
 */

.section .text
.global _start
.type _start, %function
_start:
    // write(1, message, message_len)
    mov     x0, #1
    adr     x1, message
    mov     x2, #message_len
    mov     x8, #64
    svc     #0

1:
    // nanosleep(&ts, NULL)
    adr     x0, ts
    mov     x1, xzr
    mov     x8, #101
    svc     #0
    b       1b

.section .rodata
message:
    .ascii "VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG\n"
.set message_len, . - message

.align 3
ts:
    .quad 3600
    .quad 0
