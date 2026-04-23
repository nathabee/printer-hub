pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
    }

    environment {
        MAVEN_OPTS = '-Djava.awt.headless=true'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Environment') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
            }
        }

        stage('Verify') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('Simulated Smoke Run') {
            steps {
                sh 'mvn exec:java -Dexec.mainClass="printerhub.Main" -Dexec.args="SIM_PORT M105 3 100 sim"'
            }
        }

        stage('Prepare Release Bundle') {
            steps {
                sh '''
                    rm -rf release
                    mkdir -p release

                    if ls target/*.jar >/dev/null 2>&1; then
                      cp target/*.jar release/ || true
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

                    if [ -f test.md ]; then
                      cp test.md release/
                    fi
                '''
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
        }

        success {
            echo 'CI verification, simulated smoke run, and release preparation completed successfully.'
        }

        failure {
            echo 'Pipeline failed. Check Maven output, smoke-run logs, and archived reports.'
        }
    }
}