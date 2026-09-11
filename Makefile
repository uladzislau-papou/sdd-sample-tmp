.PHONY: dev test gates devsetup devup devdown devrestart

# The quality gates in documentation/test.definition.md § 7 are canonical; these
# targets are shorthand for the commands that run them, nothing more.

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
	docker compose -f docker-compose/docker-compose.yaml up -d

devdown:
	docker compose -f docker-compose/docker-compose.yaml down

devrestart:
	docker compose -f docker-compose/docker-compose.yaml down
	docker compose -f docker-compose/docker-compose.yaml up -d
