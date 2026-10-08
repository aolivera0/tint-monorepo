GO_SERVICES := services/sync-core services/social-core
PY_SERVICES := services/media-worker services/analytics
COVERAGE_MIN := 85

.PHONY: up down test-all test-go test-python test-web

up:
	docker compose up -d

down:
	docker compose down

test-all: test-go test-python test-web

test-go:
	@for svc in $(GO_SERVICES); do \
		echo "==> $$svc"; \
		(cd $$svc && go test -race -coverprofile=coverage.out ./... && \
		 ../../scripts/check-go-coverage.sh coverage.out $(COVERAGE_MIN)) || exit 1; \
	done

test-python:
	@for svc in $(PY_SERVICES); do \
		echo "==> $$svc"; \
		(cd $$svc && python3 -m pytest --cov=src --cov-fail-under=$(COVERAGE_MIN)) || exit 1; \
	done

test-web:
	cd apps/web && npm ci && npx vitest run --coverage
