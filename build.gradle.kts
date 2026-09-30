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

// Javadoc coverage is a gate, not a courtesy. Doclint's `missing` group is what turns an
// undocumented public type or method into a failure, and `reference` is what catches a {@link}
// that points at a class the file cannot resolve -- the four that were silently broken before
// this was configured. -Werror is what makes a warning fail the build; without it doclint only
// logs and the gate is decorative.
tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all", "-quiet")
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
