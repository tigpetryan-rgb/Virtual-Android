plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDirs("../app/src/main/java", "../tests")
        kotlin.include(
            "com/example/virtualandroid/vm/P2BootStage.kt",
            "com/example/virtualandroid/vm/P2GuestArtifacts.kt",
            "com/example/virtualandroid/vm/P2QemuArgs.kt",
            "com/example/virtualandroid/vm/P3QemuArgs.kt",
            "com/example/virtualandroid/display/RfbProtocol.kt",
            "P3PureSmoke.kt",
            "RfbProtocolSmoke.kt",
        )
    }
}

val p3PureSmoke by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("P3PureSmokeKt")
}

val fakeRfbSmoke by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("RfbProtocolSmokeKt")
}

tasks.register("runHostSmokes") {
    dependsOn(p3PureSmoke, fakeRfbSmoke)
}
