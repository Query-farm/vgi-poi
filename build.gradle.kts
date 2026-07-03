import java.util.zip.ZipFile

plugins {
    java
    application
    // Fat/shaded JAR: `./gradlew shadowJar` -> build/libs/vgi-poi-<ver>-all.jar
    id("com.gradleup.shadow") version "9.4.3"
}

group = "farm.query"
version = "0.1.0-SNAPSHOT"

repositories {
    // The VGI Java SDK is published to Maven Central as farm.query:vgi /
    // farm.query:vgirpc, so the build is fully self-contained — no mavenLocal,
    // no sibling checkout, no composite build.
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all,-serial,-processing",
            "-parameters", // ScalarFn reflects parameter names off @Vector/@Const/@Setting
        )
    )
    options.encoding = "UTF-8"
}

val poiVersion = "5.5.1"

dependencies {
    // VGI Java SDK from Maven Central. `vgi` is the worker/catalog API and pulls
    // in farm.query:vgirpc transitively; vgirpc is declared explicitly because
    // the code imports farm.query.vgirpc.* directly.
    implementation("farm.query:vgi:0.10.0")
    implementation("farm.query:vgirpc:0.12.0")

    // Apache POI — Apache-2.0. `poi` reads/writes the legacy .xls (HSSF) format;
    // `poi-ooxml` adds the .xlsx (XSSF) format and pulls in xmlbeans + commons.
    implementation("org.apache.poi:poi:$poiVersion")
    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    implementation("org.slf4j:slf4j-simple:2.0.16")
    // POI's logging fronts on Log4j 2 API (org.apache.logging.log4j). Without a
    // provider, Log4j's StatusLogger prints "could not find a logging provider"
    // to System.out — which corrupts the stdio Arrow-IPC transport and hangs the
    // worker. Bridge Log4j 2 -> SLF4J -> slf4j-simple (stderr) so NOTHING ever
    // writes to stdout. (slf4j-simple defaults all output to System.err.)
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.24.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("farm.query.vgi.poi.Main")
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

// Concatenate every dependency's META-INF/services/* SPI file into a generated
// resources dir, stripping comment lines. POI's `poi` (HSSF) and `poi-ooxml`
// (XSSF) jars EACH ship a `WorkbookProvider` service file, and shadow's
// mergeServiceFiles() collapsed them to a single entry (only XSSFWorkbookFactory
// survived) — which silently made .xls (OLE2/HSSF) reads fail with
// "InputStream was neither an OLE2 stream, nor an OOXML stream". The POI service
// files carry a long Apache license header that confuses the naive merger, so we
// pre-merge cleanly here. Same fix pattern vgi-tika uses for Tika's parsers.
val generatedSpiDir = layout.buildDirectory.dir("generated/spi")
val generateMergedSpi = tasks.register("generateMergedSpi") {
    val runtime = configurations.named("runtimeClasspath")
    inputs.files(runtime)
    outputs.dir(generatedSpiDir)
    doLast {
        val servicesByName = linkedMapOf<String, LinkedHashSet<String>>()
        runtime.get().files.filter { it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zf ->
                zf.entries().asSequence()
                    .filter { e ->
                        !e.isDirectory && e.name.startsWith("META-INF/services/") &&
                            e.name.removePrefix("META-INF/services/").isNotEmpty()
                    }
                    .forEach { e ->
                        val svc = e.name.removePrefix("META-INF/services/")
                        val lines = zf.getInputStream(e).bufferedReader().readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                        if (lines.isNotEmpty()) {
                            servicesByName.getOrPut(svc) { linkedSetOf() }.addAll(lines)
                        }
                    }
            }
        }
        val outRoot = generatedSpiDir.get().dir("META-INF/services").asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()
        servicesByName.forEach { (svc, impls) ->
            outRoot.resolve(svc).writeText(impls.joinToString("\n", postfix = "\n"))
        }
        logger.lifecycle("generateMergedSpi: merged ${servicesByName.size} service files "
            + "(WorkbookProvider impls=${servicesByName["org.apache.poi.ss.usermodel.WorkbookProvider"]?.size ?: 0})")
    }
}

tasks.shadowJar {
    archiveBaseName.set("vgi-poi")
    archiveClassifier.set("all")
    dependsOn(generateMergedSpi)
    // The pre-merged SPI files override the per-jar ones; mergeServiceFiles()
    // still concatenates anything they miss.
    from(generatedSpiDir)
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "farm.query.vgi.poi.Main",
            "Multi-Release" to "true",
            // Arrow's off-heap MemoryUtil needs java.nio reflectively opened. Bake
            // it into the manifest so a bare `java -jar vgi-poi-all.jar` works as a
            // VGI LOCATION without the caller having to pass --add-opens.
            "Add-Opens" to "java.base/java.nio",
        )
    }
}

// Make `build` produce the fat jar.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

// Regenerate the committed SQL E2E fixtures (test/sql/data/*) from the same POI
// builders the JUnit tests use. The Makefile `test-sql` target runs this before
// haybarn-unittest so fixtures are reproducible from source rather than opaque
// committed binaries.
tasks.register<JavaExec>("generateSqlFixtures") {
    group = "verification"
    description = "Generate test/sql/data fixtures (sample.xlsx, sample.xls, garbage.bin)."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("farm.query.vgi.poi.SqlFixtureGenerator")
    args(layout.projectDirectory.dir("test/sql/data").asFile.absolutePath)
}
