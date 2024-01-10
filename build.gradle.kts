plugins {
    java
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.pmdet"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
//    implementation(files("framework.jar"))
    implementation(files("jazzer_standalone.jar"))
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("org.ow2.asm:asm:9.6")
    implementation("commons-cli:commons-cli:1.5.0")
//    implementation("org.ow2.asm:asm-analysis:9.6")
//    implementation("org.ow2.asm:asm-tree:9.6")
//    implementation("org.ow2.asm:asm-commons:9.6")
//    implementation("org.ow2.asm:asm-util:9.6")
}

tasks.jar {
    manifest {
        attributes["Premain-Class"] = "org.pmdet.backend.instrument.agent.InstrumentAgent"
        attributes["Main-Class"] = "org.pmdet.frontend.Main"
    }
//    from(configurations.compileClasspath.get().map { if (it.isDirectory()) it else zipTree(it) })
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    exclude("jazzer_standalone.jar")
}