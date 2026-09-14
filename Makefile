.PHONY: dev test gates devsetup devup devdown devrestart

# The quality gates in documentation/test.definition.md § 7 are canonical; these
# targets are shorthand for the commands that run them, nothing more.

# --env-file is not optional. Compose resolves ${VAR} interpolation against the
# .env in its *project directory*, which defaults to the compose file's directory
# (docker-compose/), not the repo root. Without this flag POSTGRES_USER and
# POSTGRES_DB interpolate to empty strings and the healthcheck silently degrades
# to `pg_isready -U "" -d ""`.
COMPOSE = docker compose --env-file .env -f docker-compose/docker-compose.yaml

dev:
	./gradlew bootRun

test:
	./gradlew test

gates:
	./gradlew clean test
	./gradlew build

devsetup:
	cp .env.example .env

devup:
	$(COMPOSE) up -d

devdown:
	$(COMPOSE) down

devrestart:
	$(COMPOSE) down
	$(COMPOSE) up -d
