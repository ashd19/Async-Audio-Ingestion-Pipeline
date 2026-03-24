# AGENTS Guide for audioPipeline

This file guides coding agents working in this repository.
It captures build/test commands, style expectations, and safe contribution rules.

## 1) Repository Layout

- Repo root contains docs plus one Java module: `AudioPipeline/`.
- Maven project root: `AudioPipeline/pom.xml`.
- Main code: `AudioPipeline/src/main/java`.
- Tests: `AudioPipeline/src/test/java`.
- Runtime config: `AudioPipeline/src/main/resources/application.yaml`.
- Spring imports env vars from `.env` and `../.env` via `spring.config.import`.

## 2) Toolchain

- Java 21 (defined in `pom.xml` with `<java.version>21</java.version>`).
- Maven Wrapper is preferred: `./AudioPipeline/mvnw`.
- Optional local infra for realistic runtime behavior:
  - PostgreSQL
  - RabbitMQ
  - MinIO

## 3) Build, Lint, and Test Commands

Run from repo root.

### Build

- Compile quickly (skip tests):
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml -DskipTests compile`
- Full package:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml clean package`
- CI-like verify:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml clean verify`

### Run Application

- Start Spring Boot app:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml spring-boot:run`

### Tests (including single-test workflows)

- Run all tests:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml test`
- Run a single test class:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml -Dtest=ApplicationTests test`
- Run a single test method:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml -Dtest=ApplicationTests#contextLoads test`
- Run multiple specific classes:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml -Dtest=ClassA,ClassB test`

### Lint / Static Analysis

- No dedicated lint plugin is configured currently.
- Treat `compile`, `test`, and `verify` as the current quality gates.
- Do not invent commands for Checkstyle/PMD/SpotBugs/Spotless unless they are added.

## 4) Configuration and Secrets

- Never commit tokens, passwords, or real credentials.
- Keep secrets in env vars or `.env` files outside version control.
- Keep existing variable naming conventions:
  - `SPRING_DATASOURCE_*`
  - `SPRING_RABBITMQ_*`
  - `APP_RABBITMQ_*`
  - `MINIO_*`
- Add grouped app config through typed `@ConfigurationProperties` classes.
- Safe local defaults are fine; never hardcode production secrets.

## 5) Code Style Guidelines

### Formatting and Structure

- Use Java 21 syntax only.
- Use 4-space indentation and follow existing formatting in touched files.
- Keep classes small and responsibilities clear.
- Keep one top-level public class per file.
- Avoid wildcard imports.

### Imports

- Import only what is used.
- Preferred grouping:
  1. `java.*` / `javax.*` / `jakarta.*`
  2. Third-party libraries
  3. `org.springframework.*`
  4. `com.AudioPipeline.*`
- Use static imports only when they improve readability.

### Naming

- Types: `PascalCase`.
- Methods/fields/locals/params: `camelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- REST endpoints: lowercase, resource-oriented paths.
- Keep base package convention consistent with the codebase (`com.AudioPipeline`).

### Types, DTOs, and API Boundaries

- Prefer explicit types at module and API boundaries.
- Use DTOs for transport concerns; avoid returning JPA entities from controllers.
- Use Bean Validation annotations (`@NotBlank`, `@Size`, etc.) on request DTOs.
- Prefer immutable structures where practical (records are acceptable).

### Spring and Dependency Injection

- Prefer constructor injection.
- Keep controllers thin; put business logic in services.
- Keep repositories declarative and minimal.
- Keep configuration beans deterministic and side-effect free.

### Persistence

- Use explicit JPA annotations (`@Entity`, `@Table`, `@Column`).
- Declare nullability/constraints intentionally.
- Use `@Transactional` for multi-step write operations that must be atomic.
- Avoid long blocking I/O inside DB transactions unless required.

### Error Handling

- Do not add broad `throws Exception` in new code.
- Throw specific exceptions with actionable messages.
- Prefer central exception mapping via `@ControllerAdvice` for shared HTTP behavior.
- Validate inputs early and fail fast.
- Do not leak secrets in errors.

### Logging

- Log meaningful boundary events (ingress, storage, publish).
- Never log credentials or sensitive raw payloads.
- Use log levels intentionally to avoid noisy hot paths.

### Lombok

- Use Lombok consistently and sparingly.
- Avoid redundant combinations (e.g., `@Data` with explicit `@Getter/@Setter`).

### Testing

- Testing stack: JUnit 5 + Spring Boot Test.
- Class naming: `*Tests`.
- Method naming: behavior-focused (example: `uploadFile_rejectsEmptyFile`).
- Favor focused unit tests for logic-heavy code; use context tests where appropriate.
- Keep tests deterministic and avoid external network dependency unless integration-scoped.

## 6) Component-Specific Guidance

- `FileController`: request parsing and response shaping only.
- `AudioFileService`: upload/download orchestration and storage interactions.
- Keep object-key generation sanitized and collision-resistant.
- Preserve streaming download behavior (`InputStreamResource` plus metadata headers).
- Keep RabbitMQ exchange/queue/routing names centralized in config classes/properties.

## 7) Change Hygiene for Agents

- Make focused, minimal edits.
- Before editing or creating any file, always ask for explicit confirmation from the user.
- Do not reformat unrelated files.
- Do not perform speculative refactors unless asked.
- Update tests/docs if behavior changes.
- Before finishing substantial work, run:
  - `./AudioPipeline/mvnw -f AudioPipeline/pom.xml test`

## 8) Cursor and Copilot Rules

- Checked for `.cursorrules`, `.cursor/rules/`, and `.github/copilot-instructions.md`.
- None are present in this repository at the time of writing.
- If added later, treat them as higher-priority repo instructions and update this file.

## 9) Quick Checklist

- Confirm module path is `AudioPipeline/`.
- Use Maven Wrapper commands from repo root with `-f AudioPipeline/pom.xml`.
- Use single-test commands when iterating quickly.
- Keep controller/service/repository responsibilities separated.
- Keep exceptions specific and HTTP mapping consistent.
- Never commit secrets.
