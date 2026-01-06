# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Maven project with DDD layering: `xfg-dev-tech-api` (service interfaces, DTOs, response wrapper), `xfg-dev-tech-domain` (domain services, strategies, repositories such as `VectorStoreRepositoryImpl`), `xfg-dev-tech-trigger` (HTTP adapters, AOP/logging, controllers implementing API interfaces), and `xfg-dev-tech-app` (Spring Boot entrypoint `Application`, profile configs, static HTML demos under `src/main/resources/static`).
- Shared configs live in `xfg-dev-tech-app/src/main/resources`; `application.yml` defaults to the `dev` profile, with per-environment overrides in `application-*.yml`.
- Runtime artifacts/logs land under `data/`; additional operational notes sit in `docs/`.

## Build, Test, and Development Commands
- Build all modules (tests skipped by plugin defaults):  
  `mvn clean install`
- Run the app with the default `dev` profile on port 8090 (starts the full stack if dependencies are available):  
  `mvn -pl xfg-dev-tech-app -am spring-boot:run`
- Package an executable jar:  
  `mvn -pl xfg-dev-tech-app -am package` → `xfg-dev-tech-app/target/ai-rag-knowledge-app-exec.jar`
- Switch environments with `-Dspring.profiles.active=test` or `prod`; ensure PostgreSQL + pgvector, Redis, and embedding/model endpoints match the chosen profile.

## Coding Style & Naming Conventions
- Java 17, Spring Boot, Lombok; prefer 4-space indentation and clear REST naming (`/api/v1/...`).
- Keep DDD boundaries: controllers in trigger, domain logic in domain services/strategies, contracts in api. Use `*Controller`, `*DomainService`, `*Strategy`, `*Repository`, and DTO/response types from the API module.
- Logging via `Slf4j`; favor the provided `Response<T>` wrapper for HTTP results and keep cross-cutting concerns in AOP/advice.

## Testing Guidelines
- Tests reside in `xfg-dev-tech-app/src/test/java` (JUnit 4 + SpringRunner: `RAGTest`, `JGitTest`). They are integration-heavy and rely on Postgres/pgvector, Redis, and embedding/model providers.
- Surefire is configured to skip tests by default; run explicitly with:  
  `mvn test -pl xfg-dev-tech-app -DskipTests=false`
- When adding tests, mirror the `*Test` naming, prefer Spring Boot slices for unit scope, and document external dependencies in the test class Javadoc.

## Commit & Pull Request Guidelines
- Follow Conventional Commit style as seen in history (`feat: ...`, `refactor(...): ...`, `docs: ...`, `chore: ...`); keep messages in present tense and scoped.
- PRs should include: a concise summary, linked issues, env/profile notes (especially if config keys change), test commands/results, and API or UI evidence (e.g., sample curl, screenshots of static pages).

## Security & Configuration Tips
- Do not commit real API keys or tokens; override the sample values in `application-*.yml` via environment variables or a local `application-local.yml` ignored by Git.
- Confirm profile-specific settings (database, Redis, AI endpoints) before running tests or deploying, and avoid running vectorization tests against production data without backups.
