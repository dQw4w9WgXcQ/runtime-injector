plugins {
    java
}

group = "dqw4w9wgxcq"
version = "1.0"

repositories {
    mavenCentral()
}

apply<MavenPublishPlugin>()

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.ow2.asm:asm:9.4")
    testImplementation("org.ow2.asm:asm-tree:9.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks {
    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_11
    }

    test {
        useJUnitPlatform()
    }
}

configure<PublishingExtension> {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }
}
