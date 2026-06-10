# NCBA Country Info Integration Microservice

A production-style Spring Boot microservice that accepts a country **name** over
REST, resolves it through the public **CountryInfo SOAP service**, persists the
result to **MySQL**, and exposes full **CRUD** over the stored data. Containerised
with Docker and deployable to **Kubernetes**.

> Built for the NCBA *Integration Microservices Engineer* assessment.

### 📄 Standalone documentation (`docs/`)
| Document | Markdown | PDF |
|---|---|---|
| API reference (all endpoints) | [`docs/api-documentation.md`](docs/api-documentation.md) | [`docs/api-documentation.pdf`](docs/api-documentation.pdf) |
| Kubernetes deployment guide (step 9) | [`docs/kubernetes-deployment-guide.md`](docs/kubernetes-deployment-guide.md) | [`docs/kubernetes-deployment-guide.pdf`](docs/kubernetes-deployment-guide.pdf) |
| Kubernetes troubleshooting guide (step 10) | [`docs/kubernetes-troubleshooting-guide.md`](docs/kubernetes-troubleshooting-guide.md) | [`docs/kubernetes-troubleshooting-guide.pdf`](docs/kubernetes-troubleshooting-guide.pdf) |

The Kubernetes deploy/troubleshoot sections below (§7–§8) summarise the
standalone guides; the `docs/` files are the authoritative, self-contained
versions to share.

---

## 1. Architecture Overview

```
            ┌──────────────────────────────────────────────────────────────┐
            │                    Kubernetes (ns: ncba-countryinfo)          │
            │                                                               │
  Client    │   Ingress(nginx)      Service(ClusterIP)     Deployment      │
  ──HTTP──▶ │  countryinfo.local ─▶  countryinfo-app:80 ─▶  2..10 pods      │
            │                                              (HPA on CPU 70%) │
            │                                                  │            │
            │                                   ┌──────────────┼─────────┐  │
            │                                   ▼              ▼         │  │
            │                          StatefulSet:mysql   (each pod)    │  │
            │                          PVC (2Gi)              │          │  │
            └──────────────────────────────────────────────┼──────────┘  │
                                                            │             │
                                                            ▼  SOAP (HTTP)
                              http://webservices.oorsprong.org/.../CountryInfoService.wso
```

**Request flow for `POST /api/countries {"name":"kenya"}`:**

```
Controller ─▶ Service ─▶ SoapCountryClient ──(1) CountryISOCode("Kenya") ─▶ SOAP ─▶ "KE"
                          SoapCountryClient ──(2) FullCountryInfo("KE")   ─▶ SOAP ─▶ {name,capital,...,languages}
              Service ─▶ map to CountryInfo + Language ─▶ upsert by isoCode ─▶ MySQL
Controller ◀─ 201 Created (CountryResponse DTO)
```

### Layered (MVC) structure

```
com.ncba.countryinfo
├── controller   REST controllers (web layer)
├── service      business logic (interface + impl)
├── repository   Spring Data JPA repositories
├── model        JPA entities: CountryInfo, Language
├── dto          request/response payloads
├── client       SoapCountryClient (resilient SOAP integration)
├── soap         JAXB bindings for the SOAP request/response types
├── config       WebServiceTemplate, marshaller, SOAP health indicator
├── exception    custom exceptions + GlobalExceptionHandler
└── util         StringUtils (sentence-case)
```

---

## 2. System Design Decisions & Trade-offs

