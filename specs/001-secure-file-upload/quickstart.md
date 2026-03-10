# Quickstart: US-101 Secure File Upload Initiation

**Purpose**: Guide for setting up and running the backend service for this feature.
**Created**: 2026-03-08
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

-   Java 17+
-   Maven 3.8+
-   Docker
-   Google Cloud SDK (for authentication)

## 2. Local Development Setup

This service uses Google Cloud Spanner and Pub/Sub. For local development, we use the official Google Cloud emulators.

### Running Emulators

1.  **Start the emulators**:
    ```bash
    gcloud beta emulators spanner start &
    gcloud beta emulators pubsub start &
    ```

2.  **Configure Spanner Emulator**:
    You need to create an instance and database in the emulator.
    ```bash
    # Set gcloud to use the emulator
    export SPANNER_EMULATOR_HOST=localhost:9020

    # Create instance
    gcloud spanner instances create test-instance \
      --config=emulator-config --description="Test Instance" --nodes=1

    # Create database
    gcloud spanner databases create file-processing-db --instance=test-instance
    ```

## 3. Application Configuration

Configure the Spring Boot application to connect to the emulators.

**File**: `backend/src/main/resources/application-local.yml`

```yaml
spring:
  cloud:
    gcp:
      spanner:
        instance-id: test-instance
        database: file-processing-db
      # Point to emulators
      project-id: test-project # Dummy project ID for emulators
      credentials:
        encoded-key: "use-anonymous-credentials" # No auth for emulators
      pubsub:
        emulator-host: localhost:8085
  config:
    activate:
      on-profile: local
```

## 4. Building and Running the Service

1.  **Navigate to the backend directory**:
    ```bash
    cd backend
    ```

2.  **Build the project**:
    ```bash
    mvn clean install
    ```

3.  **Run the application with the `local` profile**:
    ```bash
    mvn spring-boot:run -Dspring-boot.run.profiles=local
    ```

The service will be available at `http://localhost:8080`.

## 5. Testing the Endpoint

You can use `curl` to test the `/api/v1/files/upload-request` endpoint.

```bash
curl -X POST http://localhost:8080/api/v1/files/upload-request \
-H "Content-Type: application/json" \
-d '{
  "fileName": "transactions.csv",
  "flowType": "NAV",
  "checksum": "d41d8cd98f00b204e9800998ecf8427e"
}'
```

**Expected Response**:

```json
{
  "fileId": "...",
  "uploadUrl": "https://storage.googleapis.com/..."
}
```
