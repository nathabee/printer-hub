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
                    rm -f printerhub.db printerhub-real.db printerhub-test.db

                    mvn -B -ntp clean verify
                '''
            }
        }

        stage('Local Runtime Smoke Test') {
            steps {
                sh '''
                    set -eu
 
                    API_PORT="${API_SMOKE_PORT:-18090}"

                    echo "Starting PrinterHub local runtime on port ${API_PORT}"
 

                    mvn -B -ntp exec:java \
                        -Dexec.mainClass="printerhub.Main" \
                        -Dprinterhub.api.port="${API_PORT}" \
                        -Dprinterhub.monitoring.intervalSeconds=1 \
                        > target/runtime-smoke.log 2>&1 &


                    APP_PID=$!

                    cleanup() {
                      kill "${APP_PID}" >/dev/null 2>&1 || true
                    }
                    trap cleanup EXIT

                    for i in $(seq 1 20); do
                      if curl -fsS "http://localhost:${API_PORT}/health" >/dev/null; then
                        break
                      fi
                      sleep 1
                    done

                    curl -fsS "http://localhost:${API_PORT}/health" > target/health.json
                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-before.json

                    sleep 3

                    curl -fsS "http://localhost:${API_PORT}/printers" > target/printers-after.json

                    echo "Health:"
                    cat target/health.json

                    echo
                    echo "Printers before:"
                    cat target/printers-before.json

                    echo
                    echo "Printers after:"
                    cat target/printers-after.json

                    grep -q '"status":"ok"' target/health.json

                    grep -q '"printer-1"' target/printers-after.json
                    grep -q '"printer-2"' target/printers-after.json
                    grep -q '"printer-3"' target/printers-after.json
                    grep -q '"updatedAt"' target/printers-after.json

                    test "$(cat target/printers-before.json)" != "$(cat target/printers-after.json)"

                    echo
                    echo "Runtime smoke log:"
                    cat target/runtime-smoke.log
                '''
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'

            archiveArtifacts artifacts: 'target/surefire-reports/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true

            archiveArtifacts artifacts: 'target/runtime-smoke.log', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/health.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-before.json', allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/printers-after.json', allowEmptyArchive: true
        }

        success {
            echo 'PrinterHub local runtime verification completed successfully.'
        }

        failure {
            echo 'Pipeline failed. Check Java version, Maven output, runtime smoke log, and archived API responses.'
        }
    }
}