| Decision | Rationale |
|---|---|
| **Spring-WS `WebServiceTemplate` + JAXB** for SOAP | Strongly-typed, marshalled request/response objects instead of brittle hand-built XML strings. JAXB binding classes are hand-written (rather than `wsimport`-generated) so the build has no codegen step and the wire contract is explicit and reviewable. |
| **Resilience4j for retry + circuit breaker + fallback** | The brief calls for retries, timeouts, circuit breakers and fallbacks. Resilience4j provides all of these as composable annotations driven by `application.yml`, with first-class Micrometer metrics and an Actuator health contributor. It is the maintained successor to Hystrix and composes more cleanly than mixing Spring Retry with a separate breaker (retry wraps the breaker, a single `fallbackMethod` handles exhaustion). **Trade-off:** one extra dependency vs. the built-in `spring-retry`; chosen for the unified, observable resilience model. |
| **Explicit connect/read timeouts** (5s / 10s) on the SOAP message sender | A slow upstream must never tie up request threads indefinitely. Timeouts turn a hang into a fast, retryable failure. |
| **Upsert keyed on `isoCode`** | `isoCode` is the natural key of a country (the DB column is `UNIQUE`). On re-ingest we update the existing row instead of creating duplicates, keeping the dataset clean and the operation idempotent. |
| **Opaque UUID `publicId` as the only API identifier (IDOR mitigation)** | The sequential `BIGINT` primary key is kept internal for efficient indexing/joins but is **never exposed**. All REST paths and responses use a random, non-enumerable `publicId` (UUID). This prevents Insecure Direct Object Reference / record-enumeration attacks where a caller increments `…/1`, `…/2`, … to harvest data. **Trade-off:** a second unique column/index vs. the security and information-hiding benefit of decoupling the public identifier from row order. |
| **DTOs separate from JPA entities** | The wire contract is decoupled from the persistence model, so schema changes don't leak to clients and we avoid lazy-loading serialization pitfalls. |
| **Stateless service + externalised config** | No session/in-memory state — every replica is interchangeable, so we scale horizontally behind the Service/HPA. All config and secrets come from env vars (ConfigMap/Secret), satisfying 12-factor and enabling the same image across environments. |
| **External SOAP dependency excluded from the readiness probe** | A transient upstream outage should not eject pods that can still serve persisted CRUD reads. SOAP health is still reported under `/actuator/health` for monitoring/alerting. |

### Scaling & high-load notes
- **Horizontal scaling:** stateless pods + `HorizontalPodAutoscaler` (2→10 on 70% CPU).
- **Load balancing:** Kubernetes `Service` spreads traffic across replicas; Ingress fronts external traffic.
- **Failure isolation:** per-call timeouts, 3-attempt exponential-backoff retry, and a circuit breaker that fast-fails while the upstream is unhealthy, with a fallback that yields a clean `502`.
- **Future caching/queueing:** ISO-code lookups are highly cacheable (add a Caffeine/Redis cache on `getIsoCode`); ingestion could be made async via a queue if write volume grows. Hooks are isolated in `SoapCountryClient`/`CountryService`.

---

## 3. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| **Docker** + **Docker Compose v2** | recent | Running everything containerised (the easiest path) |
| **JDK 17** | exactly 17 (LTS) | Building/running with Maven. ⚠️ JDK 24+ breaks Lombok annotation processing — use 17. |
| **Maven** | 3.9+ | Building the jar / running tests |
| **kubectl** + a cluster | — | Kubernetes deployment (minikube / kind / Docker Desktop) |
| **MySQL 8** | — | Only if running the app outside Docker without the compose DB |
| `jq` (optional) | — | Pretty-printing JSON in the examples below |

> **JDK version trap (important):** Spring Boot 3.2 + Lombok require **JDK 17**.
> If your machine defaults to a newer JDK (e.g. 24/26), the Maven build fails
> with Lombok errors. Either make 17 the default, or point Maven at it per-build:
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
> # or (Homebrew): export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
> ```
> This does **not** affect the Docker build, which pins `eclipse-temurin-17` internally.

---

## 4. Running the Application

There are three ways to run it. **Option A (Docker Compose) is the recommended,
zero-setup path** — it starts MySQL and the app together.

### Option A — Docker Compose (recommended)

Runs the app + MySQL in containers. No local Java/Maven/MySQL needed.

```bash
# 1. Clone and enter the project
git clone <your-repo-url> country-info-service
cd country-info-service

# 2. Create the env file (defaults work out of the box; edit to change credentials)
cp .env.example .env

# 3. Build the image and start the stack (MySQL starts first, health-gated)
docker compose up --build -d
```

**Verify it started** (the app waits for MySQL to be healthy, ~30–60s on first run):
```bash
docker compose ps            # both 'countryinfo-app' and 'countryinfo-mysql' should be 'healthy'
curl http://localhost:8080/actuator/health    # -> {"status":"UP",...}
```

**What success looks like:** `docker compose ps` shows both containers `Up (healthy)`,
and the health endpoint reports `"status":"UP"` with `db`, `soap`, and
`circuitBreakers` all `UP`.

**Lifecycle commands:**
```bash
docker compose logs -f app       # tail the app's structured JSON logs
docker compose stop              # stop containers (keep data)
docker compose start             # start them again
docker compose down              # stop + remove containers (DB volume preserved)
docker compose down -v           # also wipe the MySQL data volume (fresh DB)
```

### Option B — Maven (app local, MySQL in Docker)

Useful for development with hot reload / debugging.

```bash
# Ensure JDK 17 is active (see the version trap above), then:
export JAVA_HOME=$(/usr/libexec/java_home -v 17)     # macOS example

