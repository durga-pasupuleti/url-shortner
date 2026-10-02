# Follow-up Engineering Tasks

These tasks are based on the current URL-shortener API and the local endpoint smoke test. They are a local backlog, not GitHub issues; the engine could not create a planning branch because the configured PAT received HTTP 403.

## T1. Make short-code allocation race-safe

**Depends on:** none

Review the current random-code generation and database uniqueness behavior under concurrent requests. Ensure collisions or unique-key races are retried safely without leaving the transaction unusable.

**Acceptance tests:** create many links concurrently; all successful responses have distinct codes; collision exhaustion returns a documented service error and does not persist duplicate rows.

## T2. Make click analytics atomic

**Depends on:** none

Replace read-modify-write click increments with an atomic database update so parallel redirects cannot lose counts.

**Acceptance tests:** issue concurrent redirects for one code and verify analytics equals the number of successful redirects.

## T3. Make expiration boundary tests deterministic

**Depends on:** none

Inject a `Clock` into expiration logic and test future, exact-boundary, and past expiration behavior without wall-clock sleeps.

**Acceptance tests:** a future link redirects; a link at or before the fixed clock returns 410; creation with a non-future expiration returns 400.

## T4. Expand live API contract coverage

**Depends on:** T1, T2, T3

Keep integration tests aligned with the public REST contract for create, redirect, analytics, invalid input, unknown code, and expired code. Add assertions for response schemas, Location headers, and that failed redirects do not increment analytics.

**Acceptance tests:** Maven integration suite verifies every documented status code and response field using the real Spring MVC/JPA stack.

## T5. Add API schema and operational guidance

**Depends on:** T4

Publish an OpenAPI description and document H2 file persistence, production database configuration, public base URL, retention, and abuse/rate-limit considerations.

**Acceptance tests:** generated OpenAPI includes request/response schemas and 201, 302, 400, 404, and 410 responses; documented curl examples work against the running API.
