# URL Shortener API

A standalone Spring Boot 3 REST API for creating short links, redirecting to their destinations, expiring links, and tracking click counts. The supplied GitHub reference repository was empty, so this project is scaffolded independently in that repository's local clone.

## Requirements

- Java 21 or newer
- Maven 3.6.3 or newer

## Run

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. H2 stores data in `url-shortener-db` in the project directory. Override the port with `SERVER_PORT`, the returned short-link base URL with `SHORTENER_PUBLIC_BASE_URL`, or the JDBC URL with `SHORTENER_DATABASE_URL`.

## API

Create a short link:

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/links" `
  -H "Content-Type: application/json" `
  --data-binary '{"originalUrl":"https://example.com/articles/42","expiresAt":"2030-01-01T00:00:00Z"}'
```

`expiresAt` is optional and must be a future ISO-8601 instant. Only absolute HTTP and HTTPS URLs are accepted.

Follow a short link with `GET /r/{code}`. Active links return `302 Found`; unknown links return `404 Not Found`; expired links return `410 Gone`.

Read click analytics with `GET /api/v1/links/{code}/analytics`.

## Verify

```powershell
mvn --batch-mode clean verify
```

Integration tests cover creation, URL validation, redirects, click counting, not-found behavior, and expiration.