# 1. Start only MySQL
docker compose up -d mysql

# 2. Point the app at it and run
export DB_HOST=localhost DB_PORT=3306 DB_NAME=countryinfo \
       DB_USERNAME=appuser DB_PASSWORD=apppass
mvn spring-boot:run
```
The app starts on `http://localhost:8080`. Stop it with `Ctrl+C`.

### Option C — Build a runnable jar

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean package                # produces target/country-info-service-1.0.0.jar (runs tests)
# mvn clean package -DskipTests  # skip tests for a faster build

java -jar target/country-info-service-1.0.0.jar       # uses env vars / defaults for DB
```

### Option D — Kubernetes app + external MySQL server

Run the **application on Kubernetes** but back it with a **MySQL server outside
the cluster** — a managed instance (RDS / Cloud SQL / Azure DB), a standalone
VM, or MySQL on your host machine. The in-cluster MySQL StatefulSet is replaced
by an `ExternalName` Service named `mysql`, so the app `Deployment`/`ConfigMap`
need **no changes** — only the data tier is swapped.

```bash
# 1) Point the ExternalName at your MySQL host (edit k8s/external-mysql.yaml):
#      externalName: host.docker.internal           # MySQL on your host (Docker Desktop/kind)
#      externalName: mydb.xxxx.rds.amazonaws.com     # a managed instance
#    (IP-only DB? use the commented EndpointSlice variant in that file.)

# 2) Put the real DB credentials in the Secret (k8s/secret.yaml: DB_USERNAME/DB_PASSWORD).

# 3) Deploy — note: external-mysql.yaml is used INSTEAD OF mysql-deployment.yaml
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/external-mysql.yaml      # <-- external DB (no StatefulSet)
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

kubectl -n ncba-countryinfo rollout status deployment/countryinfo-app
```

On the external MySQL, ensure a user/database matching the Secret exists and is
reachable from the pod network:
```sql
CREATE USER 'appuser'@'%' IDENTIFIED BY 'apppass';
GRANT ALL PRIVILEGES ON countryinfo.* TO 'appuser'@'%';
```
(The app's JDBC URL uses `createDatabaseIfNotExist=true`, so the schema is
auto-created when the user has CREATE privilege.)

> **Verified:** this mode was tested on Docker Desktop's Kubernetes by pointing
> `mysql` at `host.docker.internal` — the app on K8s wrote records to the host
> MySQL successfully (`db` health UP, CRUD working).

### Running on Kubernetes (in-cluster DB)
For the standard deployment (app **and** MySQL inside the cluster), see the
**[Kubernetes Deployment Guide](docs/kubernetes-deployment-guide.md)** (build/load
the image, `kubectl apply` the `k8s/` manifests, verify). A short summary is in §7.

---

## 4a. Smoke-test the running app

Once the app is up on `http://localhost:8080` (any option above), exercise the
full flow. Records are addressed by an **opaque UUID** returned on create:

```bash
# Ingest a country (live SOAP call -> persisted to MySQL) -> 201 Created
curl -s -X POST http://localhost:8080/api/countries \
  -H 'Content-Type: application/json' -d '{"name":"kenya"}' | jq

# Capture the UUID id from a create response and reuse it
ID=$(curl -s -X POST http://localhost:8080/api/countries \
       -H 'Content-Type: application/json' -d '{"name":"tanzania"}' | jq -r .id)

curl -s http://localhost:8080/api/countries | jq          # list all
curl -s http://localhost:8080/api/countries/$ID | jq      # fetch by UUID
curl -s -X PUT http://localhost:8080/api/countries/$ID \
  -H 'Content-Type: application/json' \
  -d '{"name":"United Republic of Tanzania","capitalCity":"Dodoma"}' | jq
curl -i -X DELETE http://localhost:8080/api/countries/$ID # 204 No Content
```

Or import the Postman collection under `postman/` (see §6) and run the
*Country CRUD* folder top-to-bottom.

**Common startup issues**

| Symptom | Cause / fix |
|---|---|
| `docker compose` says `app` keeps restarting | MySQL not healthy yet on first boot — wait ~60s, then `docker compose logs app`. |
| Maven build fails with `lombok`/`NoSuchFieldError` | Wrong JDK — activate JDK 17 (see §3 version trap). |
| `Communications link failure` at startup | DB not reachable — ensure MySQL is running and `DB_HOST/DB_PORT` are correct. |
| `POST` returns `502` | The external SOAP service is unreachable/slow; retries + circuit breaker kicked in. Check `/actuator/health`. |

