plugins {
	java
	id("org.springframework.boot") version "3.3.4"
	id("io.spring.dependency-management") version "1.1.6"
}

group = "com.chatter"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

extra["cucumberVersion"] = "7.34.7"
extra["junit-jupiter.version"] = "5.14.2"
extra["jjwtVersion"] = "0.13.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// UUIDv7: time-ordered ids keep B-tree inserts local instead of scattering
	// them the way random UUIDv4 does.
	implementation("com.fasterxml.uuid:java-uuid-generator:5.2.0")

	implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("com.h2database:h2")

	testImplementation("io.cucumber:cucumber-java:${property("cucumberVersion")}")
	testImplementation("io.cucumber:cucumber-spring:${property("cucumberVersion")}")
	testImplementation("io.cucumber:cucumber-junit-platform-engine:${property("cucumberVersion")}")
	testImplementation("org.junit.platform:junit-platform-suite")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
