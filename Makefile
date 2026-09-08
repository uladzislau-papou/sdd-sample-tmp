.PHONY: help setup up down run test it check coverage

help:
	@echo "setup     install git hooks (lefthook)"
	@echo "up        start PostgreSQL for local development"
	@echo "down      stop it"
	@echo "run       bootRun"
	@echo "test      fast tests: domain, use case, slice. No Docker"
	@echo "it        integration tests, every *IT. Needs Docker"
	@echo "check     the gate: both test tasks, spotlessCheck, detektMain, detektTest"
	@echo "coverage  jacoco HTML report (reporting only, never a merge gate)"

setup:
	lefthook install

up:
	docker compose -f docker-compose/docker-compose.yaml up -d

down:
	docker compose -f docker-compose/docker-compose.yaml down

run:
	./gradlew bootRun

test:
	./gradlew test

it:
	./gradlew integrationTest

check:
	./gradlew check

coverage:
	./gradlew jacocoTestReport
	@echo "report: build/reports/jacoco/test/html/index.html"
