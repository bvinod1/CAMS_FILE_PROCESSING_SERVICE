# Data Model — Epic 6: Cloud Portability

## Port Interface Matrix

Every infrastructure concern is accessed only through a port interface. This table is the canonical registry.

| Interface | Local Adapter | GCP Adapter | Location |
|---|---|---|---|
| `SignedUrlPort` | `LocalSignedUrlAdapter` (MinIO presigned) | `GcsSignedUrlAdapter` | ✅ Exists |
| `MessagePublisherPort` | `LoggingMessagePublisher` | `PubSubMessagePublisher` | ✅ Exists (logging fallback) |
| `VirusScanPort` | `ClamAvAdapter` (TCP INSTREAM) | `ClamAvAdapter` (same implementation) | ✅ Exists |
| `ObjectStoragePort` | `LocalObjectStorageAdapter` (MinIO / S3 SDK) | `GcsObjectStorageAdapter` | ❌ To create (E6) |
| `MessageConsumerPort` | `RabbitMqMessageConsumer` (Spring AMQP) | `PubSubMessageConsumer` (Spring Cloud GCP) | ❌ To create (E6) |
| `FileMetadataRepository` | `JpaFileMetadataRepository` (Spring Data JPA) | `SpannerFileMetadataRepository` (Spring Data Spanner) | ❌ To create (E6) |
| `ValidationTemplatePort` | `JpaValidationTemplateRepository` | `SpannerValidationTemplateRepository` | ❌ To create (E3/E6) |
| `ConfigurationPort` | `PostgresConfigurationRepository` | `SpannerConfigurationRepository` | ❌ To create (E7/E6) |
| `SecretPort` | `EnvVarSecretAdapter` | `SecretManagerAdapter` | ❌ To create (E7) |

---

## Profile Activation Matrix

| Profile | Storage | Messaging | Database | Secrets |
|---|---|---|---|---|
| `local` | MinIO (Docker) | RabbitMQ (Docker) | PostgreSQL (Docker) | Environment variables |
| `gcp` | Google Cloud Storage | Cloud Pub/Sub | Cloud Spanner | Secret Manager |
| `aws` (future) | S3 | SNS/SQS | Aurora PostgreSQL | Secrets Manager |

---

## `ObjectStoragePort` Interface

```java
package com.cams.fileprocessing.interfaces;

public interface ObjectStoragePort {
    /**
     * Store a file stream. Returns the storage path (e.g. gs://bucket/path or s3://bucket/path).
     */
    String store(String objectName, InputStream content, long contentLength, String contentType);

    /**
     * Retrieve a file as a stream. Caller is responsible for closing the stream.
     */
    InputStream retrieve(String objectName);

    /**
     * Move an object from source to destination (e.g. quarantine bucket move).
     */
    void move(String sourcePath, String destPath);

    /** Delete an object. */
    void delete(String objectName);

    /** Check if an object exists. */
    boolean exists(String objectName);
}
```

---

## `MessageConsumerPort` Interface

```java
package com.cams.fileprocessing.interfaces;

public interface MessageConsumerPort {
    /**
     * Register a handler for messages on the given topic.
     * The handler receives the raw JSON payload; deserialization is the handler's responsibility.
     */
    <T> void subscribe(String topic, Class<T> eventType, Consumer<T> handler);

    /** Acknowledge a message after successful processing. */
    void acknowledge(String messageId);

    /** Negatively acknowledge — route to DLQ without retry. */
    void nack(String messageId, String reason);
}
```

---

## `FileMetadataRepository` Interface

```java
package com.cams.fileprocessing.interfaces;

public interface FileMetadataRepository {
    FileRecord save(FileRecord record);
    Optional<FileRecord> findById(UUID fileId);
    List<FileRecord> findByStatus(FileStatus status);
    Page<FileRecord> findAll(FileRecordFilter filter, Pageable pageable);

    /**
     * Atomically update file status AND insert a file_status_audit row.
     * Must execute within a single transaction.
     */
    void updateStatus(UUID fileId, FileStatus newStatus, String actor, String reason);
}
```

---

## Contract Test Abstract Base Class Pattern

```
src/test/java/com/cams/fileprocessing/
  contract/
    ObjectStorageContractTest.java          (abstract)
      ├─ LocalObjectStorageContractTest.java  (Testcontainers MinIO)
      └─ GcsObjectStorageContractTest.java   (Testcontainers fake-gcs-server)

    MessageConsumerContractTest.java        (abstract)
      ├─ RabbitMqConsumerContractTest.java   (Testcontainers RabbitMQ)
      └─ PubSubConsumerContractTest.java     (Testcontainers Pub/Sub emulator)

    FileMetadataRepositoryContractTest.java (abstract)
      ├─ JpaRepositoryContractTest.java      (Testcontainers PostgreSQL)
      └─ SpannerRepositoryContractTest.java  (Testcontainers Spanner emulator)
```

---

## ArchUnit Rules (additions to Epic 6)

```java
// Rule: No cloud SDK in business layer
noClasses()
    .that().resideInAPackage("..business..")
    .should().dependOnClassesThat()
    .resideInAnyPackage(
        "com.google.cloud..",
        "software.amazon.awssdk..",
        "com.rabbitmq.."
    )
    .check(classes);

// Rule: No interface package contains implementation
noClasses()
    .that().resideInAPackage("..interfaces..")
    .should().haveSimpleNameEndingWith("Adapter")
    .orShould().haveSimpleNameEndingWith("Repository")
    .check(classes);
```
