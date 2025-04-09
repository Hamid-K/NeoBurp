plugins {
    id("java")
    id("org.openjfx.javafxplugin") version "0.0.13"
}

group = "com.darkcell"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    flatDir {
        dirs("lib")
    }
}

dependencies {
    // Use local JAR references since we're working with a beta version (2025.3)
    compileOnly(files("lib/montoya-api-2025.3.jar"))
    implementation(files("lib/neo4j-java-driver-5.25.0.jar"))
    // Include other required dependencies but don't manage versions directly
    implementation(fileTree("lib") { include("*.jar") })
    
    // Removed external visualization libraries since we're using pure Swing now
    // and loading vis.js from CDN in the HTML
}

// JavaFX configuration kept for reference, will not actually be used
javafx {
    version = "17"
    modules = listOf("javafx.controls", "javafx.web")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
}

tasks.jar {
    // Include all dependencies in the jar (except for montoya-api which is provided by Burp)
    from(configurations.runtimeClasspath.get().filter { 
        it.name.contains("neo4j") || 
        ((!it.name.contains("montoya")) && !it.name.contains("javafx-"))
    }.map { if (it.isDirectory) it else zipTree(it) })
    
    // Add web resources
    from("src/main/resources")
    
    // Avoid signature file conflicts
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes(
            "Extension-Class" to "com.darkcell.burpn2neo.BurpNeo4jExtension",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
    
    // Exclude META-INF signatures
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.test {
    enabled = false  // Disable tests
}

tasks.named("check") {
    dependsOn.clear()  // Remove test task from check
}