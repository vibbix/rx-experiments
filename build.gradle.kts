plugins {
    id("java")
}

group = "com.kayak.xp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.javafaker:javafaker:1.0.2")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.16.0"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-annotations")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation(platform("io.projectreactor:reactor-bom:2023.0.0"))
    implementation("io.projectreactor:reactor-core")
    implementation("io.projectreactor:reactor-tools")

    implementation("io.projectreactor.addons:reactor-extra")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.test {
    useJUnitPlatform()
}
