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
                    which curl || true
                    which python3 || true
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

                    start_runtime() {
                      echo "Starting PrinterHub local runtime on port ${API_PORT}"
                      echo "Using database file ${DB_FILE}"

                      mvn -B -ntp exec:java \
                        -Dexec.mainClass="printerhub.Main" \
                        -Dprinterhub.api.port="${API_PORT}" \
                        -Dprinterhub.monitoring.intervalSeconds=1 \
                        -Dprinterhub.databaseFile="${DB_FILE}" \
                        > target/runtime-smoke.log 2>&1 &

                      APP_PID=$!
                      export APP_PID

                      for i in $(seq 1 30); do
                        if curl -fsS "http://localhost:${API_PORT}/health" >/dev/null; then
                          return 0
                        fi
                        sleep 1
                      done

                      echo "Runtime did not become healthy in time"
                      cat target/runtime-smoke.log || true
                      return 1
                    }

                    stop_runtime() {
                      if [ -n "${APP_PID:-}" ]; then
                        kill "${APP_PID}" >/dev/null 2>&1 || true
                        wait "${APP_PID}" >/dev/null 2>&1 || true
                        unset APP_PID
                      fi
                    }

                    cleanup() {
                      stop_runtime
                    }
                    trap cleanup EXIT

                    json_field() {
                      FILE_PATH="$1"
                      FIELD_NAME="$2"
                      python3 - "$FILE_PATH" "$FIELD_NAME" <<'PY'
import json
import sys

path = sys.argv[1]
field = sys.argv[2]

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    value = value[part]

if value is None:
    print("null")
else:
    print(value)
PY
                    }

                    start_runtime

                    curl -fsS "http://localhost:${API_PORT}/health" > target/health.json
                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-initial.json

                    grep -q '"status":"ok"' target/health.json
                    grep -q '"printers":\\[\\]' target/printers-initial.json

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-1",
                        "displayName": "CI Simulated Printer 1",
                        "portName": "SIM_PORT_1",
                        "mode": "simulated",
                        "enabled": true
                      }' > target/printer-created.json

                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-after-create.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-1-after-monitoring.json

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1/status" \
                      > target/printer-1-status-after-monitoring.json

                    grep -q '"printer-1"' target/printer-created.json
                    grep -q '"displayName":"CI Simulated Printer 1"' target/printer-created.json
                    grep -q '"printer-1"' target/printers-after-create.json
                    grep -q '"state":"IDLE"' target/printer-1-after-monitoring.json
                    grep -q '"hotendTemperature":21.80' target/printer-1-after-monitoring.json
                    grep -q '"bedTemperature":21.52' target/printer-1-after-monitoring.json
                    grep -q '"updatedAt"' target/printer-1-after-monitoring.json

                    json_field target/printer-1-after-monitoring.json updatedAt \
                      > target/printer-1-updated-before-disable.txt

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers/printer-1/disable" \
                      > target/printer-disabled.json

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-disable.json

                    grep -q '"enabled":false' target/printer-disabled.json
                    grep -q '"enabled":false' target/printer-after-disable.json
                    grep -q '"state":"DISCONNECTED"' target/printer-after-disable.json

                    json_field target/printer-after-disable.json updatedAt \
                      > target/printer-1-disabled-updated-before.txt

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-disable-wait.json

                    json_field target/printer-after-disable-wait.json updatedAt \
                      > target/printer-1-disabled-updated-after.txt

                    cmp -s target/printer-1-disabled-updated-before.txt target/printer-1-disabled-updated-after.txt

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers/printer-1/enable" \
                      > target/printer-enabled.json

                    grep -q '"enabled":true' target/printer-enabled.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-enable.json

                    grep -q '"enabled":true' target/printer-after-enable.json
                    grep -q '"state":"IDLE"' target/printer-after-enable.json

                    json_field target/printer-enabled.json updatedAt \
                      > target/printer-1-enabled-updated-before.txt || true

                    json_field target/printer-after-enable.json updatedAt \
                      > target/printer-1-enabled-updated-after.txt

                    if cmp -s target/printer-1-disabled-updated-after.txt target/printer-1-enabled-updated-after.txt; then
                      echo "updatedAt did not change after enable"
                      exit 1
                    fi

                    curl -fsS -X PUT "http://localhost:${API_PORT}/printers/printer-1" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "displayName": "CI Simulated Printer Updated",
                        "portName": "SIM_PORT_2",
                        "mode": "sim-error",
                        "enabled": true
                      }' > target/printer-updated.json

                    curl -fsS "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-after-update.json

                    grep -q '"displayName":"CI Simulated Printer Updated"' target/printer-updated.json
                    grep -q '"portName":"SIM_PORT_2"' target/printer-updated.json
                    grep -q '"mode":"sim-error"' target/printer-updated.json
                    grep -q '"displayName":"CI Simulated Printer Updated"' target/printer-after-update.json
                    grep -q '"portName":"SIM_PORT_2"' target/printer-after-update.json
                    grep -q '"mode":"sim-error"' target/printer-after-update.json

                    curl -fsS -X POST "http://localhost:${API_PORT}/printers" \
                      -H "Content-Type: application/json" \
                      -d '{
                        "id": "printer-2",
                        "displayName": "CI Persistent Printer",
                        "portName": "SIM_PORT_PERSIST",
                        "mode": "simulated",
                        "enabled": true
                      }' > target/printer-2-created.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers" \
                      > target/printers-before-delete.json

                    grep -q '"printer-2"' target/printers-before-delete.json

                    curl -fsS -X DELETE "http://localhost:${API_PORT}/printers/printer-1" \
                      > target/printer-deleted.json

                    curl -fsS "http://localhost:${API_PORT}/printers" \
                      > target/printers-after-delete.json

                    grep -q '"deleted":"printer-1"' target/printer-deleted.json
                    if grep -q '"printer-1"' target/printers-after-delete.json; then
                      echo "printer-1 still present after delete"
                      exit 1
                    fi
                    grep -q '"printer-2"' target/printers-after-delete.json

                    curl -fsS "http://localhost:${API_PORT}/dashboard" > target/dashboard.html
                    curl -fsS "http://localhost:${API_PORT}/dashboard/dashboard.css" > target/dashboard.css
                    curl -fsS "http://localhost:${API_PORT}/dashboard/dashboard.js" > target/dashboard.js

                    grep -q 'PrinterHub Dashboard' target/dashboard.html
                    grep -q 'printer-grid' target/dashboard.css
                    grep -q 'loadDashboard' target/dashboard.js

                    stop_runtime

                    sqlite3 "${DB_FILE}" '.tables' > target/db-tables.txt
                    sqlite3 "${DB_FILE}" 'select id,name,port_name,mode,enabled from configured_printers order by id;' \
                      > target/configured-printers.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,state,created_at from printer_snapshots order by id desc limit 20;' \
                      > target/printer-snapshots.txt
                    sqlite3 "${DB_FILE}" 'select printer_id,event_type,message,created_at from printer_events order by id desc limit 20;' \
                      > target/printer-events.txt

                    grep -q 'configured_printers' target/db-tables.txt
                    grep -q 'printer_snapshots' target/db-tables.txt
                    grep -q 'printer_events' target/db-tables.txt

                    grep -q 'printer-2' target/configured-printers.txt
                    grep -q 'printer-2' target/printer-snapshots.txt

                    start_runtime

                    curl -fsS "http://localhost:${API_PORT}/printers" \
                      > target/printers-after-restart.json

                    grep -q '"printer-2"' target/printers-after-restart.json
                    grep -q '"CI Persistent Printer"' target/printers-after-restart.json

                    stop_runtime
                    trap - EXIT

                    echo "Health:"
                    cat target/health.json

                    echo
                    echo "Initial printers:"
                    cat target/printers-initial.json

                    echo
                    echo "Created printer-1:"
                    cat target/printer-created.json

                    echo
                    echo "Printer-1 after monitoring:"
                    cat target/printer-1-after-monitoring.json

                    echo
                    echo "Printer-1 after disable:"
                    cat target/printer-after-disable.json

                    echo
                    echo "Printer-1 after enable:"
                    cat target/printer-after-enable.json

                    echo
                    echo "Printer-1 after update:"
                    cat target/printer-after-update.json

                    echo
                    echo "Printers after delete:"
                    cat target/printers-after-delete.json

                    echo
                    echo "Printers after restart:"
                    cat target/printers-after-restart.json

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
                      cp target/printers-initial.json release/smoke/ 2>/dev/null || true
                      cp target/printer-created.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after-create.json release/smoke/ 2>/dev/null || true
                      cp target/printer-1-after-monitoring.json release/smoke/ 2>/dev/null || true
                      cp target/printer-1-status-after-monitoring.json release/smoke/ 2>/dev/null || true
                      cp target/printer-disabled.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-disable.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-disable-wait.json release/smoke/ 2>/dev/null || true
                      cp target/printer-enabled.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-enable.json release/smoke/ 2>/dev/null || true
                      cp target/printer-updated.json release/smoke/ 2>/dev/null || true
                      cp target/printer-after-update.json release/smoke/ 2>/dev/null || true
                      cp target/printer-2-created.json release/smoke/ 2>/dev/null || true
                      cp target/printer-deleted.json release/smoke/ 2>/dev/null || true
                      cp target/printers-before-delete.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after-delete.json release/smoke/ 2>/dev/null || true
                      cp target/printers-after-restart.json release/smoke/ 2>/dev/null || true
                      cp target/dashboard.html release/smoke/ 2>/dev/null || true
                      cp target/dashboard.css release/smoke/ 2>/dev/null || true
                      cp target/dashboard.js release/smoke/ 2>/dev/null || true
                      cp target/db-tables.txt release/smoke/ 2>/dev/null || true
                      cp target/configured-printers.txt release/smoke/ 2>/dev/null || true
                      cp target/printer-snapshots.txt release/smoke/ 2>/dev/null || true
                      cp target/printer-events.txt release/smoke/ 2>/dev/null || true
                    fi
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
            archiveArtifacts artifacts: 'target/printers-initial.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after-create.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-1-after-monitoring.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-1-status-after-monitoring.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/printer-disabled.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-disable.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-disable-wait.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-enabled.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-enable.json', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/printer-updated.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-after-update.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-2-created.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printer-deleted.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-before-delete.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after-delete.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after-restart.json', allowEmptyArchive: true

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
            echo 'Pipeline failed. Check Java version, Maven output, runtime smoke log, archived API responses, and SQLite smoke artifacts.'
        }
    }
}