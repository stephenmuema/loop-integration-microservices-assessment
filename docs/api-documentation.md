---
title: "NCBA Country Info Service — API Documentation"
subtitle: "REST endpoint reference"
author: "NCBA Integration Microservices"
---

# API Documentation

REST API for the **Country Info integration microservice**. Given a country
name, the service resolves it through the CountryInfo SOAP API, persists the
result to MySQL, and serves CRUD operations over the stored data.

- **Base URL (local/Docker):** `http://localhost:8080`
- **Media type:** `application/json`
- **Resource identifier:** an opaque **UUID** (`id`). The internal sequential
  database key is never exposed — this prevents IDOR / record-enumeration
  attacks. Always use the `id` returned by a previous response.

---

## Endpoint summary

| # | Method | Path | Description | Success |
|---|---|---|---|---|
| 1 | `POST` | `/api/countries` | Ingest a country by name (via SOAP) and persist it | `201 Created` |
| 2 | `GET` | `/api/countries` | Fetch all countries | `200 OK` |
| 3 | `GET` | `/api/countries/{id}` | Fetch a country by its UUID | `200 OK` |
| 4 | `PUT` | `/api/countries/{id}` | Update a country by its UUID | `200 OK` |
| 5 | `DELETE` | `/api/countries/{id}` | Delete a country by its UUID | `204 No Content` |

**Observability (Spring Boot Actuator):**

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Overall health (DB, SOAP, circuit breaker) |
| `GET` | `/actuator/health/readiness` | Kubernetes readiness probe |
| `GET` | `/actuator/health/liveness` | Kubernetes liveness probe |
| `GET` | `/actuator/metrics` | Micrometer metrics |
| `GET` | `/actuator/prometheus` | Prometheus scrape endpoint |
| `GET` | `/actuator/info` | Build/app info |

---

## Data model

### CountryResponse
| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | Opaque public identifier |
| `name` | string | Country name (sentence-cased) |
| `isoCode` | string | ISO country code (e.g. `KE`) — unique |
| `capitalCity` | string | |
| `phoneCode` | string | International dialling code |
| `continentCode` | string | e.g. `AF` |
| `currencyISOCode` | string | e.g. `KES` |
| `countryFlag` | string (URL) | Flag image URL |
| `createdAt` | string (ISO-8601) | |
| `updatedAt` | string (ISO-8601) | |
| `languages` | array | List of `{ isoCode, name }` |

---

## 1. Ingest a country — `POST /api/countries`

Resolves the country name to an ISO code and full detail via SOAP, then
**upserts** the record (keyed on ISO code: updates if it already exists,
otherwise inserts). The name is converted to sentence case server-side, so
`"kenya"`, `"KENYA"`, and `"kEnYa"` all resolve to `Kenya`.

**Request body**
```json
{ "name": "kenya" }
```

| Field | Type | Required | Rule |
|---|---|---|---|
| `name` | string | yes | Must not be blank |

**`curl`**
```bash
curl -i -X POST http://localhost:8080/api/countries \
  -H 'Content-Type: application/json' \
  -d '{"name":"kenya"}'
```

**`201 Created`**
```json
{
  "id": "54bdedee-cb2e-4efa-955e-f355f4ba0cbf",
  "name": "Kenya",
  "isoCode": "KE",
  "capitalCity": "Nairobi",
  "phoneCode": "254",
  "continentCode": "AF",
  "currencyISOCode": "KES",
  "countryFlag": "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
  "createdAt": "2026-06-10T12:37:00.233",
  "updatedAt": "2026-06-10T12:37:00.233",
  "languages": [ { "isoCode": "swa", "name": "Swahili" } ]
}
```

**Errors**

| Status | When |
|---|---|
| `400 Bad Request` | `name` is blank/missing |
| `404 Not Found` | The country name cannot be resolved to an ISO code |
| `502 Bad Gateway` | The SOAP service is unavailable / timed out / circuit breaker open |

---

## 2. Fetch all countries — `GET /api/countries`

**`curl`**
```bash
curl http://localhost:8080/api/countries
```

