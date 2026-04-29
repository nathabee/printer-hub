pipeline {
    agent any

    parameters {
        string(
            name: 'GIT_BRANCH',
            defaultValue: 'develop',
            description: 'Git branch to build, for example develop or main.'
        )
        string(
            name: 'JAVA_HOME_OVERRIDE',
            defaultValue: '',
            description: 'Optional JAVA_HOME override. Leave empty to use the agent default.'
        )
        string(
            name: 'API_SMOKE_PORT',
            defaultValue: '18090',
            description: 'Port used for the local runtime smoke test.'
        )
        string(
            name: 'RELEASE_VERSION',
            defaultValue: '',
            description: 'Optional release version, for example 0.1.3. Leave empty for CI-only runs.'
        )
        booleanParam(
            name: 'PUBLISH_GITHUB_RELEASE',
            defaultValue: false,
            description: 'Publish the prepared release bundle to GitHub Releases. Only use for stable main releases.'
        )
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
    }

    environment {
        MAVEN_OPTS = '-Djava.awt.headless=true'
        GITHUB_REPO = 'nathabee/printer-hub'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.GIT_BRANCH}"]],
                    userRemoteConfigs: [[
                        url: 'https://github.com/nathabee/printer-hub.git'
                    ]]
                ])
            }
        }

        stage('Environment') {
            steps {
                script {
                    if (params.JAVA_HOME_OVERRIDE?.trim()) {
                        env.JAVA_HOME = params.JAVA_HOME_OVERRIDE.trim()
                        env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
                    }
                }

                sh '''
                    echo "JAVA_HOME=${JAVA_HOME:-<not-set>}"
                    which java || true
                    which javac || true
                    which mvn || true
                    which sqlite3 || true
                    java -version
                    javac -version
                    mvn -version
                '''
            }
        }

        stage('Verify') {
            steps {
                sh '''
                    set -eu

                    rm -rf target
                    rm -f printerhub.db printerhub-real.db printerhub-test.db printerhub-ci.db

                    mvn -B -ntp clean verify
                '''
            }
        }

        stage('Local Runtime Smoke Test') {
            steps {
                sh '''
                    set -eu

                    API_PORT="${API_SMOKE_PORT:-18090}"
                    DB_FILE="printerhub-ci.db"

                    mkdir -p target
                    rm -f "${DB_FILE}"

                    echo "Starting PrinterHub local runtime on port ${API_PORT}"
                    echo "Using database file ${DB_FILE}"

                    mvn -B -ntp exec:java \
                      -Dexec.mainClass="printerhub.Main" \
                      -Dprinterhub.api.port="${API_PORT}" \
                      -Dprinterhub.monitoring.intervalSeconds=1 \
                      -Dprinterhub.databaseFile="${DB_FILE}" \
                      > target/runtime-smoke.log 2>&1 &

                    APP_PID=$!

                    cleanup() {
                      kill "${APP_PID}" >/dev/null 2>&1 || true
                    }
                    trap cleanup EXIT

                    for i in $(seq 1 30); do
                      if curl -fsS "http://localhost:${API_PORT}/health" >/dev/null; then
                        break
                      fi
                      sleep 1
                    done

                    curl -fsS "http://localhost:${API_PORT}/health" > target/health.json

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-1",
                        "displayName": "CI Simulated Printer 1",
                        "portName": "SIM_PORT_1",
                        "mode": "simulated",
                        "enabled": true
                      }' > target/printer-created.json

                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-before.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-after.json

                    curl -fsS "http://localhost:${API_PORT}/dashboard" > target/dashboard.html
                    curl -fsS "http://localhost:${API_PORT}/dashboard/dashboard.css" > target/dashboard.css
                    curl -fsS "http://localhost:${API_PORT}/dashboard/dashboard.js" > target/dashboard.js

                    kill "${APP_PID}" >/dev/null 2>&1 || true
                    wait "${APP_PID}" >/dev/null 2>&1 || true
                    trap - EXIT

                    sqlite3 "${DB_FILE}" '.tables' > target/db-tables.txt
                    sqlite3 "${DB_FILE}" 'select id,name,port_name,mode,enabled from configured_printers order by id;' > target/configured-printers.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,state,created_at from printer_snapshots order by id desc limit 10;' > target/printer-snapshots.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,event_type,message,created_at from printer_events order by id desc limit 10;' > target/printer-events.txt

                    echo "Health:"
                    cat target/health.json

                    echo
                    echo "Created printer:"
                    cat target/printer-created.json

                    echo
                    echo "Printers before:"
                    cat target/printers-before.json

                    echo
                    echo "Printers after:"
                    cat target/printers-after.json

                    echo
                    echo "Database tables:"
                    cat target/db-tables.txt

                    echo
                    echo "Configured printers:"
                    cat target/configured-printers.txt

                    echo
                    echo "Printer snapshots:"
                    cat target/printer-snapshots.txt

                    echo
                    echo "Printer events:"
                    cat target/printer-events.txt

                    grep -q '"status":"ok"' target/health.json
                    grep -q '"printer-1"' target/printer-created.json
                    grep -q '"printer-1"' target/printers-after.json
                    grep -q '"state":"IDLE"' target/printers-after.json
                    grep -q '"hotendTemperature":21.80' target/printers-after.json
                    grep -q '"bedTemperature":21.52' target/printers-after.json
                    grep -q '"updatedAt"' target/printers-after.json

                    grep -q 'PrinterHub Dashboard' target/dashboard.html
                    grep -q 'printer-grid'  target/dashboard.css
                    grep -q 'loadDashboard' target/dashboard.js

                    grep -q 'configured_printers' target/db-tables.txt
                    grep -q 'printer_snapshots' target/db-tables.txt
                    grep -q 'printer_events' target/db-tables.txt

                    grep -q 'printer-1' target/configured-printers.txt
                    grep -q 'printer-1' target/printer-snapshots.txt
                    grep -q 'PRINTER_POLLED' target/printer-events.txt

                    test "$(cat target/printers-before.json)" != "$(cat target/printers-after.json)"

                    echo
                    echo "Runtime smoke log:"
                    cat target/runtime-smoke.log
                '''
            }
        }

        stage('Prepare Release Bundle') {
            when {
                expression {
                    return params.RELEASE_VERSION?.trim()
                }
            }
            steps {
                sh '''
                    set -eu

                    rm -rf release
                    mkdir -p release

                    if ls target/*-all.jar >/dev/null 2>&1; then
                      cp target/*-all.jar release/
                    fi

                    if [ -f target/operator-message-report.md ]; then
                      cp target/operator-message-report.md release/
                    fi

                    if [ -d target/site/jacoco ]; then
                      mkdir -p release/jacoco
                      cp -r target/site/jacoco/* release/jacoco/
                    fi

                    if [ -f README.md ]; then
                      cp README.md release/
                    fi

                    if [ -f docs/test.md ]; then
                      cp docs/test.md release/
                    fi

                    if [ -f docs/devops.md ]; then
                      cp docs/devops.md release/
                    else
                      echo "docs/devops.md is not available in this branch." > release/devops-notes.md
                    fi

                    if [ -f docs/roadmap.md ]; then
                      cp docs/roadmap.md release/
                    fi

                    if [ -f docs/version.md ]; then
                      cp docs/version.md release/
                    fi

                    if [ -f docs/industrial-bio-printer-simulation.md ]; then
                      cp docs/industrial-bio-printer-simulation.md release/
                    fi

                    if [ -d target ]; then
                      mkdir -p release/smoke
                      cp target/runtime-smoke.log release/smoke/ 2>/dev/null || true
                      cp target/health.json release/smoke/ 2>/dev/null || true
                      cp target/printer-created.json release/smoke/ 2>/dev/null || true
                      cp target/printers-before.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after.json release/smoke/ 2>/dev/null || true
                      cp target/db-tables.txt release/smoke/ 2>/dev/null || true
                      cp target/configured-printers.txt release/smoke/ 2>/dev/null || true
                      cp target/printer-snapshots.txt release/smoke/ 2>/dev/null || true
                      cp target/printer-events.txt release/smoke/ 2>/dev/null || true
                    fi

                    # Future release documents:
                    # docs/quickstart.md will be restored after the 0.1.x runtime architecture stabilizes.
                    # docs/install.md will be restored after runtime packaging is defined.
                    # docs/developer.md will be restored after the internal architecture is stable.
                '''
            }
        }

        stage('Package Release Archive') {
            when {
                expression {
                    return params.RELEASE_VERSION?.trim()
                }
            }
            steps {
                script {
                    def versionName = params.RELEASE_VERSION.trim()
                    env.RELEASE_ARCHIVE = "printer-hub-${versionName}-release.tar.gz"
                }

                sh '''
                    set -eu

                    tar -czf "${RELEASE_ARCHIVE}" release
                    ls -lh "${RELEASE_ARCHIVE}"
                '''
            }
        }

        stage('Publish GitHub Release') {
            when {
                expression {
                    return params.PUBLISH_GITHUB_RELEASE && params.RELEASE_VERSION?.trim()
                }
            }
            steps {
                withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                    sh '''
                        set -eu

                        TAG_NAME="v${RELEASE_VERSION}"
                        RELEASE_NAME="PrinterHub ${RELEASE_VERSION}"

                        API_JSON=$(mktemp)

                        cat > "${API_JSON}" <<EOF
{
  "tag_name": "${TAG_NAME}",
  "name": "${RELEASE_NAME}",
  "draft": false,
  "prerelease": false,
  "generate_release_notes": true
}
EOF

                        curl -sS -X POST \
                          -H "Accept: application/vnd.github+json" \
                          -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                          https://api.github.com/repos/${GITHUB_REPO}/releases \
                          -d @"${API_JSON}" \
                          > github-release-response.json

                        UPLOAD_URL=$(python3 - <<'PY'
import json
with open("github-release-response.json", "r", encoding="utf-8") as f:
    data = json.load(f)
url = data.get("upload_url", "")
print(url.split("{")[0])
PY
)

                        test -n "${UPLOAD_URL}"

                        curl -sS -X POST \
                          -H "Accept: application/vnd.github+json" \
                          -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                          -H "Content-Type: application/gzip" \
                          "${UPLOAD_URL}?name=${RELEASE_ARCHIVE}" \
                          --data-binary @"${RELEASE_ARCHIVE}"
                    '''
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'

            archiveArtifacts artifacts: 'target/surefire-reports/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/operator-message-report.md', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/runtime-smoke.log', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/health.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-before.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/dashboard.html', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard.css', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/dashboard.js', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/db-tables.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/configured-printers.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-snapshots.txt', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-events.txt', allowEmptyArchive: true

            archiveArtifacts artifacts: 'release/**', allowEmptyArchive: true
            archiveArtifacts artifacts: '*.tar.gz', allowEmptyArchive: true
            archiveArtifacts artifacts: 'github-release-response.json', allowEmptyArchive: true
        }

        success {
            echo 'PrinterHub verification completed successfully.'
        }

        failure {
            echo 'Pipeline failed. Check Java version, Maven output, runtime smoke log, release bundle, archived API responses, and SQLite smoke artifacts.'
        }
    }
}