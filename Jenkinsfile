pipeline {
    agent any

    parameters {
        string(
            name: 'JAVA_HOME_OVERRIDE',
            defaultValue: '',
            description: 'Optional JAVA_HOME override. Leave empty to use the agent default.'
        )
        string(
            name: 'RELEASE_VERSION',
            defaultValue: '',
            description: 'Optional release version, for example 0.0.7. Leave empty for CI-only runs.'
        )
        booleanParam(
            name: 'PUBLISH_GITHUB_RELEASE',
            defaultValue: false,
            description: 'Publish the prepared release bundle to GitHub Releases.'
        )
        string(
            name: 'API_SMOKE_PORT',
            defaultValue: '18090',
            description: 'Port used for the simulated API smoke test.'
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
                checkout scm
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

                sh 'echo "JAVA_HOME=${JAVA_HOME:-<not-set>}"'
                sh 'which java || true'
                sh 'which javac || true'
                sh 'java -version'
                sh 'javac -version'
                sh 'mvn -version'
            }
        }

        stage('Verify') {
            steps {
                sh 'mvn -B -ntp clean verify'
            }
        }

        stage('Simulated Smoke Run') {
            steps {
                sh 'mvn -B -ntp exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"'
            }
        }

        stage('API Simulated Smoke Run') {
            steps {
                sh '''
                    set -eu

                    API_PORT="${API_SMOKE_PORT}"
                    JAR_FILE=$(ls target/*-all.jar | head -n 1)

                    echo "Using jar: ${JAR_FILE}"
                    echo "Starting API smoke server on port ${API_PORT}"

                    java -jar "${JAR_FILE}" api SIM_PORT sim "${API_PORT}" > target/api-smoke.log 2>&1 &
                    API_PID=$!

                    cleanup() {
                    kill "${API_PID}" >/dev/null 2>&1 || true
                    }
                    trap cleanup EXIT

                    for i in $(seq 1 20); do
                    if curl -fsS "http://localhost:${API_PORT}/health" >/dev/null; then
                        break
                    fi
                    sleep 1
                    done

                    curl -fsS "http://localhost:${API_PORT}/health"
                    curl -fsS "http://localhost:${API_PORT}/printer/status" > target/api-status-before.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printer/status" > target/api-status-after.json

                    echo "Status before:"
                    cat target/api-status-before.json

                    echo
                    echo "Status after:"
                    cat target/api-status-after.json

                    grep -q '"state"' target/api-status-after.json
                    grep -q '"updatedAt"' target/api-status-after.json

                    BEFORE_UPDATED_AT=$(grep '"updatedAt"' target/api-status-before.json)
                    AFTER_UPDATED_AT=$(grep '"updatedAt"' target/api-status-after.json)

                    echo "Before updatedAt: ${BEFORE_UPDATED_AT}"
                    echo "After updatedAt:  ${AFTER_UPDATED_AT}"

                    test "${BEFORE_UPDATED_AT}" != "${AFTER_UPDATED_AT}"

                    echo
                    echo "API smoke log:"
                    cat target/api-smoke.log
                '''
            }
        }

        stage('Prepare Release Bundle') {
            steps {
                sh '''
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
                    fi

                    if [ -f docs/roadmap.md ]; then
                      cp docs/roadmap.md release/
                    fi

                    if [ -f docs/version.md ]; then
                      cp docs/version.md release/
                    fi

                    if [ -f docs/quickstart.md ]; then
                      cp docs/quickstart.md release/
                    fi

                    if [ -f docs/industrial-bio-printer-simulation.md ]; then
                      cp docs/industrial-bio-printer-simulation.md release/
                    fi

                '''
            }
        }

        stage('Package Release Archive') {
            steps {
                script {
                    def versionName = params.RELEASE_VERSION?.trim() ? params.RELEASE_VERSION.trim() : 'snapshot'
                    env.RELEASE_ARCHIVE = "printer-hub-${versionName}-release.tar.gz"
                }

                sh '''
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
            archiveArtifacts artifacts: 'release/**', allowEmptyArchive: true
            archiveArtifacts artifacts: '*.tar.gz', allowEmptyArchive: true
            archiveArtifacts artifacts: 'github-release-response.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/api-smoke.log', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/api-status-before.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/api-status-after.json', allowEmptyArchive: true
        }

        success {
            echo 'CI verification, smoke validation, release packaging, and optional GitHub release publication completed successfully.'
        }

        failure {
            echo 'Pipeline failed. Check Java version, Maven output, smoke-run logs, release bundle, and archived reports.'
        }
    }
}