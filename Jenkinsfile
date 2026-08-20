pipeline {
    agent any
    tools {
        maven 'maven-3.8.4'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Image') {
            steps {
                sh 'mvn -B clean package -DskipTests'
                sh 'docker build -t tns-capital-skeleton:latest .'
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm tns-capital-skeleton:latest'
            }
        }
    }
}
