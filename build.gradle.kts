import com.diffplug.spotless.LineEnding

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "7.0.4"
    checkstyle
}

group = "io.github.mit37"
version = "2.0.0-SNAPSHOT"
description = "Offline speech-to-notes in Java: on-device Vosk transcription, optional Gemini-formatted notes, and screenshot Q&A."

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    // Stays on the JavaFX 21 line to match the Java 21 toolchain (PRD §4).
    version = "21.0.12"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    // Speech-to-text, on-device (G1).
    implementation("com.alphacephei:vosk:0.3.45")
    // JSON: model config, eval reports, Gemini payloads (G3/G4).
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    // MP3 decoding through the javax.sound.spi that ships in this artifact (PRD G1, file mode).
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    // PDF export (G5, milestone 7).
    implementation("com.github.librepdf:openpdf:2.2.2")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.24.0")
    // Verifies exported PDFs by reading their text back (milestone 7).
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    // Headless UI tests: TestFX drives the real scene graph, Monocle provides the display (M4).
    // TestFX's robot API is typed with Hamcrest matchers, so the robot needs it on the classpath.
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.testfx:openjfx-monocle:21.0.2")
    // TestFX + Monocle (headless JavaFX) arrive with the UI in milestone 4.
}

application {
    // CLI-first entry point; the JavaFX UI is launched from an explicit mode (milestone 4).
    mainClass = "io.github.mit37.speechnotes.CliMain"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // No microphone and no display here: every test must be headless (docs/PLAN.md).
    systemProperty("java.awt.headless", "true")
    // CI sets -Dspeechnotes.requireModel=true so a missing model fails instead of skipping.
    systemProperty("speechnotes.requireModel", System.getProperty("speechnotes.requireModel", "false"))
    // Never let an automated run sit on a live microphone: bound "record until stopped" in tests.
    systemProperty("speechnotes.mic.maxSeconds", System.getProperty("speechnotes.mic.maxSeconds", "3"))
    // Golden files are only rewritten when explicitly asked for (scripts/regen_golden.sh).
    systemProperty("speechnotes.updateGolden", System.getProperty("speechnotes.updateGolden", "false"))
    // Headless JavaFX: Monocle gives the toolkit a display that is not a display.
    systemProperty("testfx.headless", "true")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("prism.order", "sw")
    systemProperty("prism.verbose", "false")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
    maxWarnings = 0
}

spotless {
    // LF everywhere: the default is platform-native, which would pass on Windows and fail on CI.
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        lineEndings = LineEnding.UNIX
    }
    // The PRD/STANDARDS/AGENTS/CLAUDE copies stay untouched; only files this repo owns.
    format("misc") {
        target(
            "README.md",
            "docs/*.md",
            ".github/workflows/*.yml",
            "scripts/*.sh",
            ".gitignore",
            ".env.example",
        )
        trimTrailingWhitespace()
        endWithNewline()
        lineEndings = LineEnding.UNIX
    }
}

// `./gradlew check` runs formatting and static analysis as well as tests.
tasks.named("check") {
    dependsOn("spotlessCheck")
    dependsOn("checkstyleMain", "checkstyleTest")
}

// The eval run behind `scripts/run_eval.sh`: offline measurement, report written by the code.
tasks.register<JavaExec>("evalRun") {
    group = "verification"
    description = "Measures WER, real-time factor and keyword recall on the committed fixtures (offline)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "io.github.mit37.speechnotes.eval.EvalMain"
}