---

## 5. Running Tests

```bash
mvn test      # unit + integration tests (uses in-memory H2)
mvn verify    # runs tests + JaCoCo report and the 80% line-coverage gate
```

Coverage report (HTML): `target/site/jacoco/index.html`.
The build **fails** if line coverage on the business code drops below **80%**.

---

## 6. API Reference

Base path: `/api/countries`

| Method | Path | Request body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/countries` | `{"name":"kenya"}` | `201 Created` + country | `400` blank name, `404` unknown country, `502` SOAP failure |
| `GET` | `/api/countries` | — | `200 OK` + list | — |
| `GET` | `/api/countries/{id}` | — | `200 OK` + country | `404` not found |
| `PUT` | `/api/countries/{id}` | `CountryUpdateRequest` | `200 OK` + country | `400` blank name, `404` not found |
| `DELETE` | `/api/countries/{id}` | — | `204 No Content` | `404` not found |

> `{id}` is the opaque **UUID** `publicId` returned by `POST`/`GET` — not a sequential integer (see the IDOR mitigation above). Take it from the `id` field of a previous response.

**`CountryResponse`**
```json
{
  "id": "3f1c9b7e-8a2d-4c6f-9e15-2b7a1d0c4e88",
  "name": "Kenya",
  "isoCode": "KE",
  "capitalCity": "Nairobi",
  "phoneCode": "254",
  "continentCode": "AF",
  "currencyISOCode": "KES",
  "countryFlag": "http://.../Flags/Kenya.jpg",
  "createdAt": "2026-06-10T10:00:00",
  "updatedAt": "2026-06-10T10:00:00",
  "languages": [ { "isoCode": "swa", "name": "Swahili" } ]
}
```

**Error envelope** (all handled errors)
```json
{
  "timestamp": "2026-06-10T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Country with id 3f1c9b7e-8a2d-4c6f-9e15-2b7a1d0c4e88 not found",
  "path": "/api/countries/3f1c9b7e-8a2d-4c6f-9e15-2b7a1d0c4e88"
}
```
Validation (`400`) responses additionally include a `fieldErrors` array.

### Postman collection
Import `postman/NCBA-CountryInfo.postman_collection.json` (and optionally
`postman/NCBA-CountryInfo.postman_environment.json`) into Postman.

- The **Ingest country** request captures the returned opaque UUID into the
  `countryId` collection variable, so **Get by id / Update / Delete** chain
  automatically — just run the *Country CRUD* folder top to bottom (or use the
  Collection Runner for the whole collection).
- Folders: **Country CRUD** (happy path), **Error cases** (400/404 + an IDOR
  enumeration probe), **Observability** (actuator endpoints).
- Set `baseUrl` (default `http://localhost:8080`) to point at Docker, a
  port-forwarded pod, or the ingress host.

CLI run (optional, needs Node):
```bash
npx newman run postman/NCBA-CountryInfo.postman_collection.json \
  -e postman/NCBA-CountryInfo.postman_environment.json
```

### Observability endpoints
| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Overall health incl. DB, SOAP reachability, circuit-breaker state |
| `/actuator/health/readiness` | K8s readiness (readiness state + DB) |
| `/actuator/health/liveness` | K8s liveness |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |

---

## 7. Kubernetes Deployment Guide

> Build & push the image first, then update `image:` in `k8s/deployment.yaml`.

```bash
# 0) Build and push the image (example with a local registry / minikube)
docker build -t country-info-service:1.0.0 .
# minikube: eval $(minikube docker-env) && docker build -t country-info-service:1.0.0 .
# registry: docker tag country-info-service:1.0.0 <registry>/country-info-service:1.0.0 && docker push <registry>/country-info-service:1.0.0

# 1) Namespace
kubectl apply -f k8s/namespace.yaml

# 2) Config + secret
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

# 3) Database (StatefulSet + headless Service + PVC)
kubectl apply -f k8s/mysql-deployment.yaml
kubectl -n ncba-countryinfo rollout status statefulset/mysql

# 4) Application
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl -n ncba-countryinfo rollout status deployment/countryinfo-app

# 5) Autoscaling + ingress (requires metrics-server and an ingress controller)
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/ingress.yaml
```

