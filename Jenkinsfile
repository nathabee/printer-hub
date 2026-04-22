pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'mvn clean verify'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
        }

        success {
            echo 'CI verification completed successfully.'
        }

        failure {
            echo 'CI verification failed. Check Maven output and test reports.'
        }
    }
}