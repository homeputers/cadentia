.PHONY: help build test typecheck check api-test ts-test deps-up deps-down clean

help:
	@echo "Common Cadentia commands:"
	@echo "  make build      Build Java and TypeScript artifacts"
	@echo "  make test       Run Java and TypeScript tests"
	@echo "  make typecheck  Run TypeScript type checks"
	@echo "  make check      Run the full local verification script"
	@echo "  make deps-up    Start local development dependencies"
	@echo "  make deps-down  Stop local development dependencies"
	@echo "  make clean      Remove generated build outputs"

build:
	mvn package
	npm run build

test: api-test ts-test

api-test:
	mvn test

ts-test:
	npm test

typecheck:
	npm run typecheck

check:
	./scripts/check.sh

deps-up:
	docker compose up -d

deps-down:
	docker compose down

clean:
	mvn clean
	rm -rf packages/*/dist
