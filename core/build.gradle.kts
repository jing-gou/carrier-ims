plugins {
    `java-library`
}

// Pure JVM module: no Android types, so every rule here is unit-testable without a device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
