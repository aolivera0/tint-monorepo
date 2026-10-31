GO_SERVICES := services/sync-core services/social-core
PY_SERVICES := services/media-worker services/analytics
COVERAGE_MIN := 85

NODE_SERVICES := services/identity-api

.PHONY: up down test-all test-go test-python test-web test-node test-android

up:
	docker compose up -d

down:
	docker compose down

test-all: test-go test-python test-web test-node

test-go:
	@for svc in $(GO_SERVICES); do \
		echo "==> $$svc"; \
		(cd $$svc && go test -race -coverprofile=coverage.out ./... && \
		 ../../scripts/check-go-coverage.sh coverage.out $(COVERAGE_MIN)) || exit 1; \
	done

test-python:
	@for svc in $(PY_SERVICES); do \
		echo "==> $$svc"; \
		(cd $$svc && \
		 if [ -f .venv/bin/activate ]; then . .venv/bin/activate; fi && \
		 python3 -m pytest --cov=src --cov-fail-under=$(COVERAGE_MIN)) || exit 1; \
	done

test-web:
	cd apps/web && npm ci && npx vitest run --coverage

test-node:
	@for svc in $(NODE_SERVICES); do \
		echo "==> $$svc"; \
		(cd $$svc && npm ci && npm run typecheck && npx vitest run --coverage) || exit 1; \
	done

# Aparte de test-all: exige JDK 17 y el Android SDK (JAVA_HOME y ANDROID_HOME) y un google-services.json
# (el real o app/google-services.json.example). Ver apps/android/README.md.
test-android:
	cd apps/android && ./gradlew testDebugUnitTest koverVerifyDebug