**`200 OK`**
```json
[
  {
    "id": "54bdedee-cb2e-4efa-955e-f355f4ba0cbf",
    "name": "Kenya",
    "isoCode": "KE",
    "capitalCity": "Nairobi",
    "phoneCode": "254",
    "continentCode": "AF",
    "currencyISOCode": "KES",
    "countryFlag": "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
    "createdAt": "2026-06-10T12:37:00.233",
    "updatedAt": "2026-06-10T12:37:00.233",
    "languages": [ { "isoCode": "swa", "name": "Swahili" } ]
  }
]
```
Returns `[]` when no records exist.

---

## 3. Fetch a country by id — `GET /api/countries/{id}`

`{id}` is the opaque UUID from a previous response.

**`curl`**
```bash
curl http://localhost:8080/api/countries/54bdedee-cb2e-4efa-955e-f355f4ba0cbf
```

**`200 OK`** — same shape as a single `CountryResponse` (see §1).

**Errors**

| Status | When |
|---|---|
| `404 Not Found` | No record with that id |

---

## 4. Update a country — `PUT /api/countries/{id}`

Updates the mutable fields of an existing record. The ISO code is the natural
key and is not editable here. `name` is sentence-cased server-side. If
`languages` is provided, it **replaces** the existing list.

**Request body**
```json
{
  "name": "republic of kenya",
  "capitalCity": "Nairobi City",
  "phoneCode": "254",
  "continentCode": "AF",
  "currencyISOCode": "KES",
  "countryFlag": "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
  "languages": [
    { "isoCode": "swa", "name": "Swahili" },
    { "isoCode": "eng", "name": "English" }
  ]
}
```

| Field | Type | Required | Rule |
|---|---|---|---|
| `name` | string | yes | Must not be blank |
| `capitalCity`, `phoneCode`, `continentCode`, `currencyISOCode`, `countryFlag` | string | no | |
| `languages` | array | no | Replaces existing languages when present |

**`curl`**
```bash
curl -i -X PUT http://localhost:8080/api/countries/54bdedee-cb2e-4efa-955e-f355f4ba0cbf \
  -H 'Content-Type: application/json' \
  -d '{"name":"republic of kenya","capitalCity":"Nairobi City"}'
```

**`200 OK`**
```json
{
  "id": "54bdedee-cb2e-4efa-955e-f355f4ba0cbf",
  "name": "Republic Of Kenya",
  "isoCode": "KE",
  "capitalCity": "Nairobi City",
  "...": "...",
  "updatedAt": "2026-06-10T12:40:11.512"
}
```

**Errors**

| Status | When |
|---|---|
| `400 Bad Request` | `name` is blank/missing |
| `404 Not Found` | No record with that id |

---

## 5. Delete a country — `DELETE /api/countries/{id}`

**`curl`**
```bash
curl -i -X DELETE http://localhost:8080/api/countries/54bdedee-cb2e-4efa-955e-f355f4ba0cbf
```

**`204 No Content`** — empty body on success.

**Errors**

| Status | When |
|---|---|
| `404 Not Found` | No record with that id |

---

## Error response format

All handled errors return a consistent structured body (never a stack trace):

```json
{
  "timestamp": "2026-06-10T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Country with id 54bdedee-cb2e-4efa-955e-f355f4ba0cbf not found",
  "path": "/api/countries/54bdedee-cb2e-4efa-955e-f355f4ba0cbf"
}
```

**Validation (`400`)** responses additionally include a `fieldErrors` array:
```json
{
  "timestamp": "2026-06-10T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for request",
  "path": "/api/countries",
  "fieldErrors": [
    { "field": "name", "message": "name must not be blank" }
  ]
}
```

### HTTP status codes used

| Code | Meaning in this API |
|---|---|
| `200 OK` | Successful GET/PUT |
| `201 Created` | Country ingested/persisted |
| `204 No Content` | Successful DELETE |
| `400 Bad Request` | Request body validation failed |
| `404 Not Found` | Country/record not found |
| `502 Bad Gateway` | Downstream SOAP service failure / circuit breaker open |
| `500 Internal Server Error` | Unexpected error (safe generic message) |

---

## Postman

An importable collection and environment are provided under `postman/`:

- `postman/NCBA-CountryInfo.postman_collection.json`
- `postman/NCBA-CountryInfo.postman_environment.json`

The *Ingest country* request stores the returned UUID in a collection variable,
so the Get/Update/Delete requests chain automatically.
