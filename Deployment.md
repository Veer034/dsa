# CI/CD Pipeline — GitHub Actions to Production
## Interview Guide for 11+ Years Experienced Engineers

> **Target Audience:** Senior / Staff / Principal Engineers interviewing at product-based companies (Google, Meta, Amazon, Flipkart, Razorpay, Swiggy, etc.)
> Covers: GitHub Actions internals, testing strategies, deployment approaches, production incidents, and real war stories.

---

## Table of Contents

1. [CI/CD Core Concepts](#1-cicd-core-concepts)
2. [GitHub Actions Deep Dive](#2-github-actions-deep-dive)
3. [Testing Strategy in CI](#3-testing-strategy-in-ci)
4. [Deployment Strategies](#4-deployment-strategies)
5. [Production Deployment Approaches at Big Companies](#5-production-deployment-approaches-at-big-companies)
6. [Production Incidents & How They Were Resolved](#6-production-incidents--how-they-were-resolved)
7. [Advanced GitHub Actions Patterns](#7-advanced-github-actions-patterns)
8. [Security in CI/CD](#8-security-in-cicd)
9. [Observability & Rollbacks](#9-observability--rollbacks)
10. [Senior-Level Architecture Questions](#10-senior-level-architecture-questions)

---

## 1. CI/CD Core Concepts

### What is CI/CD and what problem does it actually solve?

* **Continuous Integration (CI):** Every code commit triggers an automated build and test run. Developers integrate code into a shared branch frequently (multiple times a day), and automation catches breakage immediately — not during a monthly integration hell.
* **Continuous Delivery (CD):** Every passing commit is automatically deployed to a staging/pre-prod environment and is **releasable to production** with one click or approval gate.
* **Continuous Deployment:** Every passing commit is **automatically deployed to production** without manual intervention. Netflix, Amazon, and Etsy operate this way.
* **The real problem it solves:**
    * Without CI/CD: Teams integrate code every few weeks → merge conflicts are catastrophic → bugs found late → long release cycles → fear of deploying → even longer cycles. (The "Death Spiral of Waterfall Releases")
    * With CI/CD: Small commits, fast feedback, fearless deployment. Amazon deploys every 11.6 seconds on average.

---

### Difference between Continuous Delivery and Continuous Deployment

| Aspect | Continuous Delivery | Continuous Deployment |
|--------|--------------------|-----------------------|
| Production deploy | Manual trigger/approval | Fully automatic |
| Human gate | Yes — one-click after tests pass | No — pipeline decides |
| Risk tolerance | Medium (regulated industries) | High (fast-moving products) |
| Examples | Banks, healthcare, govt | Netflix, Etsy, Amazon |
| When to use | Compliance requirements, high-stakes releases | High-trust automation, strong rollback |

---

### What is a pipeline and what stages should a mature pipeline have?

A pipeline is a sequence of automated stages that code passes through from commit to production. A mature pipeline looks like:

```
Developer Push
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 1: TRIGGER                                               │
│  Push to PR branch → GitHub Actions triggered                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 2: BUILD & STATIC ANALYSIS                               │
│  • Compile / lint / SAST (static security scan)                 │
│  • Dependency vulnerability check (Snyk, Dependabot)           │
│  • Code style enforcement (Checkstyle, ESLint)                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 3: UNIT TESTS                                            │
│  • Runs in parallel (sharded across runners)                    │
│  • Code coverage gate (fail if < 80%)                           │
│  • Fast — must complete in <5 minutes                           │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 4: INTEGRATION TESTS                                     │
│  • Spin up Docker Compose (DB, Redis, queues)                   │
│  • Test service boundaries                                      │
│  • Contract tests (Pact)                                        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 5: BUILD & PUSH ARTIFACT                                 │
│  • Docker image built, tagged with git SHA                      │
│  • Push to ECR / GCR / Artifactory                              │
│  • SBOM (Software Bill of Materials) generated                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 6: DEPLOY TO STAGING                                     │
│  • Auto-deploy on merge to main                                 │
│  • Smoke tests, E2E tests run                                   │
│  • Performance regression tests                                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 7: PRODUCTION GATE                                       │
│  • Manual approval (or automatic with feature flags)            │
│  • Change request ticket auto-created (for regulated companies) │
│  • Deployment window check (no deploys during peak hours)       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  STAGE 8: PRODUCTION DEPLOY                                     │
│  • Canary / Blue-Green / Rolling (chosen strategy)              │
│  • Automated rollback trigger on error rate spike               │
│  • Post-deploy smoke tests & synthetic monitoring               │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. GitHub Actions Deep Dive

### How does GitHub Actions work internally?

* **GitHub Actions** is an event-driven automation platform built into GitHub. Every `.github/workflows/*.yml` file defines a workflow.
* **Key Concepts:**
    * **Event:** The trigger — `push`, `pull_request`, `schedule`, `workflow_dispatch`, `release`.
    * **Workflow:** The YAML file. Can have multiple jobs.
    * **Job:** A group of steps that run on a single runner. Jobs run in parallel by default.
    * **Step:** A single command or action within a job. Steps run sequentially.
    * **Runner:** A VM (GitHub-hosted or self-hosted) that executes the job. GitHub provides `ubuntu-latest`, `windows-latest`, `macos-latest`.
    * **Action:** A reusable unit — can be a Docker container, JavaScript, or composite action. Stored in their own repos (e.g., `actions/checkout@v4`).

```yaml

# =============================================================================
# CI/CD PIPELINE — Enterprise Grade
# =============================================================================
#
# FLOW OVERVIEW:
#
#   [Any Branch] --> PR raised --> Run Tests Only (no build, no deploy)
#                                  └─ All pass? --> PR can be merged
#                                  └─ Any fail? --> PR is blocked
#
#   [main branch] --> PR merged --> Run Tests + Build Docker + Deploy Staging
#
#   [Manual trigger by admin] --> Canary Production Deploy
#                                  └─ 5% traffic for 10 min
#                                  └─ Error/latency OK? --> promote to 100%
#                                  └─ Threshold breached? --> instant rollback
#                                     (just shift traffic back, no pod kill,
#                                      old pods are still running, ~30 seconds)
#
# WHY THIS STRUCTURE FOR 30+ ENGINEERS:
#   - PRs only run tests, never deploy → no staging conflicts between devs
#   - Only merged main code reaches staging → staging is always stable
#   - Production needs admin approval → no accidental prod deploys
#   - Canary keeps old pods alive → rollback is a traffic shift, not a redeploy
#
# =============================================================================

name: CI/CD Pipeline

on:
  # Trigger 1: When any engineer opens or updates a PR targeting main.
  # This ONLY runs tests (jobs: lint, unit-tests, integration-tests, sast).
  # Nothing gets built or deployed here. This is the PR gate.
  pull_request:
    branches: [main]

  # Trigger 2: When a PR is merged into main (a push to main).
  # This runs tests + builds Docker image + deploys to staging automatically.
  push:
    branches: [main]

  # Trigger 3: Manual trigger for production deployment.
  # Only admins with repo write access can run this from the GitHub Actions UI.
  # This is the ONLY way code reaches production.
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Docker image tag to deploy to production (e.g. sha-abc1234)'
        required: true
        type: string
      canary_weight:
        description: 'Initial canary traffic percentage (default 5)'
        required: false
        default: '5'
        type: string
      canary_duration_seconds:
        description: 'How long to monitor canary before promoting (default 600 = 10 min)'
        required: false
        default: '600'
        type: string

env:
  # GitHub Container Registry — images are pushed here after every main merge
  REGISTRY: ghcr.io

  # Full image name becomes: ghcr.io/<org>/<repo>
  IMAGE_NAME: ${{ github.repository }}

  # Java version used across all jobs — change here to update everywhere
  JAVA_VERSION: '21'

  # Minimum code coverage % required to pass the coverage gate
  COVERAGE_THRESHOLD: 80

  # Kubernetes namespace names
  STAGING_NAMESPACE: staging
  PRODUCTION_NAMESPACE: production

# =============================================================================
# JOB 1: LINT & COMPILE
# -----------------------------------------------------------------------------
# Runs on: PR to main + push to main
# Purpose: Catch syntax errors, style violations, and compilation failures
#          BEFORE wasting time on expensive test jobs.
#          If this fails, no other jobs run (they all depend on this).
# =============================================================================
jobs:
  lint-and-compile:
    name: "Lint & Compile"
    runs-on: ubuntu-latest

    # Only run this job for PRs or pushes to main.
    # Skip for workflow_dispatch (manual prod deploy doesn't need to recompile).
    if: github.event_name == 'pull_request' || github.event_name == 'push'

    steps:
      - name: "Checkout source code"
        # fetch-depth 0 = full git history, needed by SonarQube for accurate blame info
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: "Set up JDK ${{ env.JAVA_VERSION }}"
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          # cache: maven → restores ~/.m2 between runs using a cache key based on pom.xml
          # This saves 1–3 minutes per run by not re-downloading all dependencies
          cache: 'maven'

      - name: "Run Checkstyle (code style) and compile"
        # -B = batch mode (no progress bars, cleaner logs)
        # checkstyle:check enforces coding standards (tabs, naming, line length, etc.)
        # compile catches any Java compilation errors early
        run: mvn compile checkstyle:check -B --no-transfer-progress

# =============================================================================
# JOB 2: UNIT TESTS (Sharded for speed)
# -----------------------------------------------------------------------------
# Runs on: PR to main + push to main
# Purpose: Run all unit tests quickly by splitting them across 4 parallel runners.
#          With 30 engineers, test suites grow large. Sharding keeps this fast.
#          Each shard uploads its coverage data as an artifact for the gate job.
# =============================================================================
  unit-tests:
    name: "Unit Tests (Shard ${{ matrix.shard }}/4)"
    runs-on: ubuntu-latest
    needs: lint-and-compile   # Wait for compile to pass first

    strategy:
      # fail-fast: false → if shard 2 fails, shards 3 and 4 still complete
      # This gives you the full picture of failures, not just the first one
      fail-fast: false
      matrix:
        shard: [1, 2, 3, 4]   # 4 parallel VMs, each running ~25% of the test suite

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: "Run unit tests — shard ${{ matrix.shard }} of 4"
        # forkCount=4 tells Surefire to split tests into 4 groups
        # forkNumber tells this specific runner which group to pick up
        # jacoco.skip=false ensures coverage data is collected on each shard
        run: |
          mvn test -B \
            -Dsurefire.forkNumber=${{ matrix.shard }} \
            -Dsurefire.forkCount=4 \
            -Djacoco.skip=false

      - name: "Upload coverage data for this shard"
        # Each shard uploads its .exec file. The coverage-gate job merges them all.
        uses: actions/upload-artifact@v4
        with:
          name: coverage-shard-${{ matrix.shard }}
          path: target/jacoco.exec
          # Keep artifacts for 3 days — enough for debugging, not forever
          retention-days: 3

# =============================================================================
# JOB 3: COVERAGE GATE
# -----------------------------------------------------------------------------
# Runs on: PR to main + push to main
# Purpose: Merge all 4 shards' coverage data into one report.
#          Enforce a minimum coverage threshold (default 80%).
#          If coverage drops below threshold, the PR/push is blocked.
# =============================================================================
  coverage-gate:
    name: "Coverage Gate (min ${{ env.COVERAGE_THRESHOLD }}%)"
    runs-on: ubuntu-latest
    needs: unit-tests   # Wait for ALL 4 shards to finish first

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: "Download coverage data from all shards"
        # pattern: coverage-shard-* matches all 4 uploaded artifacts
        # merge-multiple: true puts them all in the same directory
        uses: actions/download-artifact@v4
        with:
          pattern: coverage-shard-*
          merge-multiple: true

      - name: "Merge coverage reports and enforce threshold"
        # jacoco:merge combines the 4 .exec files into one
        # jacoco:report generates the XML report
        # extract_coverage.py reads the XML and outputs a percentage number
        # bc -l is used for floating point comparison
        run: |
          mvn jacoco:merge jacoco:report -B
          COVERAGE=$(python scripts/extract_coverage.py target/site/jacoco/jacoco.xml)
          echo "📊 Total coverage: $COVERAGE%"
          if (( $(echo "$COVERAGE < ${{ env.COVERAGE_THRESHOLD }}" | bc -l) )); then
            echo "❌ Coverage $COVERAGE% is below the ${{ env.COVERAGE_THRESHOLD }}% threshold. PR blocked."
            exit 1
          fi
          echo "✅ Coverage check passed: $COVERAGE%"

# =============================================================================
# JOB 4: INTEGRATION TESTS
# -----------------------------------------------------------------------------
# Runs on: PR to main + push to main
# Purpose: Test the application against real service dependencies (MySQL, Redis).
#          GitHub spins up these containers as "services" — they run alongside
#          the test runner and are accessible via localhost.
#          Runs in parallel with unit-tests to save total pipeline time.
# =============================================================================
  integration-tests:
    name: "Integration Tests"
    runs-on: ubuntu-latest
    needs: lint-and-compile   # Only needs compile, runs parallel to unit-tests

    # GitHub Actions "services" spin up Docker containers before the steps run.
    # They are stopped and removed automatically after the job finishes.
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: testpass
          MYSQL_DATABASE: testdb
        # Health check ensures MySQL is fully ready before tests start.
        # Without this, tests may start before MySQL finishes initializing and fail.
        options: >-
          --health-cmd="mysqladmin ping -h localhost"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5
        ports:
          - 3306:3306

      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd="redis-cli ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: "Run integration tests against MySQL and Redis"
        # -P integration-tests activates the Maven profile that includes
        # integration test classes (usually in src/test/java/**/*IT.java)
        # Spring picks up the DB/Redis URLs from these environment variables
        run: mvn verify -P integration-tests -B --no-transfer-progress
        env:
          SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/testdb
          SPRING_DATASOURCE_USERNAME: root
          SPRING_DATASOURCE_PASSWORD: testpass
          SPRING_REDIS_HOST: localhost
          SPRING_REDIS_PORT: 6379

# =============================================================================
# JOB 5: SAST & DEPENDENCY SCAN
# -----------------------------------------------------------------------------
# Runs on: PR to main + push to main
# Purpose: Security scanning in parallel with tests.
#          SonarQube → static analysis (code smells, bugs, security hotspots)
#          Snyk → checks all Maven dependencies for known CVEs
#          Both run in parallel with tests to not add to total pipeline time.
# =============================================================================
  security-scan:
    name: "SAST + Dependency Vulnerability Scan"
    runs-on: ubuntu-latest
    needs: lint-and-compile

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # SonarQube needs full history for blame annotations

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: "SonarQube — static analysis and security hotspot detection"
        # Checks for: bugs, vulnerabilities, code smells, duplications
        # Results visible in your SonarCloud dashboard
        uses: SonarSource/sonarcloud-github-action@master
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}   # Needed to post PR comments

      - name: "Snyk — scan Maven dependencies for known CVEs"
        # --severity-threshold=high → only fail on HIGH or CRITICAL CVEs
        # LOW/MEDIUM issues are reported but don't block the PR
        uses: snyk/actions/maven@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
        with:
          args: --severity-threshold=high

# =============================================================================
# JOB 6: BUILD & PUSH DOCKER IMAGE
# -----------------------------------------------------------------------------
# Runs on: push to main ONLY (not on PRs — no point building for every PR)
# Purpose: Build a production Docker image and push it to GitHub Container Registry.
#          Only runs after ALL tests pass (unit, integration, coverage, security).
#          The image tag includes the git SHA so every commit has a unique image.
#          Builds for both amd64 and arm64 (for teams using Apple Silicon locally).
# =============================================================================
  docker-build:
    name: "Build & Push Docker Image"
    runs-on: ubuntu-latest

    # Wait for all test jobs to pass before building the image.
    # This ensures we never push an image from failing code.
    needs: [coverage-gate, integration-tests, security-scan]

    # ONLY run this on push to main (i.e., after a PR is merged).
    # PRs do NOT build Docker images — that would waste time and registry space.
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'

    permissions:
      contents: read
      packages: write       # Required to push to ghcr.io

    outputs:
      # Pass the full image tag to the staging deploy job
      image-tag: ${{ steps.meta.outputs.tags }}
      # Short SHA used to construct the specific tag (e.g. sha-abc1234)
      short-sha: ${{ steps.sha.outputs.short }}

    steps:
      - uses: actions/checkout@v4

      - name: "Compute short git SHA for image tagging"
        id: sha
        # Short SHA (first 7 chars) used as the image tag.
        # Example: sha-a1b2c3d → easy to trace which commit an image came from
        run: echo "short=$(echo ${{ github.sha }} | cut -c1-7)" >> $GITHUB_OUTPUT

      - name: "Log in to GitHub Container Registry (ghcr.io)"
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          # GITHUB_TOKEN is auto-generated per workflow run — no manual secret needed
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: "Set up Docker Buildx (needed for multi-platform builds)"
        # Buildx is required to build for multiple CPU architectures in one command
        uses: docker/setup-buildx-action@v3

      - name: "Generate Docker image tags and labels"
        id: meta
        # This action automatically generates:
        #   - sha-<short_sha> tag (e.g. sha-a1b2c3d) — used for traceability
        #   - branch name tag (e.g. main) — always points to latest from that branch
        #   - semver tag (e.g. v1.2.3) — if you push a git tag
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=sha-,format=short
            type=ref,event=branch
            type=semver,pattern={{version}}

      - name: "Build Docker image and push to registry"
        id: build
        # platforms: build for Intel (amd64) and Apple Silicon / ARM servers (arm64)
        # cache-from/cache-to: uses GitHub Actions cache to speed up layer reuse
        # sbom: generates a Software Bill of Materials (lists all included packages)
        # provenance: generates SLSA attestation (proves this image came from this commit)
        uses: docker/build-push-action@v5
        with:
          context: .
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          sbom: true
          provenance: true

# =============================================================================
# JOB 7: DEPLOY TO STAGING
# -----------------------------------------------------------------------------
# Runs on: push to main ONLY (after Docker image is built and pushed)
# Purpose: Automatically deploy the new image to the staging environment.
#          Staging is for QA, integration checks, and demo — NOT per-PR deploys.
#          With 30+ engineers, deploying every PR to staging would cause constant
#          conflicts and instability. Only merged, tested code reaches staging.
#          After deploy, smoke tests verify the app is actually responding.
# =============================================================================
  deploy-staging:
    name: "Deploy to Staging"
    runs-on: ubuntu-latest
    needs: docker-build

    # "environment: staging" links to a GitHub Environment (Settings → Environments).
    # You can configure environment-specific secrets (KUBECONFIG, etc.) there.
    # You can also add required reviewers here if you want a staging approval gate.
    environment: staging

    if: github.event_name == 'push' && github.ref == 'refs/heads/main'

    steps:
      - uses: actions/checkout@v4

      - name: "Set up kubectl to talk to the staging cluster"
        uses: azure/setup-kubectl@v3
        with:
          version: 'latest'

      - name: "Configure kubeconfig for staging cluster"
        # KUBECONFIG_STAGING is a base64-encoded kubeconfig stored as a GitHub secret.
        # This writes it to disk so kubectl commands work in subsequent steps.
        run: |
          echo "${{ secrets.KUBECONFIG_STAGING }}" | base64 -d > /tmp/kubeconfig
          echo "KUBECONFIG=/tmp/kubeconfig" >> $GITHUB_ENV

      - name: "Update staging deployment to use new Docker image"
        # kubectl set image: updates the container image inside the running Deployment.
        # Kubernetes then does a rolling update — new pods come up, old ones go down.
        # kubectl rollout status: waits and watches until all new pods are Running.
        # --timeout=5m: if it takes more than 5 minutes, fail and alert.
        run: |
          IMAGE="${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ needs.docker-build.outputs.short-sha }}"
          echo "🚀 Deploying image: $IMAGE"
          kubectl set image deployment/app app=$IMAGE \
            --namespace=${{ env.STAGING_NAMESPACE }}
          kubectl rollout status deployment/app \
            --namespace=${{ env.STAGING_NAMESPACE }} \
            --timeout=5m

      - name: "Run smoke tests against staging"
        # Smoke tests are a minimal set of checks to confirm the app is alive.
        # /health → Spring Boot actuator endpoint (checks DB, Redis connectivity too)
        # npm run test:smoke → runs a small Playwright/Cypress suite against staging URL
        # If smoke tests fail, the deploy job fails and the team is alerted immediately.
        run: |
          echo "🔍 Running smoke tests against staging..."
          curl -f https://staging.myapp.com/actuator/health || exit 1
          npm run test:smoke -- --baseUrl=https://staging.myapp.com

      - name: "Notify team of successful staging deploy"
        # Post a Slack message so QA and devs know staging is updated.
        # Uses a Slack webhook URL stored as a GitHub secret.
        if: success()
        run: |
          curl -s -X POST ${{ secrets.SLACK_WEBHOOK_URL }} \
            -H 'Content-type: application/json' \
            -d '{
              "text": "✅ *Staging deploy complete*\nCommit: `${{ github.sha }}`\nAuthor: ${{ github.actor }}\nURL: https://staging.myapp.com"
            }'

      - name: "Notify team of staging deploy failure"
        if: failure()
        run: |
          curl -s -X POST ${{ secrets.SLACK_WEBHOOK_URL }} \
            -H 'Content-type: application/json' \
            -d '{
              "text": "❌ *Staging deploy FAILED*\nCommit: `${{ github.sha }}`\nAuthor: ${{ github.actor }}\nCheck: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}"
            }'

# =============================================================================
# JOB 8: PRODUCTION CANARY DEPLOY (Manual Admin Trigger Only)
# -----------------------------------------------------------------------------
# Runs on: workflow_dispatch (manual trigger from GitHub Actions UI)
# Purpose: Deploy to production using canary strategy.
#
# HOW CANARY WORKS HERE:
#   Step 1 → Deploy new version alongside old version.
#             Old pods keep running — nothing is killed.
#   Step 2 → Route only X% of traffic (default 5%) to new pods via Helm/Istio.
#   Step 3 → Monitor error rate and p99 latency for N seconds (default 10 min).
#   Step 4a → All healthy? Promote to 100% traffic. Old pods gracefully terminated.
#   Step 4b → Threshold breached? Shift traffic back to 0% on new pods.
#             This takes ~5-10 seconds. Old pods were never stopped — instant recovery.
#             No need to rebuild or redeploy anything.
#
# WHY ~30 SECOND ROLLBACK:
#   Traditional rollback = redeploy old image → pull image → start containers → ready
#   This approach = change Helm value canary.weight=0 → Istio updates routing rules
#   Istio/nginx routing rule propagation takes ~5-15 seconds across the mesh.
#   Old pods are still warm and serving traffic immediately.
# =============================================================================
  deploy-production:
    name: "Production Canary Deploy"
    runs-on: ubuntu-latest

    # "environment: production" requires manual approval from designated reviewers
    # before this job starts. Configure this in Settings → Environments → production.
    # Add required reviewers = your admins/SRE team.
    environment: production

    # This job ONLY runs when manually triggered via workflow_dispatch.
    # It will never run automatically on PR or push events.
    if: github.event_name == 'workflow_dispatch'

    steps:
      - uses: actions/checkout@v4

      - name: "Set up Helm (used to manage Kubernetes releases)"
        uses: azure/setup-helm@v3
        with:
          version: 'latest'

      - name: "Set up kubectl for production cluster"
        uses: azure/setup-kubectl@v3

      - name: "Configure kubeconfig for production cluster"
        run: |
          echo "${{ secrets.KUBECONFIG_PRODUCTION }}" | base64 -d > /tmp/kubeconfig
          echo "KUBECONFIG=/tmp/kubeconfig" >> $GITHUB_ENV

      - name: "Step 1 — Deploy canary pods (old pods stay running)"
        # helm upgrade --install: deploys the new image as a canary alongside the stable.
        # canary.enabled=true → your Helm chart creates a second Deployment + Istio rules.
        # canary.weight=5 → 5% of incoming traffic goes to new pods.
        # The OLD deployment is untouched. Its pods keep running and serve 95% of traffic.
        # At this point BOTH versions are live simultaneously.
        run: |
          IMAGE_TAG="${{ github.event.inputs.image_tag }}"
          CANARY_WEIGHT="${{ github.event.inputs.canary_weight }}"
          echo "🐦 Deploying canary with image tag: $IMAGE_TAG at $CANARY_WEIGHT% traffic"

          helm upgrade --install app ./helm/app \
            --namespace=${{ env.PRODUCTION_NAMESPACE }} \
            --set image.tag=$IMAGE_TAG \
            --set canary.enabled=true \
            --set canary.weight=$CANARY_WEIGHT \
            --wait \
            --timeout=3m

      - name: "Step 2 — Monitor canary health (error rate + latency)"
        # monitor_canary.py queries your metrics backend (Prometheus/Datadog/CloudWatch).
        # --duration: how long to monitor (in seconds, default 600 = 10 minutes)
        # --error-threshold: fail if error rate exceeds this % (e.g. 1 = 1%)
        # --latency-p99-threshold: fail if p99 latency exceeds this ms (e.g. 500ms)
        # If the script exits non-zero, the next step (promote) is skipped
        # and the rollback step runs instead.
        id: monitor
        run: |
          echo "📊 Monitoring canary for ${{ github.event.inputs.canary_duration_seconds }} seconds..."
          python scripts/monitor_canary.py \
            --duration=${{ github.event.inputs.canary_duration_seconds }} \
            --error-threshold=1 \
            --latency-p99-threshold=500 \
            --namespace=${{ env.PRODUCTION_NAMESPACE }}

      - name: "Step 3a — PROMOTE: Shift 100% traffic to new version"
        # Only runs if the monitor step succeeded (canary is healthy).
        # canary.enabled=false → removes the canary split, new version becomes stable.
        # Old pods are gracefully terminated by Kubernetes (they finish in-flight requests first).
        if: success() && steps.monitor.outcome == 'success'
        run: |
          echo "✅ Canary healthy! Promoting to 100% traffic..."
          helm upgrade app ./helm/app \
            --namespace=${{ env.PRODUCTION_NAMESPACE }} \
            --set image.tag=${{ github.event.inputs.image_tag }} \
            --set canary.enabled=false \
            --wait \
            --timeout=3m
          echo "🎉 Production deploy complete. New version is now serving 100% traffic."

      - name: "Step 3b — ROLLBACK: Shift traffic back to stable (~30 seconds)"
        # Runs ONLY if the monitor step failed (threshold was breached).
        # canary.weight=0 → Istio/nginx immediately routes 0% traffic to new pods.
        # This propagates across the service mesh in ~5-15 seconds.
        # Old (stable) pods were running the whole time — they take back 100% instantly.
        # We then scale canary replicas to 0 to stop wasting resources (but NOT delete).
        # This entire step completes in well under 30 seconds.
        if: failure() && steps.monitor.outcome == 'failure'
        run: |
          echo "🚨 Canary threshold breached! Rolling back traffic to stable version..."

          # Immediately set canary traffic weight to 0
          # This is just a Helm values update → Istio routing rule change → ~10 seconds
          helm upgrade app ./helm/app \
            --namespace=${{ env.PRODUCTION_NAMESPACE }} \
            --set image.tag=${{ github.event.inputs.image_tag }} \
            --set canary.enabled=true \
            --set canary.weight=0 \
            --wait \
            --timeout=1m

          echo "✅ Traffic rolled back. Stable version is now receiving 100% of traffic."
          echo "⏱  Rollback completed. Canary pods still exist but receive zero traffic."
          echo "👀 Investigate the failed canary before attempting another deployment."

      - name: "Notify team of production deploy outcome"
        # Always runs regardless of success or failure, to alert the team.
        if: always()
        run: |
          if [ "${{ steps.monitor.outcome }}" == "success" ]; then
            STATUS="✅ *Production deploy SUCCEEDED*\nImage: \`${{ github.event.inputs.image_tag }}\`\nTraffic: 100% on new version"
          else
            STATUS="🚨 *Production deploy ROLLED BACK*\nImage: \`${{ github.event.inputs.image_tag }}\`\nReason: Canary threshold breached\nTraffic: 100% back on stable"
          fi
          curl -s -X POST ${{ secrets.SLACK_WEBHOOK_URL }} \
            -H 'Content-type: application/json' \
            -d "{\"text\": \"$STATUS\nTriggered by: ${{ github.actor }}\nRun: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}\"}"
```
---

### What are GitHub Environments and how do you use them for production gates?

* **GitHub Environments** are named deployment targets (`staging`, `production`) with their own:
    * **Secrets:** Production DB passwords, API keys separate from CI secrets.
    * **Protection Rules:** Required reviewers (e.g., 2 senior engineers must approve before prod deploy runs).
    * **Deployment branches:** Only `main` branch can deploy to production.
    * **Wait timer:** Add a 5-minute delay after approval before execution.
* **Setup:** Repository Settings → Environments → New Environment → Add required reviewers.
* **In workflow:** `environment: production` on a job — the job pauses until approved.

---

### What is the difference between `secrets.GITHUB_TOKEN` and repository secrets?

* **`secrets.GITHUB_TOKEN`:** Automatically provided by GitHub on every workflow run. Scoped to the repository. Used for pushing to packages (GHCR), creating releases, commenting on PRs. Expires after the workflow ends.
* **Repository Secrets (`secrets.MY_SECRET`):** Manually added by repo admins. Used for external services — AWS credentials, Docker Hub tokens, Slack webhooks, SonarQube tokens. Persist indefinitely.
* **Organization Secrets:** Shared across multiple repositories — useful for company-wide Snyk tokens or Sonar configs.
* **Environment Secrets:** Scoped to a specific environment (staging/production) — tighter security, production secrets not accessible during PR builds.

---

### How do you cache dependencies in GitHub Actions to speed up builds?

```yaml
# Maven
- uses: actions/setup-java@v4
  with:
    cache: 'maven'            # Caches ~/.m2/repository automatically

# Node.js
- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'npm'              # Caches node_modules based on package-lock.json hash

# Python
- uses: actions/setup-python@v5
  with:
    python-version: '3.12'
    cache: 'pip'

# Generic cache (Docker layers, Gradle, custom paths)
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
    restore-keys: |
      ${{ runner.os }}-gradle-
```

* **Cache key design:** Include a hash of the dependency file (`package-lock.json`, `pom.xml`, `requirements.txt`). When dependencies change, cache misses and rebuilds. Unchanged → cache hit → skip install.
* **Cache size limit:** GitHub caches are limited to 10GB per repository. Evicted after 7 days of no access.

---

## 3. Testing Strategy in CI

### What is the Test Pyramid and how should it map to your CI pipeline?

```
           /\
          /  \
         / E2E \           ← Few (10-20): Slow, brittle, expensive
        /  Tests \            Run on staging only, not every PR
       /──────────\
      /            \
     / Integration  \      ← Some (100-500): Medium speed
    /    Tests       \        Run on every PR with Docker services
   /──────────────────\
  /                    \
 /     Unit Tests       \   ← Many (1000+): Fast, isolated, cheap
/________________________\     Run on every commit, sharded


```

* **Unit tests:** Mock all I/O. Run in <5 minutes. Gate every commit.
* **Integration tests:** Real DB + Redis via Docker. Test service-to-service contracts. Run on every PR.
* **E2E tests:** Full user journeys in a real browser (Playwright/Selenium). Run on staging post-deploy. NOT on every PR — too slow and flaky.
* **Contract tests (Pact):** Consumer-driven contracts between microservices. Run in CI to ensure API compatibility without spinning up all services.

---

### How do you handle flaky tests in CI?

* **Flaky tests** are tests that pass and fail non-deterministically — the silent killers of CI trust. Teams that tolerate flaky tests stop trusting CI failures, which defeats the purpose.
* **Detection:** Track test results over time. A test that fails in <10% of runs but passes when re-run = flaky. Tools: BuildPulse, Datadog CI Visibility, GitHub Actions test annotations.
* **Retry strategy (short-term fix, not a solution):**
    ```yaml
    - name: Run tests with retry on failure
      uses: nick-fields/retry@v3
      with:
        timeout_minutes: 10
        max_attempts: 3
        command: mvn test
    ```
* **Root causes and real fixes:**
    * **Timing issues:** Replace `Thread.sleep(1000)` with `Awaitility.await().atMost(5, SECONDS).until(...)`.
    * **Shared state:** Tests modifying global state, static variables. Fix: Reset state in `@BeforeEach`/`@AfterEach`.
    * **External dependencies:** Tests calling real APIs. Fix: Mock them with WireMock or LocalStack.
    * **Port conflicts:** Two tests binding the same port. Fix: Use random ports or `@DynamicPropertySource` in Spring.
    * **Race conditions:** Async code without proper wait. Fix: Use `CompletableFuture.get()` or `CountDownLatch`.
* **Quarantine process:** Move flaky tests to a `@Quarantine` tag. Run them separately, don't block the main pipeline. Fix within 1 sprint or delete.

---

### How do you ensure database migrations don't break CI?

```yaml
# Run Flyway/Liquibase migrations in CI before integration tests
- name: Run DB migrations
  run: |
    mvn flyway:migrate \
      -Dflyway.url=jdbc:mysql://localhost:3306/testdb \
      -Dflyway.user=root \
      -Dflyway.password=testpass

- name: Verify migration was clean
  run: |
    mvn flyway:validate
```

* **Migration testing strategy:**
    * Every PR that includes a migration file must pass both `flyway:migrate` (forward) and `flyway:undo` (backward, if using Flyway Teams) in CI.
    * Test the migration on a **copy of production data volume** in a pre-prod environment before prod deploy.
    * Always test with "current production schema + new migration" — not just "empty DB + all migrations."

---

## 4. Deployment Strategies

### Explain Blue-Green Deployment

```
BEFORE DEPLOY:
  Load Balancer ──── 100% ──→ Blue (v1.0) [LIVE]
                              Green (v1.1) [IDLE]

AFTER IMAGE DEPLOY:
  Load Balancer ──── 100% ──→ Blue (v1.0) [LIVE]
                              Green (v1.1) [NEW CODE, WARMED UP]

AFTER TRAFFIC SWITCH:
  Load Balancer ──── 100% ──→ Green (v1.1) [NOW LIVE]
                              Blue (v1.0) [STANDBY - instant rollback]

ON ROLLBACK (seconds):
  Load Balancer ──── 100% ──→ Blue (v1.0) [LIVE AGAIN]
```

* **How it works:** Two identical production environments. You deploy to the idle one, run tests, then switch traffic via load balancer update (Route53, nginx, ALB listener rule).
* **Pros:** Zero downtime, instant rollback (switch traffic back), no mixed versions in flight.
* **Cons:** Requires 2× infrastructure cost, database migrations must be backward compatible (both blue and green must work with the same DB schema during cutover).
* **Production use:** AWS Elastic Beanstalk, AWS CodeDeploy (blue/green), Argo Rollouts.

---

### Explain Canary Deployment

```
Step 1: 5% canary
  Load Balancer ─→ 95% Old Version (v1.0)
                └→  5% Canary  (v1.1)   ← Monitor: error rate, latency, business metrics

Step 2: If healthy, 25%
  Load Balancer ─→ 75% Old Version (v1.0)
                └→ 25% Canary  (v1.1)

Step 3: If healthy, 100%
  Load Balancer ─→   0% Old Version
                └→ 100% Canary  (v1.1)   ← Full rollout complete
```

* **Automated Canary Analysis (Kayenta / Argo Rollouts):**
    * Compare canary metrics vs baseline: error rate, p99 latency, business KPIs (order success rate, payment conversion).
    * Auto-promote if metrics are within threshold. Auto-rollback if they deviate.
* **When canary beats blue-green:** When you can't afford 2× infra costs or when you want real user traffic validation rather than synthetic test validation.
* **Pitfall:** Session stickiness — if 5% of users hit the canary, they should consistently hit it (sticky sessions or consistent hashing) to avoid getting different behavior on page reload.

---

### Explain Rolling Deployment

```
Pods: [v1] [v1] [v1] [v1] [v1] [v1] [v1] [v1]

Step 1:
        [v2] [v2] [v1] [v1] [v1] [v1] [v1] [v1]

Step 2:
        [v2] [v2] [v2] [v2] [v1] [v1] [v1] [v1]

Step 3 (complete):
        [v2] [v2] [v2] [v2] [v2] [v2] [v2] [v2]
```

* **Kubernetes Rolling Update config:**
    ```yaml
    spec:
      strategy:
        type: RollingUpdate
        rollingUpdate:
          maxUnavailable: 0         # Never reduce capacity
          maxSurge: 2               # Add 2 extra pods during update
    ```
* **`maxUnavailable: 0` is critical for production** — without it, Kubernetes may terminate old pods before new ones are ready, causing brief capacity reduction.
* **Pros:** No extra infra cost, built into Kubernetes natively.
* **Cons:** During rollout, both versions run simultaneously — API must be backward compatible. Rollback is a new rolling deployment (takes time, not instant like blue-green).

---

### Feature Flags — the hidden fourth deployment strategy

* Feature flags decouple **deployment** from **release**. You deploy code to 100% of servers but the feature is off by default.
* **Tools:** LaunchDarkly, Unleash, AWS AppConfig, Flagsmith, GrowthBook.
* **Production pattern:**
    ```java
    if (featureFlags.isEnabled("new-payment-flow", userId)) {
        return newPaymentService.process(order);
    } else {
        return legacyPaymentService.process(order);
    }
    ```
* **Canary release via flags:** Enable flag for 5% of users → monitor → ramp to 100% → cleanup flag.
* **Kill switch:** If production issue — flip flag off in 30 seconds without a deploy.
* **Dark Launch:** Run new code path alongside old, compare outputs, never show to users. Validate correctness before any user exposure.

---

## 5. Production Deployment Approaches at Big Companies

### How does Google/Meta approach deployments?

* **Google (Borg/GKE):**
    * All services are containerized. Deploys go through a staging → canary → production pipeline managed by **Spanner**-backed release tooling.
    * **Rollout policy:** Deploy to 1% → 5% → 10% → 50% → 100% with automated analysis at each step.
    * **Hermetic builds:** Every build is reproducible — same inputs always produce same binary. Enforced by Bazel.
    * **Binary authorization:** Only binaries built by trusted CI systems can be deployed to production (cryptographic attestation).
* **Meta (Tupperware/Manifold):**
    * "Push on Green" — if CI passes, code is automatically staged. Engineers approve staging → production.
    * **Sandcastle** runs 100,000+ CI jobs/day. Heavily distributed test infrastructure.
    * **Gatekeeper:** Feature flag system used for all feature releases. A feature ships to 0.1% → 1% → 10% → 100% over days/weeks.
* **Amazon:**
    * Deploy to one region → monitor for 1 hour → next region. Full global rollout takes hours to days.
    * **Weighted routing via Route53:** Traffic shifted at DNS level, not application level.
    * **Pre-mortem required:** High-risk deploys require a written pre-mortem before execution.
* **Flipkart/Swiggy/Razorpay (Indian product companies):**
    * Heavy use of Kubernetes + Helm + Argo CD (GitOps).
    * Deploy window enforcement: No deploys between 12PM-3PM (lunch peak for food delivery) or during sale events.
    * Mandatory dark hours: No production deploys between 10PM-6AM without explicit on-call approval.

---

### What is GitOps and how does it differ from traditional CI/CD?

* **Traditional CI/CD:** Pipeline pushes changes to infrastructure. CI system has `kubectl apply` credentials and directly modifies the cluster.
* **GitOps:** Git repository is the single source of truth for infrastructure state. A GitOps operator (Argo CD, Flux) continuously reconciles the cluster state to match what's in Git.

```
Traditional:
  CI Pipeline ──→ kubectl apply ──→ K8s Cluster
  (push model)

GitOps:
  Developer ──→ Git Repo (Helm values, K8s manifests)
                    ↑
               Argo CD (pull model) ──→ K8s Cluster
               (constantly syncs)
```

* **Benefits of GitOps:**
    * **Auditability:** Every change to production is a Git commit — who, what, when, why.
    * **Disaster recovery:** If cluster is destroyed, re-point Argo CD at the Git repo → cluster rebuilt automatically.
    * **Security:** CI pipeline doesn't need cluster credentials. Only the in-cluster operator has access.
    * **Rollback:** `git revert` → Argo CD detects drift → auto-syncs back to previous state.

---

### What is a Change Freeze and how do CI/CD systems enforce it?

* **Change Freeze:** A period during which production deployments are blocked — typically around major sales (Diwali, Black Friday), year-end, or peak business hours.
* **Enforcement in GitHub Actions:**
    ```yaml
    - name: Check deployment window
      run: |
        HOUR=$(date -u +%H)
        DOW=$(date -u +%u)  # 1=Monday, 7=Sunday
        
        # Block deploys on weekends and outside 6AM-8PM UTC
        if [ "$DOW" -gt 5 ] || [ "$HOUR" -lt 6 ] || [ "$HOUR" -ge 20 ]; then
          echo "🚫 Outside deployment window. Get on-call approval."
          exit 1
        fi
        
        # Check Opsgenie/PagerDuty for active incidents
        python scripts/check_active_incidents.py || exit 1
        
        echo "✅ Deployment window is open"
    ```
* **Dynamic freeze via feature flag:** Store `deployment_freeze=true` in a config service. CI pipeline checks it before deploying. Operations team can toggle it without code changes.

---

## 6. Production Incidents & How They Were Resolved

### Incident 1: Bad deploy caused 30% error rate spike — rollback took 25 minutes

* **What happened:** A deploy introduced a bug in the payment service. Error rate jumped from 0.1% to 30% within 2 minutes of full rollout. Rollback took 25 minutes because the team had to manually revert the Helm chart, rebuild, push, and redeploy.
* **Root cause of slow rollback:** The pipeline didn't preserve the previous working image tag. The rollback required a new CI run (12 minutes) + deploy (8 minutes) + smoke tests (5 minutes).
* **Fix implemented:**
    * All Docker images now tagged with Git SHA and stored for 30 days: `app:sha-abc1234`.
    * Added a `rollback` workflow that takes an image tag as input and deploys it directly — no rebuild needed. Rollback now takes 90 seconds.
    * Automated canary analysis that auto-rolls-back if error rate exceeds 1% during rollout — the bad deploy would have been stopped at 5% traffic, not 100%.
    ```yaml
    # Emergency rollback workflow
    name: Emergency Rollback
    on:
      workflow_dispatch:
        inputs:
          image_tag:
            description: 'Git SHA to roll back to (e.g., sha-abc1234)'
            required: true
    jobs:
      rollback:
        environment: production
        runs-on: ubuntu-latest
        steps:
          - name: Rollback to ${{ inputs.image_tag }}
            run: |
              kubectl set image deployment/payment-service \
                app=ghcr.io/myorg/payment:${{ inputs.image_tag }} \
                --namespace=production
              kubectl rollout status deployment/payment-service --timeout=3m
    ```
* **Lesson:** Rollback must be faster than the deploy. Rollback is not a new deploy — it's a pointer change to a known-good image.

---

### Incident 2: Database migration caused 45 minutes of downtime on deploy day

* **What happened:** A schema migration that added a NOT NULL column with no default value ran on the production database (200M rows). `ALTER TABLE` locked the table for 38 minutes. All writes to that table returned errors. 45 minutes of partial outage.
* **Root cause:** The migration was tested on a 10K row staging database (took 0.2 seconds). Nobody tested on production-scale data. The pipeline had no migration safety check.
* **Fix implemented:**
    * **Pre-flight migration check in CI:**
        ```yaml
        - name: Estimate migration duration on production scale
          run: |
            python scripts/estimate_migration_time.py \
              --migration-file=V42__add_column.sql \
              --table=orders \
              --row-count=$(mysql -e "SELECT COUNT(*) FROM orders" production)
        ```
    * **Mandatory online DDL:** All migrations now reviewed for whether they use `ALGORITHM=INPLACE, LOCK=NONE` or require `gh-ost`.
    * **Migration dry-run on a production replica** before every production deploy that includes migrations.
    * **Deploy pipeline now separates migration from code deploy** — migration runs first, validated, then new code deploys.
* **Lesson:** Never test migrations only on staging. "It works on staging" is not sufficient for schema changes. Test on a production-sized replica.

---

### Incident 3: CI passed, but production broke due to environment-specific config

* **What happened:** A new microservice worked perfectly in CI (using `application-test.properties`) but failed in production because a required environment variable `PAYMENT_GATEWAY_URL` was missing from the production Kubernetes secret. The service started, passed health checks (which didn't call the payment gateway), but every payment request returned 500.
* **Root cause:** Health checks were too shallow — they only checked `GET /health` which returned 200 even when payment gateway was unreachable. No validation that all required env vars were present.
* **Fix implemented:**
    * **Startup config validation:**
        ```java
        @Component
        public class ConfigValidator implements ApplicationListener<ApplicationReadyEvent> {
            @Value("${payment.gateway.url:#{null}}")
            private String paymentGatewayUrl;

            @Override
            public void onApplicationEvent(ApplicationReadyEvent event) {
                if (paymentGatewayUrl == null || paymentGatewayUrl.isBlank()) {
                    throw new IllegalStateException("PAYMENT_GATEWAY_URL is required but not configured");
                }
                // Validate connectivity
                try {
                    new URL(paymentGatewayUrl).openConnection().connect();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot reach payment gateway: " + paymentGatewayUrl);
                }
            }
        }
        ```
    * **Deep health check endpoint:**
        ```
        GET /health/deep
        → Checks DB connectivity
        → Checks Redis connectivity
        → Checks payment gateway reachability
        → Returns 503 if any dependency is unreachable
        ```
    * **Post-deploy smoke tests** that actually call the payment flow end-to-end (with a test order).
* **Lesson:** Health checks must reflect actual service readiness, not just "process is running." Shallow health checks give false confidence.

---

### Incident 4: Secrets leaked into CI logs, required full key rotation

* **What happened:** A developer added a debug `echo` statement: `echo "Connecting to DB: $DATABASE_URL"`. The CI log (visible to all developers in the org) printed the full production connection string including password. By the time it was noticed (3 days later), the log had been viewed 47 times.
* **Root cause:** No log scrubbing, secrets passed as plain env vars to commands that could log them.
* **Fix implemented:**
    * **GitHub Actions secret masking** (automatic — any value registered as a `${{ secrets.X }}` is replaced with `***` in logs). Ensure ALL sensitive values come from secrets, never hardcoded.
    * **Pre-commit hook** to detect secrets before they're committed:
        ```bash
        # Install git-secrets or gitleaks
        brew install gitleaks
        # Pre-commit hook
        gitleaks protect --staged --redact
        ```
    * **CI step that scans for secrets in code:**
        ```yaml
        - name: Secret scan (Gitleaks)
          uses: gitleaks/gitleaks-action@v2
          env:
            GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        ```
    * **Principle of least privilege:** CI jobs now use short-lived OIDC tokens (not long-lived IAM keys) for AWS access. Tokens expire after the job ends.
        ```yaml
        - name: Configure AWS credentials via OIDC
          uses: aws-actions/configure-aws-credentials@v4
          with:
            role-to-assume: arn:aws:iam::123456789:role/github-actions-role
            aws-region: ap-south-1
        ```
* **Lesson:** Assume CI logs are readable by your entire engineering org. Design accordingly. Use OIDC for cloud credentials — no long-lived secrets.

---

### Incident 5: Dependency update in CI broke production silently

* **What happened:** A `package.json` had `"lodash": "^4.0.0"` (caret = allow minor updates). A new minor version of lodash introduced a behavior change. Old CI cache had lodash 4.17.19. A cache miss caused lodash 4.17.21 to be installed. Tests passed (no test covered the changed behavior). Production broke for a specific edge case in data transformation.
* **Root cause:** Non-deterministic dependency versions + no lockfile enforcement in CI.
* **Fix implemented:**
    * **Lockfile enforcement:** `npm ci` (instead of `npm install`) — fails if `package-lock.json` is out of sync. Never use `npm install` in CI.
        ```yaml
        - name: Install dependencies (locked)
          run: npm ci --prefer-offline  # Uses package-lock.json exactly
        ```
    * **Dependabot with auto-merge for patch only:**
        ```yaml
        # .github/dependabot.yml
        updates:
          - package-ecosystem: "npm"
            directory: "/"
            schedule:
              interval: "weekly"
            allow:
              - dependency-type: "direct"
            versioning-strategy: "increase-if-necessary"
        ```
        * Dependabot opens PRs for dependency updates. Minor/patch updates auto-merge if tests pass. Major updates require human review.
    * **Artifact caching by lockfile hash** — cache only invalidates when lockfile changes.
* **Lesson:** CI must be deterministic. Floating version ranges + non-cached installs = non-reproducible builds. `npm ci`, `pip install --require-hashes`, `mvn dependency:resolve -Dsort` — always pin or lock.

---

## 7. Advanced GitHub Actions Patterns

### How do you implement matrix builds for cross-version testing?

```yaml
jobs:
  test:
    strategy:
      fail-fast: false      # Don't cancel all matrix jobs if one fails
      matrix:
        java: ['17', '21']
        os: ['ubuntu-latest', 'windows-latest']
        exclude:
          - os: windows-latest
            java: '17'      # Skip this combination
        include:
          - os: ubuntu-latest
            java: '21'
            experimental: false

    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
      - run: mvn test
```

---

### How do you implement reusable workflows to avoid DRY violations across repos?

```yaml
# .github/workflows/reusable-deploy.yml (in a shared repo)
name: Reusable Deploy Workflow
on:
  workflow_call:
    inputs:
      environment:
        required: true
        type: string
      image-tag:
        required: true
        type: string
    secrets:
      KUBE_CONFIG:
        required: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: ${{ inputs.environment }}
    steps:
      - name: Deploy ${{ inputs.image-tag }} to ${{ inputs.environment }}
        run: |
          kubectl set image deployment/app app=ghcr.io/myorg/app:${{ inputs.image-tag }}
        env:
          KUBECONFIG: ${{ secrets.KUBE_CONFIG }}

# ─────────────────────────────────────────────────────
# In the calling repo:
# .github/workflows/deploy.yml
jobs:
  call-deploy:
    uses: myorg/shared-workflows/.github/workflows/reusable-deploy.yml@main
    with:
      environment: production
      image-tag: sha-${{ github.sha }}
    secrets:
      KUBE_CONFIG: ${{ secrets.PROD_KUBE_CONFIG }}
```

---

### How do you implement dynamic environments for PR previews?

```yaml
# Create a preview environment for every PR
name: PR Preview Deploy
on:
  pull_request:
    types: [opened, synchronize]

jobs:
  preview:
    runs-on: ubuntu-latest
    steps:
      - name: Deploy PR preview
        run: |
          NAMESPACE="pr-${{ github.event.pull_request.number }}"
          kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
          helm upgrade --install app-preview ./helm/app \
            --namespace=$NAMESPACE \
            --set image.tag=sha-${{ github.sha }} \
            --set ingress.host=pr-${{ github.event.pull_request.number }}.preview.myapp.com

      - name: Comment preview URL on PR
        uses: actions/github-script@v7
        with:
          script: |
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '🚀 Preview deployed: https://pr-${{ github.event.pull_request.number }}.preview.myapp.com'
            })

  cleanup:
    if: github.event.action == 'closed'
    runs-on: ubuntu-latest
    steps:
      - name: Delete preview namespace
        run: kubectl delete namespace pr-${{ github.event.pull_request.number }} --ignore-not-found
```

---

## 8. Security in CI/CD

### What is SLSA and how does GitHub Actions support it?

* **SLSA (Supply-chain Levels for Software Artifacts):** A framework for software supply chain security, with levels 1-4 of increasing assurance.
* **SLSA Level 1:** Build process is documented (basic CI).
* **SLSA Level 2:** Version-controlled build, signed provenance. GitHub Actions with `docker/build-push-action` and `--provenance=true` achieves this automatically.
* **SLSA Level 3:** Hardened build environment, isolated runs, non-falsifiable provenance. GitHub's hosted runners are SLSA Level 3 compliant.
* **Practical implementation:**
    ```yaml
    - name: Build with provenance
      uses: docker/build-push-action@v5
      with:
        push: true
        tags: myapp:latest
        provenance: true          # Generates SLSA provenance attestation
        sbom: true                # Software Bill of Materials (lists all dependencies)
    ```

---

### How do you prevent supply chain attacks in CI/CD?

* **Pin Action versions to full SHA (not tags):**
    ```yaml
    # ❌ Vulnerable — tag can be moved to point to malicious code
    uses: actions/checkout@v4

    # ✅ Safe — SHA cannot change
    uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
    ```
    * Use `dependabot` to auto-update pinned SHAs.
* **Minimal permissions:** Every workflow should declare `permissions` explicitly:
    ```yaml
    permissions:
      contents: read          # Don't give write unless needed
      packages: write         # Only for the job that pushes to GHCR
      id-token: write         # Only for OIDC authentication
    ```
* **OIDC instead of long-lived secrets** for cloud provider auth.
* **Scan container images:** Run Trivy or Snyk Container on every built image before pushing:
    ```yaml
    - name: Scan image for vulnerabilities
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: ${{ env.IMAGE_NAME }}:sha-${{ github.sha }}
        exit-code: '1'                    # Fail pipeline on HIGH/CRITICAL vulns
        severity: 'HIGH,CRITICAL'
    ```

---

## 9. Observability & Rollbacks

### How do you know when to roll back a deployment automatically?

* **Key signals to monitor post-deploy (first 15 minutes are critical):**
    * HTTP 5xx error rate (threshold: `> 1% above baseline`)
    * P99 latency spike (threshold: `> 200ms above baseline`)
    * Business KPIs: order success rate, payment conversion, login success rate
    * JVM metrics: GC pause time, heap usage, thread count
* **Automated rollback via Argo Rollouts:**
    ```yaml
    apiVersion: argoproj.io/v1alpha1
    kind: Rollout
    spec:
      strategy:
        canary:
          analysis:
            templates:
              - templateName: error-rate-analysis
            startingStep: 2         # Start analysis after step 2
            args:
              - name: service-name
                value: payment-service
          steps:
            - setWeight: 5
            - pause: {duration: 5m}   # Wait 5 minutes
            - setWeight: 25
            - pause: {duration: 5m}
            - setWeight: 100
    ---
    apiVersion: argoproj.io/v1alpha1
    kind: AnalysisTemplate
    spec:
      metrics:
        - name: error-rate
          interval: 1m
          failureLimit: 2           # Allow 2 bad measurements before failing
          provider:
            prometheus:
              address: http://prometheus:9090
              query: |
                sum(rate(http_requests_total{status=~"5..",job="payment-service"}[1m]))
                /
                sum(rate(http_requests_total{job="payment-service"}[1m]))
          successCondition: result[0] < 0.01   # < 1% error rate
    ```

---

### How do you implement deployment notifications and audit trails?

```yaml
# Post-deploy Slack notification
- name: Notify Slack on success
  if: success()
  uses: slackapi/slack-github-action@v1
  with:
    payload: |
      {
        "text": "✅ *${{ github.repository }}* deployed to production",
        "blocks": [
          {
            "type": "section",
            "fields": [
              {"type": "mrkdwn", "text": "*Version:* sha-${{ github.sha }}"},
              {"type": "mrkdwn", "text": "*Deployed by:* ${{ github.actor }}"},
              {"type": "mrkdwn", "text": "*PR:* ${{ github.event.pull_request.title }}"}
            ]
          }
        ]
      }
  env:
    SLACK_WEBHOOK_URL: ${{ secrets.SLACK_DEPLOY_WEBHOOK }}

# Post-deploy failure notification with rollback button
- name: Notify Slack on failure
  if: failure()
  uses: slackapi/slack-github-action@v1
  with:
    payload: |
      {
        "text": "🚨 *DEPLOY FAILED* — ${{ github.repository }}",
        "blocks": [
          {
            "type": "actions",
            "elements": [
              {
                "type": "button",
                "text": {"type": "plain_text", "text": "🔄 Trigger Rollback"},
                "url": "${{ github.server_url }}/${{ github.repository }}/actions/workflows/rollback.yml"
              }
            ]
          }
        ]
      }
```

---

## 10. Senior-Level Architecture Questions

### How would you design a CI/CD system for a microservices architecture with 50+ services?

* **The Problem:** With 50+ services, a "deploy everything on every commit" approach doesn't scale. You get:
    * 50 CI pipelines triggered on unrelated changes.
    * Slow feedback because every pipeline is long.
    * Cross-service breaking changes discovered in staging, not CI.
* **Solution: Affected-service detection (changed files → affected services):**
    ```yaml
    # Detect which services changed
    - name: Detect changed services
      id: changes
      run: |
        CHANGED=$(git diff --name-only origin/main... | grep '^services/' | cut -d/ -f2 | sort -u | tr '\n' ',')
        echo "services=$CHANGED" >> $GITHUB_OUTPUT

    - name: Run CI only for changed services
      if: contains(steps.changes.outputs.services, 'payment-service')
      run: cd services/payment-service && mvn test
    ```
* **Monorepo tooling:** Use **Nx** (Node.js), **Bazel** (language-agnostic), or **Turborepo** — they understand dependency graphs. If `shared-lib` changes → automatically detect all services that depend on it → trigger their CI.
* **Contract testing (Pact):** Each service publishes its consumer contracts to a Pact Broker. Before any service deploys, it verifies all its consumers' contracts are still satisfied. Cross-service breaking changes caught in CI.
* **Independent deployment:** Each service has its own version, deploy pipeline, and rollback history. Services are not deployed together.
* **Platform team owns the base pipeline:** Services inherit a reusable workflow with security scanning, coverage gates, image signing. Teams customize via inputs.

---

### How do you measure CI/CD pipeline health? What metrics matter?

* **DORA Metrics (industry standard):**
    * **Deployment Frequency:** How often you deploy to production. Elite: multiple times/day. Low: once/month.
    * **Lead Time for Changes:** Time from code commit to running in production. Elite: <1 hour. Low: weeks.
    * **Change Failure Rate:** Percentage of deployments that cause a production incident. Elite: 0-15%.
    * **Mean Time to Restore (MTTR):** How long to recover from a production failure. Elite: <1 hour. Low: days.
* **Pipeline-specific metrics:**
    * **Build success rate:** If <85%, investigate — too many flaky tests or infra issues.
    * **Average pipeline duration:** Unit tests should be <5 min, full pipeline <20 min. Longer → engineers stop waiting for CI and move on.
    * **Queue time:** Time a job spends waiting for a runner. If high, add more runners or use self-hosted.
    * **Cache hit rate:** Low cache hit rate = slow installs on every run.
* **Track with:** GitHub Actions insights (built-in), Datadog CI Visibility, Grafana dashboards on pipeline webhooks.

---

### How do you handle secrets rotation without downtime?

* **The Problem:** When you rotate a secret (DB password, API key), both old and new credentials may need to be valid simultaneously during the transition.
* **Pattern: Versioned secrets with parallel validity:**
    ```
    Step 1: Add new credential (old still works)
    Step 2: Update CI/CD to use new credential
    Step 3: Deploy all services using new credential
    Step 4: Revoke old credential
    ```
* **AWS Secrets Manager rotation (automated):**
    * Define a Lambda rotation function. Secrets Manager calls it automatically on schedule.
    * Lambda creates new password in DB, updates the secret, old password remains valid for `pending` window, then is revoked.
    * Services read secrets via SDK at startup — next restart picks up new credentials.
* **Zero-downtime rotation for running services:**
    * Services cache credentials in memory but handle `AuthenticationException` by fetching fresh credentials and retrying once before propagating the error. This handles credential rotation without restart.
* **In GitHub Actions:** Use environment-scoped secrets. Rotation: update the secret value in GitHub UI → all new jobs pick it up automatically. Running jobs use the value that was loaded when the job started.

---

### What is the difference between artifact promotion and rebuilding per environment?

* **Rebuild per environment (anti-pattern):**
    ```
    Dev: Build image → tag as :dev → test
    Staging: Build SAME code again → tag as :staging → test
    Production: Build SAME code AGAIN → tag as :prod → deploy
    ```
    * **Problems:** Each build may produce slightly different output (different dependency versions pulled, different build times). The artifact you tested is NOT the artifact you deployed. This violates the core CI/CD principle.
* **Artifact promotion (correct approach):**
    ```
    Build once: sha-abc1234 → push to registry
    Dev test: deploy sha-abc1234 → pass ✅
    Staging test: promote sha-abc1234 → pass ✅
    Production: promote same sha-abc1234 → deploy
    ```
    * The binary that passed all tests is exactly the binary in production. No rebuilds. Tag the same image with environment tags:
    ```bash
    # Promote staging image to production tag
    docker buildx imagetools create \
      --tag myregistry/app:production \
      myregistry/app:sha-abc1234
    ```
* **Artifact registry strategy:**
    * Tag with Git SHA (immutable, traceable).
    * Add semantic version tag on release (`v2.1.0`).
    * Add `latest` tag for convenience (never deploy using `latest` in production — not immutable).

---

*Last updated: 2026 — covers GitHub Actions, Argo CD/Rollouts, SLSA, DORA metrics, and production patterns used at companies like Google, Meta, Amazon, Razorpay, Swiggy, and Flipkart.*