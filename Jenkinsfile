pipeline {
    agent any

    tools {
        maven 'maven-3.8.4'
        jdk 'JDK21'
    }

    environment {
        IMAGE_NAME = "tns-capital-skeleton"

        // Security scanners run as pinned Docker images so the Jenkins agent only
        // needs the Docker CLI — nothing to install, and every build uses the same
        // scanner version. Bump these deliberately, not via :latest.
        GITLEAKS_IMAGE = "zricethezav/gitleaks:v8.21.2"
        SEMGREP_IMAGE  = "semgrep/semgrep:1.99.0"
        TRIVY_IMAGE    = "aquasec/trivy:0.57.1"

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
                    // Any finding fails the stage. Confirmed false positives go in .gitleaksignore.
                    sh '''
                        docker run --rm --user "$(id -u):$(id -g)" \
                            -v "$WORKSPACE":/repo -w /repo \
                            "$GITLEAKS_IMAGE" git /repo \
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
                sh 'mvn -B clean package'
                sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} ."
            }
        }

        stage('Test') {
            steps {
                // Runs the Maven test suite inside the build environment.
                // -B (batch mode) suppresses interactive prompts so output is clean in logs.
                sh "mvn -B test"
            }
            post {
                always {
                    // Publishes JUnit XML results to Jenkins regardless of pass/fail.
                    // This gives a test-trend chart in the Jenkins UI and lets branch
                    // protection rules check the test result as a status check.
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Dependency Scan') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    // Trivy checks libraries against known CVEs in two places:
                    //   fs    — the manifests in the repo (pom.xml, auth-stub package-lock.json)
                    //   image — the built image: every jar inside the fat jar plus OS packages
                    // Reports include HIGH and CRITICAL. The gate fails only on CRITICAL
                    // findings that have a fixed version available (--ignore-unfixed).
                    // Accepted risks go in .trivyignore. The trivy-cache volume keeps the
                    // vulnerability DB between builds so it isn't re-downloaded each time.
                    sh '''
                        TRIVY="docker run --rm -v trivy-cache:/root/.cache/ \
                            -v /var/run/docker.sock:/var/run/docker.sock \
                            -v $WORKSPACE:/src:ro -w /src $TRIVY_IMAGE"
                        COMMON="--scanners vuln --ignorefile /src/.trivyignore --quiet"

                        $TRIVY fs $COMMON --severity HIGH,CRITICAL \
                            --format sarif /src > "$REPORTS_DIR/trivy-fs.sarif"
                        $TRIVY image $COMMON --severity HIGH,CRITICAL \
                            --format sarif "$IMAGE_NAME:$BUILD_NUMBER" > "$REPORTS_DIR/trivy-image.sarif"

                        GATE_FAILED=0
                        $TRIVY fs $COMMON --severity CRITICAL --ignore-unfixed \
                            --exit-code 1 /src || GATE_FAILED=1
                        $TRIVY image $COMMON --severity CRITICAL --ignore-unfixed \
                            --exit-code 1 "$IMAGE_NAME:$BUILD_NUMBER" || GATE_FAILED=1
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
        }
        failure {
            // Notifies the team on build failure. Replace with your notification
            // mechanism (Slack, email, Teams) once Jenkins is configured.
            echo "Build ${BUILD_NUMBER} failed — check console output and ${REPORTS_DIR}/ artifacts."
        }
        success {
            echo "Build ${BUILD_NUMBER} passed."
        }
    }
}
