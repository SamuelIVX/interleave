plugins {
    `java-library`
    `maven-publish`
    java
    application
}

group = "dev.samhb.interleave"
version = "1.0-SNAPSHOT"
description = "interleave — explicit-state model checker for concurrent programs"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "dev.samhb.interleave.cli.Main"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(26)
}

tasks.withType<Test> {
    systemProperty("file.encoding", Charsets.UTF_8.name())
}

// Javadoc *correctness* is a gate. Javadoc *coverage* is not yet, and conflating the two is
// how gates get switched off. `reference` is the group that catches a {@link} pointing at a
// class the file cannot resolve -- the four that were silently broken before this was
// configured -- and `syntax`/`html` catch malformed tags. -Werror is what makes a warning fail
// the build; without it doclint only logs and the gate is decorative.
//
// `missing` is deliberately NOT enabled yet. Measured on this branch, enabling it surfaces 282
// undocumented public/protected members across 78 files, 173 of them public. Doclint checks the
// source AST rather than only the emitted docs, so it also flags 48 private members javadoc
// would never emit. Enabling it now would fail the build on the entire pre-existing backlog
// while this change documents 9 files, and the fastest way to get a green build would be to
// delete the gate -- exactly the outcome to avoid. Re-enable as `Xdoclint:all,-quiet` once the
// backlog reaches zero.
tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:reference,syntax,html", "-quiet")
        addBooleanOption("Werror", true)
        // javadoc stops reporting after 100 warnings by default and says nothing, so a large gap
        // silently presents as a complete report of exactly 100. Raise the cap so the gate sees
        // the whole backlog.
        addStringOption("Xmaxwarns", "10000")
        encoding = "UTF-8"
    }
    isFailOnError = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "dev.samhb.interleave"
            artifactId = "interleave"
            version = "1.0-SNAPSHOT"
        }
    }
    repositories {
        mavenLocal()
    }
}
