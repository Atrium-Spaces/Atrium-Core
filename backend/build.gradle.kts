plugins {
	java
	id("org.springframework.boot") version "3.+"
	id("io.spring.dependency-management") version "1.+"
	id("io.freefair.lombok") version "+"
}

group = "org.atrium"
version = project.version

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation("org.jspecify:jspecify:+")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

tasks.withType<AbstractArchiveTask> {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
}

tasks.test {
	useJUnitPlatform()
	testLogging { showStandardStreams = true }
}

tasks.javadoc {
	// Suppress "missing" doclint only (generated classes don't need javadoc)
	(options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.withType<JavaCompile> {
	// CODE_STYLES.md §3.13: enable all linting with specific suppressions
	options.compilerArgs.addAll(
		listOf(
			"-Xlint:all",
			"-Xlint:-serial",     // No Java serialization
			"-Xlint:-processing", // Lombok annotation processor noise
			"-Xlint:-this-escape" // Safe: schema constructor pattern
		)
	)
}
