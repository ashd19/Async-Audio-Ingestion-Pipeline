# Authentication And Authorization

Short version: this system has neither.

Related docs:

- [Request Flow](./02-request-flow.md)
- [Security](./11-security.md)

## Current State

There is no implemented authentication or authorization layer anywhere in the repository.

Evidence:

- no Spring Security dependency in [`AudioPipeline/pom.xml`](../AudioPipeline/pom.xml)
- no security configuration package or `SecurityFilterChain`
- no auth middleware in Python workers
- no user/account tables in the schema
- no API key, JWT, session, OAuth, or basic-auth code
- no endpoint annotations like `@PreAuthorize`

## Effective Access Model

Anyone who can reach the API can:

- upload audio
- fetch job status
- fetch transcripts
- attempt direct file download by object key

Anyone who can reach RabbitMQ/MinIO/PostgreSQL with valid credentials can interact directly with those backends.

## What The Code Does Instead

The only request-origin metadata captured is:

- `HttpServletRequest.getRemoteAddr()` stored as `ip_address` in `audiofile`

This is an audit field, not an authorization mechanism.

## Why This Matters

Without auth, the system is best understood as:

- a local/demo backend
- an internal experiment
- an architecture prototype

It is not production-safe for untrusted network exposure.

## Authorization Model

There is no authorization model because there are no authenticated principals.

Missing concepts:

- users
- roles
- scopes
- tenant boundaries
- ownership checks

Example consequence:

- `GET /api/audio/{audioId}/transcript` returns a transcript solely based on numeric ID, with no caller identity check.

## Worker Identity

Python workers authenticate only to infrastructure services:

- RabbitMQ username/password
- MinIO access/secret key
- PostgreSQL URL or credentials

That is service-to-service connectivity, not application-level auth.

## If Authentication Were Added

The natural places would be:

- Spring Security for the API boundary
- service account credentials via secrets manager for infrastructure
- ownership checks before transcript/file retrieval
- separate internal-only routes for job operations if needed

Those are improvement ideas, not implemented behavior.
