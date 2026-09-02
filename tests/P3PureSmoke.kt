import com.example.virtualandroid.vm.P2BootStage
import com.example.virtualandroid.vm.P2BootStageClassifier
import com.example.virtualandroid.vm.P2GuestArtifacts
import com.example.virtualandroid.vm.P3QemuArgs
import java.io.File

fun main() {
    var stage = P2BootStage.STARTING
    val sequence = listOf(
        "Booting Linux on physical CPU 0x0" to P2BootStage.LINUX,
        "VA_P2_ANDROID_INIT_OK" to P2BootStage.ANDROID_INIT,
        "VA_P2_SERVICEMANAGER_OK" to P2BootStage.SERVICEMANAGER,
        "VA_P2_ZYGOTE_OK" to P2BootStage.ZYGOTE,
        "VA_P2_FRAMEWORK_OK" to P2BootStage.FRAMEWORK_READY,
    )
    for ((line, expected) in sequence) {
        stage = P2BootStageClassifier.advance(stage, line)
        check(stage == expected) { "stage=$stage expected=$expected" }
    }
    check(P2BootStageClassifier.advance(stage, "Booting Linux") == P2BootStage.FRAMEWORK_READY)

    val a = P2GuestArtifacts(File("kernel"), File("ramdisk"), File("system"), File("userdata"), "sha", 1)
    val args = P3QemuArgs.interactive(a, 2048, 4)
    check(args.windowed(2).any { it[0] == "-vnc" && it[1] == "127.0.0.1:1" })
    check(args.windowed(2).none { it[0] == "-display" && it[1] == "none" })
    check(args.contains("usb-tablet"))
    check(args.contains("usb-kbd"))
    check(args.contains("virtio-gpu-pci"))
    println("P2/P3 pure topology smoke: OK")
}
