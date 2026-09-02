TERMUX_PKG_HOMEPAGE=https://www.qemu.org
TERMUX_PKG_DESCRIPTION="Virtual Android P1 minimal AArch64 system emulator"
TERMUX_PKG_LICENSE="GPL-2.0"
TERMUX_PKG_MAINTAINER="Virtual Android prototype"
TERMUX_PKG_VERSION=10.2.1
TERMUX_PKG_SRCURL=https://download.qemu.org/qemu-${TERMUX_PKG_VERSION}.tar.xz
TERMUX_PKG_SHA256=a3717477d8e2c84d630bfffbc20f6cd3293eb45aa1e6dac6d0cc27689991c9e1
TERMUX_PKG_DEPENDS="dtc, glib, libandroid-shmem, libpixman, libslirp, zlib"
TERMUX_PKG_BUILD_IN_SRC=true
TERMUX_PKG_RM_AFTER_INSTALL="
bin/qemu-ga
bin/qemu-img
bin/qemu-io
bin/qemu-nbd
bin/qemu-pr-helper
bin/qemu-storage-daemon
include
libexec
share
"

termux_step_pre_configure() {
    # QEMU/TCG on AArch64 Android currently needs the same setjmp workaround
    # maintained by Termux's full QEMU recipe. Reuse that audited source rather
    # than carrying a fork in this prototype.
    local upstream_builder="$TERMUX_SCRIPTDIR/x11-packages/qemu-system-x86-64"
    if [[ ! -d "$upstream_builder/setjmp-aarch64" ]]; then
        termux_error_exit "Missing Termux QEMU setjmp-aarch64 helper: $upstream_builder"
    fi

    rm -rf "$TERMUX_PKG_BUILDDIR/_lib" "$TERMUX_PKG_BUILDDIR/_setjmp-aarch64"
    mkdir -p "$TERMUX_PKG_BUILDDIR/_lib" "$TERMUX_PKG_BUILDDIR/_setjmp-aarch64/private"
    pushd "$TERMUX_PKG_BUILDDIR/_setjmp-aarch64"
    local s f
    for s in "$upstream_builder"/setjmp-aarch64/{setjmp.S,private-*.h}; do
        f=$(basename "$s")
        cp "$s" "./${f/-//}"
    done
    "$CC" $CFLAGS $CPPFLAGS -I. setjmp.S -c
    "$AR" cru "$TERMUX_PKG_BUILDDIR/_lib/libandroid-setjmp.a" setjmp.o
    popd

    LDFLAGS+=" -L$TERMUX_PKG_BUILDDIR/_lib -l:libandroid-setjmp.a -landroid-shmem -llog -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
    CFLAGS+=" $CPPFLAGS"
    CXXFLAGS+=" $CPPFLAGS"
}

termux_step_configure() {
    termux_setup_ninja

    ./configure \
        --prefix="$TERMUX_PREFIX" \
        --cross-prefix="${TERMUX_HOST_PLATFORM}-" \
        --host-cc=gcc \
        --cc="$CC" \
        --cxx="$CXX" \
        --objcc="$CC" \
        --target-list=aarch64-softmmu \
        --disable-stack-protector \
        --enable-tcg \
        --disable-kvm \
        --disable-xen \
        --disable-hvf \
        --disable-whpx \
        --disable-docs \
        --disable-guest-agent \
        --disable-tools \
        --disable-plugins \
        --disable-modules \
        --enable-vnc \
        --disable-vnc-sasl \
        --disable-vnc-jpeg \
        --disable-sdl \
        --disable-gtk \
        --disable-opengl \
        --disable-curses \
        --disable-spice \
        --disable-usb-redir \
        --disable-libusb \
        --disable-libnfs \
        --disable-libssh \
        --disable-curl \
        --disable-gnutls \
        --disable-nettle \
        --disable-seccomp \
        --disable-vhost-net \
        --disable-vhost-user \
        --disable-vhost-user-blk-server \
        --enable-slirp \
        --disable-bzip2 \
        --disable-lzo \
        --disable-snappy \
        --disable-lzfse \
        --disable-zstd \
        --enable-fdt=system \
        --enable-pixman \
        --enable-trace-backends=nop
}
