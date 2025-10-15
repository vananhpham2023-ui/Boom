plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

sourceSets {
    named("test") {
        java.setSrcDirs(listOf("src/test/java"))
    }
}

dependencies {
    // No external dependencies to keep the module fully offline-compatible.
}

val runModuleTests = tasks.register<JavaExec>("runModuleTests") {
    group = "verification"
    description = "Runs lightweight assertions for the time-tracking module"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.boom.timetracking.TestHarness")
}

tasks.named("check") {
    dependsOn(runModuleTests)
}