### Verify each step
```bash
kubectl -n ncba-countryinfo get pods,svc,statefulset,deploy,hpa,ingress
kubectl -n ncba-countryinfo describe deployment countryinfo-app
kubectl -n ncba-countryinfo logs deploy/countryinfo-app --tail=100
kubectl -n ncba-countryinfo get endpoints countryinfo-app   # should list pod IPs

# Reach the API without an ingress:
kubectl -n ncba-countryinfo port-forward svc/countryinfo-app 8080:80
curl -X POST localhost:8080/api/countries -H 'Content-Type: application/json' -d '{"name":"kenya"}'

# With ingress (add to /etc/hosts: <ingress-ip> countryinfo.local):
curl -X POST http://countryinfo.local/api/countries -H 'Content-Type: application/json' -d '{"name":"kenya"}'
```

---

## 8. Kubernetes Troubleshooting Guide

**Pod in `CrashLoopBackOff`**
```bash
kubectl -n ncba-countryinfo logs <pod> --previous       # logs from the crashed instance
kubectl -n ncba-countryinfo describe pod <pod>          # events at the bottom
```
Usual causes: DB unreachable at startup, bad env var, OOMKilled (raise memory limit / check `JAVA_OPTS`).

**Pod stuck in `Pending`**
```bash
kubectl -n ncba-countryinfo describe pod <pod>          # "Insufficient cpu/memory" or unbound PVC
kubectl -n ncba-countryinfo get pvc                     # STATUS should be Bound
kubectl get nodes -o wide
```
Causes: no node has the requested CPU/memory; PVC can't bind (no default StorageClass) → install/define a StorageClass or lower the request.

**Service unreachable / no response**
```bash
kubectl -n ncba-countryinfo get endpoints countryinfo-app   # empty = selector/label mismatch or no ready pods
kubectl -n ncba-countryinfo get pods --show-labels          # labels must match the Service selector
```
Check `Service.targetPort (8080)` matches the container port, and that readiness is passing (only Ready pods appear in endpoints).

**DB connection refused**
```bash
kubectl -n ncba-countryinfo get pods -l app=mysql
kubectl -n ncba-countryinfo logs statefulset/mysql
kubectl -n ncba-countryinfo get secret countryinfo-secret -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
kubectl -n ncba-countryinfo get configmap countryinfo-config -o yaml   # DB_HOST must be "mysql"
```
Confirm the Secret/ConfigMap values match what MySQL was initialised with, and that the `mysql` Service exists.

**High memory/CPU**
```bash
kubectl -n ncba-countryinfo get hpa                     # current vs target utilisation, replica count
kubectl -n ncba-countryinfo top pods                    # needs metrics-server
kubectl -n ncba-countryinfo describe hpa countryinfo-app
```
If the HPA shows `<unknown>` targets, metrics-server isn't installed. Tune requests/limits or HPA thresholds.

**Rolling update stuck**
```bash
kubectl -n ncba-countryinfo rollout status deployment/countryinfo-app
kubectl -n ncba-countryinfo get pods                    # new pods not becoming Ready?
kubectl -n ncba-countryinfo describe pod <new-pod>      # failing readiness probe?
kubectl -n ncba-countryinfo rollout undo deployment/countryinfo-app   # roll back
```
With `maxUnavailable: 0`, a failing readiness probe halts the rollout (old pods keep serving) — fix the probe/image, or roll back.

---

## 9. Configuration Reference (env vars)

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `countryinfo` | MySQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | MySQL credentials (from Secret in k8s) |
| `JPA_DDL_AUTO` | `update` | Hibernate schema management |
| `SOAP_ENDPOINT` | oorsprong CountryInfoService URL | SOAP service endpoint |
| `SOAP_CONNECT_TIMEOUT_MS` / `SOAP_READ_TIMEOUT_MS` | `5000` / `10000` | SOAP client timeouts |
| `SPRING_PROFILES_ACTIVE` | — | set to `prod` for JSON structured logs |
| `LOG_LEVEL_SOAP` | `INFO` | set to `DEBUG` to log full SOAP envelopes |

---

## 10. Tech Stack
Java 17 · Spring Boot 3.2 · Spring Web · Spring Data JPA · Spring Web Services (JAXB) ·
Resilience4j · Micrometer/Prometheus · Spring Boot Actuator · MySQL 8 · Lombok ·
JUnit 5 / Mockito / MockMvc · JaCoCo · Docker · Kubernetes.
