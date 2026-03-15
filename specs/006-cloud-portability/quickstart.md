# Quickstart: Epic 6 — Cloud Portability

**Purpose**: Guide for running portability tests and verifying the full interface/adapter matrix locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Docker Desktop (running) — portability tests start multiple containers automatically

## 2. Verify the Interface/Adapter Matrix

Check that all required beans are present at startup for the local profile:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local &
sleep 20

# Check all port beans are present
curl -s http://localhost:8080/actuator/beans | jq '.contexts[].beans | keys[]' | grep -i 'port\|adapter\|repository'
```

Expected beans include: `LocalObjectStorageAdapter`, `RabbitMqMessageConsumer`, `JpaFileMetadataRepository`, `LocalSignedUrlAdapter`, `ClamAvAdapter`, etc.

Kill the running app (`fg`, then Ctrl-C) and stop docker-compose before running tests.

## 3. Run Individual Contract Tests

Each contract test starts its own Testcontainers automatically — no manual Docker setup needed.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Object storage contract (MinIO)
mvn test -Dtest=LocalObjectStorageContractTest

# Object storage contract (fake-gcs-server)
mvn test -Dtest=GcsObjectStorageContractTest

# Message consumer contract (RabbitMQ)
mvn test -Dtest=RabbitMqConsumerContractTest

# Message consumer contract (Pub/Sub emulator)
mvn test -Dtest=PubSubConsumerContractTest

# File metadata repository contract (PostgreSQL)
mvn test -Dtest=JpaRepositoryContractTest

# File metadata repository contract (Spanner emulator)
mvn test -Dtest=SpannerRepositoryContractTest
```

Each test should report all abstract base class methods as **PASSED**.

## 4. Run the Portability Integration Tests

These tests run the full pipeline (upload → scan → validate → process) against both profiles. They take ~3–5 minutes each due to container startup.

```bash
# Local profile portability test (PostgreSQL + MinIO + RabbitMQ + ClamAV)
mvn test -Dgroups=portability -Dtest=LocalProfilePortabilityTest

# GCP emulator portability test (Spanner + fake-gcs + Pub/Sub + ClamAV)
mvn test -Dgroups=portability -Dtest=GcpEmulatorPortabilityTest

# Both together
mvn test -Dgroups=portability
```

Expected output: **SAME file transitions and final status** in both test runs.

## 5. Run ArchUnit Rules for Portability

```bash
mvn test -Dtest=PortAdapterArchitectureTest
```

Expected: all rules pass, including:
- No GCP SDK in `business.*` or `features.*`
- No class in `interfaces.*` named `*Adapter` or `*Repository`
- All `infrastructure.gcp.*` classes annotated `@Profile("gcp")`
- All `infrastructure.local.*` classes annotated `@Profile("local")`

## 6. Manual Profile Switch Verification

To confirm the `gcp` profile loads different beans without errors (even without real GCP credentials):

```bash
# Set dummy GCP project to prevent SDK auth failure
export GOOGLE_CLOUD_PROJECT=test-project
mvn spring-boot:run -Dspring-boot.run.profiles=gcp 2>&1 | head -50
# Expected: GcsObjectStorageAdapter, PubSubMessageConsumer, SpannerFileMetadataRepository in bean list
# May exit quickly due to missing emulators — that is expected; we only check bean loading
```

## 7. Run All Tests (Standard + Contract)

```bash
# Excludes portability tests (slow) but includes contract tests
mvn test '-Dgroups=!portability'
```
