# Zenith Contributor Roadmap

This is a public list of useful areas where contributors can help improve Zenith. These are **open proposals**, not promises or a release schedule. Check the [open issues](https://github.com/ouerghi0x/zenith-lang/issues) before starting; for larger changes, open an issue or RFC first so the design can be agreed on.

## Reliability and deployment

- **Finish Nginx HTTPS support.** `ssl: true` currently publishes port 443, but the generated Nginx config does not yet listen for TLS or provision certificates. Define a safe certificate configuration, generate the HTTPS listener and Compose mounts, and add an end-to-end test. Also make the Nginx upstream use the configured application `port` instead of assuming 8080.
- **Test the deployment CLI end to end.** Cover `zenith server add/test/ls` and `zenith deploy deploy/logs/status/restart` against an isolated SSH or container-based test server. Verify successful deploys, remote command output, connection failures, and cleanup without requiring contributors to use a production VPS.
- **Harden Kafka and RabbitMQ streaming.** These integrations are not fully tested end to end. Add broker-backed tests for event delivery, outbox recovery, retries, dead-letter routing, and duplicate delivery after restart. Keep documentation clear about tested behavior and remaining limits.
- **Test generated WebSocket flows.** Exercise STOMP connect, subscribe, publish/broadcast, authentication rules, and disconnect/reconnect behavior against a generated application. Include a runnable example and document what was verified.
- **Add generated-project smoke tests.** Exercise representative generated projects with H2, PostgreSQL, and MySQL; validate Docker Compose output and confirm the application starts and serves a basic request.

## Language and developer tools

- **Improve multi-file diagnostics.** Preserve precise file, line, and column locations for errors in imported schemas and generated code. Add stable diagnostic codes and regression examples.
- **Keep editor support in sync with the compiler.** Share or verify the language keywords, config keys, and syntax rules used by the parser, Studio, and VS Code extension. Add conformance checks so editor highlighting and completion do not accept misleading syntax.
- **Design a safe formatter for `.zen` files.** Define formatting rules and preserve comments and imports. Start with a proposal and fixtures before adding a formatter command or editor action.

## Generated projects and Studio

- **Generate optional CI workflows.** Add an opt-in config or template for GitHub Actions that builds the generated Spring Boot project and runs its tests. Keep generated workflows small and editable.
- **Expand keyboard accessibility in Studio.** Add keyboard navigation and accessible labels for the ERD and API Tester, then cover the main interactions with repeatable checks.
- **Teach Workflows about dedicated request DTOs.** Workflow discovery currently misses some custom endpoints whose body is declared as a DTO, such as `(body: CheckoutRequest)`. Include the DTO type and fields in workflow metadata, show a useful sample payload in the step inspector, and let contributors edit or wire values into that payload. Add a regression case using a DTO-backed endpoint, including nested fields where supported.
- **Improve generated SDK verification.** Add tests for TypeScript client behavior such as authentication headers, query parameters, error responses, and compatibility with generated DTOs.

## Good first contributions

- Add a small, runnable `.zen` example for an existing language feature and link it from the docs.
- Add a focused parser or generator regression case for a reported edge case.
- Improve an error message or explain a confusing syntax rule with a before-and-after example.
- Check links, command examples, and generated file names in the documentation against the current CLI.

Before implementing a feature, review the current [contribution guide](CONTRIBUTING.md), open issues, and existing examples. For syntax or architecture changes, propose the user-facing behavior and acceptance criteria first.
