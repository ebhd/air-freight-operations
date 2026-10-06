# Contributing

1. Import the root Gradle project with JDK 22. Run `npm ci` to enable Husky.
2. Start with `docker compose up -d --build --wait`. See [README](README.md) for credentials and setup.
3. Pick an issue and use a short branch name such as `feature/12-flight-list` or `fix/15-token-error`.
4. Keep changes focused. Follow the layers in [the analysis](architecture/analyse.md); exchange data through APIs/messages.
5. Run `./gradlew spotlessApply`, then `./gradlew check assemble`. Review and stage formatting changes.
6. Reference the issue in commits and open a merge request using the template. Explain how to verify the change; wait for green CI and teammate review.

Use `http/local.http` in IntelliJ for authentication and connection checks. Never commit real credentials or tokens.
