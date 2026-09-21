# Agent Notes: Local Development

## Requirements

This repository is a Java 21 / Spring Boot 3.2.x microservices project built with Gradle. Each service has its own Gradle wrapper.

- **Java 21** (JDK) — required by all modules. Gradle 8.6 does not run on Java 25+, so Java 21 is the safest choice.
- **Docker / Docker Compose** (optional) — only needed to run the full stack via `docker-compose/`.

## macOS setup

Install Java 21 via Homebrew and symlink it so system wrappers can find it:

```bash
brew install openjdk@21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

Set `JAVA_HOME` for each shell session (or add it to `~/.zshrc`):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Verify:

```bash
java -version  # should report 21.x
```

## Running tests

There is no top-level Gradle project. Run tests from within each service directory.

For the main service with the majority of the tests:

```bash
cd core-banking-service
./gradlew test
```

Other services also have smoke tests and can be checked with:

```bash
cd internet-banking-api-gateway && ./gradlew test
cd internet-banking-config-server && ./gradlew test
cd internet-banking-service-registry && ./gradlew test
cd internet-banking-user-service && ./gradlew test
cd internet-banking-fund-transfer-service && ./gradlew test
cd internet-banking-utility-payment-service && ./gradlew test
```

All current tests are plain JUnit 5 / Mockito plus `@SpringBootTest` `contextLoads` smoke tests on H2; no external database or Docker is required for `./gradlew test`. Repository and integration tests (MySQL via Testcontainers) belong to the separate `integrationTest` task described in `TESTING.md`.

The testing strategy — layers, frameworks, mocking rules, integration and e2e patterns shared by every module — is defined in [`TESTING.md`](TESTING.md). New or touched tests must follow it (short form: `.agents/skills/banking-test-patterns/SKILL.md`).

## Verification checklist

After any change, at minimum run:

```bash
cd core-banking-service && ./gradlew test
```

A green `test` task is the verification gate for this repository.
