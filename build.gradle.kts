plugins {
    java
}

group = "com.github.shoothzj"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    compileOnly("org.projectlombok:lombok:1.18.22")
    implementation("org.apache.curator:curator-recipes:5.2.0")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("org.apache.logging.log4j:log4j-core:2.17.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.22")
    testCompileOnly("org.projectlombok:lombok:1.18.22")
    testImplementation("org.apache.zookeeper:zookeeper:3.7.0")
    testImplementation("io.dropwizard.metrics:metrics-core:4.2.7")
    testImplementation("org.xerial.snappy:snappy-java:1.1.8.4")
    testImplementation("commons-io:commons-io:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.assertj:assertj-core:3.21.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}