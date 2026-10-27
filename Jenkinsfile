pipeline {
    agent any

    tools {
        maven 'maven-3.8.4'
        jdk 'JDK21'
    }

    environment {
        IMAGE_NAME = "tns-capital-skeleton"
        // The auth service image, built only so Trivy can scan its OS packages and Node runtime
        AUTH_IMAGE_NAME = "tns-capital-auth"
        // The strategy service image, built only so Trivy can scan its jar and OS packages
        STRATEGY_IMAGE_NAME = "tns-capital-strategy"

        // Security scanners run as pinned Docker images so the Jenkins agent only
        // needs the Docker CLI — nothing to install, and every build uses the same
        // scanner version. Bump these deliberately, not via :latest.
        GITLEAKS_IMAGE = "zricethezav/gitleaks:v8.21.2"
        SEMGREP_IMAGE  = "semgrep/semgrep:1.99.0"
        TRIVY_IMAGE    = "aquasec/trivy:0.57.1"

        // Node and a matching headless Chromium for the Angular tests, pinned the same way
        PLAYWRIGHT_IMAGE = "mcr.microsoft.com/playwright:v1.64.0-noble"

        // All scan reports land here and are archived with the build.
        REPORTS_DIR = "security-reports"
    }

    stages {

        stage('Checkout') {
            steps {
                // Checks out the branch that triggered this build.
                // In a Multibranch Pipeline this covers main, feature branches,
                // and PRs automatically — no per-branch configuration needed.
                checkout scm
                sh "mkdir -p ${REPORTS_DIR}"
            }
        }

        // Each security stage is wrapped in catchError: a failing gate marks the
        // stage and build as FAILED, but the remaining stages still run so a single
        // build reports every finding instead of stopping at the first one.

        stage('Secret Scan') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    // Gitleaks scans the full git history, not just the current files,
                    // because a secret that was committed and later deleted is still exposed.
                    // --log-opts=HEAD limits it to this branch's history; by default it scans
                    // every branch in the clone, so one branch's leak would fail all builds.
                    // Any finding fails the stage. Confirmed false positives go in .gitleaksignore.
                    sh '''
                        docker run --rm --user "$(id -u):$(id -g)" \
                            -v "$WORKSPACE":/repo -w /repo \
                            "$GITLEAKS_IMAGE" git /repo --log-opts="HEAD" \
                            --redact --verbose \
                            --report-format sarif --report-path "/repo/$REPORTS_DIR/gitleaks.sarif"
                    '''
                }
            }
        }

        stage('SAST') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    // Semgrep static analysis with the Java and OWASP Top 10 rule packs.
                    // Pass 1 reports every finding (all severities) to the console and SARIF.
                    // Pass 2 is the gate: it fails only on ERROR-severity (critical) rules.
                    sh '''
                        SEMGREP="docker run --rm --user $(id -u):$(id -g) -e HOME=/tmp \
                            -v $WORKSPACE:/src -w /src $SEMGREP_IMAGE semgrep scan \
                            --config p/java --config p/owasp-top-ten \
                            --metrics=off --exclude target --exclude $REPORTS_DIR"

                        $SEMGREP --sarif-output="$REPORTS_DIR/semgrep.sarif" --text
                        $SEMGREP --severity ERROR --error --quiet
                    '''
                }
            }
        }

        stage('Build Image') {
            steps {
                // Builds the Docker image using the multi-stage Dockerfile from Lab 06.
                // Tags with the Jenkins build number so every build produces a uniquely
                // tagged image — avoids overwriting previous builds' artefacts.
                // Tests are skipped here because the Test stage runs them.
                sh 'mvn -B clean package -DskipTests'
                sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} ."
            }
        }

        stage('Test') {
            steps {
                // verify runs the unit tests (surefire) and the *IT integration tests
                // (failsafe). The integration tests start Postgres and Kafka with
                // Testcontainers, so the agent needs Docker, which it already has for
                // the image build. -B (batch mode) keeps the log output clean.
                sh "mvn -B verify"
            }
            post {
                always {
                    // Publishes JUnit XML results to Jenkins regardless of pass/fail.
                    // This gives a test-trend chart in the Jenkins UI and lets branch
                    // protection rules check the test result as a status check.
                    junit 'target/surefire-reports/*.xml, target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Broker Simulator Tests') {
            steps {
                // The Broker Simulator is its own Maven project
                dir('broker-simulator') {
                    sh 'mvn -B test'
                }
            }
            post {
                always {
                    junit 'broker-simulator/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Strategy Service Tests') {
            steps {
                // The Strategy Lab service is its own Maven project. verify runs its unit tests and
                // StrategyServiceIT (Postgres built from db/ and Kafka, with Testcontainers), and
                // writes the JaCoCo report SonarQube reads
                dir('strategy-service') {
                    sh 'mvn -B verify'
                }
            }
            post {
                always {
                    junit 'strategy-service/target/surefire-reports/*.xml, strategy-service/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('ETL Tests') {
            steps {
                // The ETL pytest suite, including its Postgres integration tests against a
                // throwaway database, with coverage for SonarQube (see ci/python-tests.sh)
                sh 'ci/python-tests.sh etl'
            }
            post {
                always {
                    junit 'etl/test-results.xml'
                }
            }
        }

        stage('Dashboard Tests') {
            steps {
                // The dashboard's tests: insights, the Streamlit page (headless) and data.py
                // against a throwaway database, with coverage for SonarQube
                sh 'ci/python-tests.sh dashboard'
            }
            post {
                always {
                    junit 'dashboard/test-results.xml'
                }
            }
        }

        stage('Auth Service Tests') {
            steps {
                // The NestJS auth service's Jest suite in node:22-alpine (the image its
                // container uses), including its Postgres tests against a throwaway database
                // built from db/, with coverage for SonarQube (see ci/auth-tests.sh)
                sh 'ci/auth-tests.sh'
            }
        }

        stage('Frontend Tests') {
            steps {
                // The Angular unit tests in headless Chromium, then a production build so a
                // compile error fails here. npm's pretest/prebuild hooks first generate the API
                // clients in src/app/api from contracts/, which needs Java: the JDK21 tool is
                // mounted read-only, since the Playwright image ships Chromium but no JDK.
                // --code-coverage writes frontend/coverage/frontend/lcov.info for SonarQube.
                sh '''
                    docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
                        -v "$JAVA_HOME":/opt/jdk:ro -e JAVA_HOME=/opt/jdk \
                        -v "$WORKSPACE":/repo -w /repo/frontend $PLAYWRIGHT_IMAGE \
                        bash -c 'export PATH="$JAVA_HOME/bin:$PATH" \
                            CHROME_BIN="$(ls -d /ms-playwright/chromium-*/chrome-linux64/chrome | head -n 1)" \
                            && npm ci --no-audit --no-fund \
                            && npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage \
                            && npm run build'
                '''
            }
        }

        stage('E2E Tests') {
            steps {
                // The Playwright journeys (frontend/e2e) in a real browser against the whole
                // stack: register, log in, place a BUY that fills, the route guard, support
                // suspending an account, and an admin reaching the analytics dashboard. ci/e2e-tests.sh starts a throwaway Compose
                // project with no host ports, runs the suite in PLAYWRIGHT_IMAGE on its network,
                // then removes it, so it can't clash with a stack already running on this host.
                sh 'ci/e2e-tests.sh'
            }
            post {
                always {
                    junit testResults: 'frontend/e2e-results.xml', allowEmptyResults: true
                    // Report, traces and screenshots of failed tests, and the services' logs on failure
                    archiveArtifacts artifacts: 'frontend/playwright-report/**, frontend/test-results/**, frontend/e2e-stack.log',
                        allowEmptyArchive: true
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    // Uploads the analysis (including the JaCoCo coverage report from the
                    // Test stage) to the SonarQube server configured as 'sonarserver'.
                    // sonar.qualitygate.wait=true blocks until SonarQube has evaluated the
                    // Quality Gate and fails the stage if it does not pass — without it the
                    // stage would succeed as soon as the report was uploaded.
                    // The heap caps keep the scanner JVM and the Node.js process of the
                    // TypeScript analysis (which sizes itself to 1.4 GB by default) small
                    // enough that systemd-oomd does not kill Jenkins on this host.
                    withSonarQubeEnv('sonarserver') {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            sh 'MAVEN_OPTS="-Xmx768m" mvn -B sonar:sonar -Dsonar.token=$SONAR_TOKEN -Dsonar.qualitygate.wait=true -Dsonar.javascript.node.maxspace=768'
                        }
                    }
                }
            }
        }

        stage('Dependency Scan') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    // Trivy checks libraries against known CVEs in two places:
                    //   fs    — the manifests in the repo (pom.xml, auth-service package-lock.json)
                    //   image — the built images: every jar inside the API's fat jar plus OS
                    //           packages, and the auth service's Node runtime, node_modules and
                    //           OS packages (OWASP A06-4)
                    // Reports include HIGH and CRITICAL. The gate fails only on CRITICAL
                    // findings that have a fixed version available (--ignore-unfixed).
                    // Accepted risks go in .trivyignore. The trivy-cache volume keeps the
                    // vulnerability DB between builds so it isn't re-downloaded each time.
                    // The databases come from GitHub's registry: Trivy's default mirror
                    // (mirror.gcr.io) served a broken copy, failing the stage on every branch.
                    sh '''
                        docker build -q -t "$AUTH_IMAGE_NAME:$BUILD_NUMBER" shared/auth-service > /dev/null
                        docker build -q -t "$STRATEGY_IMAGE_NAME:$BUILD_NUMBER" strategy-service > /dev/null
                        TRIVY="docker run --rm -v trivy-cache:/root/.cache/ \
                            -v /var/run/docker.sock:/var/run/docker.sock \
                            -v $WORKSPACE:/src:ro -w /src $TRIVY_IMAGE"
                        COMMON="--scanners vuln --ignorefile /src/.trivyignore --quiet \
                            --db-repository ghcr.io/aquasecurity/trivy-db:2 \
                            --java-db-repository ghcr.io/aquasecurity/trivy-java-db:1"

                        $TRIVY fs $COMMON --severity HIGH,CRITICAL \
                            --format sarif /src > "$REPORTS_DIR/trivy-fs.sarif"
                        $TRIVY image $COMMON --severity HIGH,CRITICAL \
                            --format sarif "$IMAGE_NAME:$BUILD_NUMBER" > "$REPORTS_DIR/trivy-image.sarif"
                        $TRIVY image $COMMON --severity HIGH,CRITICAL \
                            --format sarif "$AUTH_IMAGE_NAME:$BUILD_NUMBER" > "$REPORTS_DIR/trivy-auth-image.sarif"
                        $TRIVY image $COMMON --severity HIGH,CRITICAL \
                            --format sarif "$STRATEGY_IMAGE_NAME:$BUILD_NUMBER" > "$REPORTS_DIR/trivy-strategy-image.sarif"

                        GATE_FAILED=0
                        $TRIVY fs $COMMON --severity CRITICAL --ignore-unfixed \
                            --exit-code 1 /src || GATE_FAILED=1
                        $TRIVY image $COMMON --severity CRITICAL --ignore-unfixed \
                            --exit-code 1 "$IMAGE_NAME:$BUILD_NUMBER" || GATE_FAILED=1
                        $TRIVY image $COMMON --severity CRITICAL --ignore-unfixed \
                            --exit-code 1 "$AUTH_IMAGE_NAME:$BUILD_NUMBER" || GATE_FAILED=1
                        $TRIVY image $COMMON --severity CRITICAL --ignore-unfixed \
                            --exit-code 1 "$STRATEGY_IMAGE_NAME:$BUILD_NUMBER" || GATE_FAILED=1
                        exit $GATE_FAILED
                    '''
                }
            }
        }
    }

    post {
        always {
            // Security reports (SARIF) are attached to every build under "Build Artifacts",
            // whether the gates passed or not.
            archiveArtifacts artifacts: "${REPORTS_DIR}/**", allowEmptyArchive: true

            // Each build tags a ~300MB image that nothing uses afterwards; remove it so
            // the agent's disk doesn't fill up.
            sh "docker rmi ${IMAGE_NAME}:${BUILD_NUMBER} ${AUTH_IMAGE_NAME}:${BUILD_NUMBER} ${STRATEGY_IMAGE_NAME}:${BUILD_NUMBER} || true"

            // Every image build adds to Docker's build cache (about 3 GB a build), which filled
            // the disk until SonarQube's index went read-only and failed the analysis. Keep only
            // the newest 3 GB, so later builds still reuse the dependency layers, and drop the
            // untagged images the builds leave behind.
            sh 'docker builder prune -f --keep-storage 3GB > /dev/null || true'
            sh 'docker image prune -f > /dev/null || true'
        }
        failure {
            // Notifies the team on build failure. Replace with your notification
            // mechanism (Slack, email, Teams) once Jenkins is configured.
            echo "Build ${BUILD_NUMBER} failed — check console output and ${REPORTS_DIR}/ artifacts."
        }
        success {
            echo "Build ${BUILD_NUMBER} passed."
        }
        cleanup {
            // Runs last, after the reports are archived. Each workspace holds node_modules and
            // build output (~1 GB); without this, every branch ever built keeps one, which
            // filled the disk and took the agent offline. Nothing is lost: every stage
            // reinstalls its dependencies (npm ci) on the next build anyway.
            deleteDir()
        }
    }
}
