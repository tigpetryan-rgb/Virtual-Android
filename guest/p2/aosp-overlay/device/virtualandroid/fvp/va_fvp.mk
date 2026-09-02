# Virtual Android P2 product overlay.
# Reuses AOSP's upstream fvpbase QEMU hardware contract and only adds
# deterministic prototype markers/properties.

$(call inherit-product, device/generic/goldfish/fvpbase/fvp.mk)

PRODUCT_NAME := va_fvp
PRODUCT_DEVICE := fvpbase
PRODUCT_BRAND := VirtualAndroid
PRODUCT_MODEL := Virtual Android AOSP Guest
PRODUCT_MANUFACTURER := OpenAI-Prototype

PRODUCT_SYSTEM_PROPERTIES += \
    persist.virtualandroid.p2=1

PRODUCT_COPY_FILES += \
    device/virtualandroid/fvp/init.virtualandroid.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.virtualandroid.rc
