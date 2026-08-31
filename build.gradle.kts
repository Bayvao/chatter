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

tasks.withType<JavaCompile> {
	// sourceCompatibility/targetCompatibility only set the bytecode version;
	// they don't stop code from calling APIs added after 17 when compiled
	// with a newer JDK. --release enforces the actual JDK 17 API surface,
	// which is what CI (running on a real JDK 17) will reject anyway.
	options.release.set(17)
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

	// Presence lives in Redis as TTL keys in deployed environments; the
	// in-memory PresenceStore is the default so tests and a bare `bootRun`
	// need no Redis.
	implementation("org.springframework.boot:spring-boot-starter-data-redis")

	// Web Push (RFC 8030/8291) — VAPID-signed, encrypted payloads sent
	// straight to the browser's push service. No Firebase account needed.
	implementation("nl.martijndwars:web-push:5.1.2") {
		// Ships the deprecated bcprov-jdk15on 1.70, which carries known CVEs.
		exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
	}
	implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
	// web-push exposes both of these on its send() signature but declares them
	// runtime-only: httpcore for the HttpResponse whose status code tells us to
	// retire a dead subscription, jose4j for the checked JoseException.
	implementation("org.apache.httpcomponents:httpcore:4.4.16")
	implementation("org.bitbucket.b_c:jose4j:0.9.6")

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
