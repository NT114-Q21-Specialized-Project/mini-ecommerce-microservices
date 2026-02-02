pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
    }

    environment {
        DOCKERHUB_USER = "tienphatng237"
        IMAGE_TAG = "${env.GIT_COMMIT.take(7)}"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Detect Changed Services') {
            steps {
                script {
                    def changedFiles = sh(
                        script: "git diff --name-only HEAD~1 HEAD || true",
                        returnStdout: true
                    ).trim().split("\n")

                    env.BUILD_API_GATEWAY   = changedFiles.any { it.startsWith("api-gateway/") } ? "true" : "false"
                    env.BUILD_USER_SERVICE  = changedFiles.any { it.startsWith("user-service/") } ? "true" : "false"
                    env.BUILD_PRODUCT       = changedFiles.any { it.startsWith("product-service/") } ? "true" : "false"
                    env.BUILD_ORDER         = changedFiles.any { it.startsWith("order-service/") } ? "true" : "false"
                    env.BUILD_FRONTEND      = changedFiles.any { it.startsWith("front-end/") } ? "true" : "false"

                    echo """
                    Change summary:
                      api-gateway  : ${env.BUILD_API_GATEWAY}
                      user-service : ${env.BUILD_USER_SERVICE}
                      product      : ${env.BUILD_PRODUCT}
                      order        : ${env.BUILD_ORDER}
                      front-end    : ${env.BUILD_FRONTEND}
                    """
                }
            }
        }

        /* =========================
           BUILD STAGE
        ========================= */
        stage('Build Images') {
            parallel {

                stage('Build api-gateway') {
                    when { environment name: 'BUILD_API_GATEWAY', value: 'true' }
                    steps {
                        sh 'docker build -t $DOCKERHUB_USER/api-gateway:$IMAGE_TAG ./api-gateway'
                    }
                }

                stage('Build user-service') {
                    when { environment name: 'BUILD_USER_SERVICE', value: 'true' }
                    steps {
                        sh 'docker build -t $DOCKERHUB_USER/user-service:$IMAGE_TAG ./user-service'
                    }
                }

                stage('Build product-service') {
                    when { environment name: 'BUILD_PRODUCT', value: 'true' }
                    steps {
                        sh 'docker build -t $DOCKERHUB_USER/product-service:$IMAGE_TAG ./product-service'
                    }
                }

                stage('Build order-service') {
                    when { environment name: 'BUILD_ORDER', value: 'true' }
                    steps {
                        sh 'docker build -t $DOCKERHUB_USER/order-service:$IMAGE_TAG ./order-service'
                    }
                }

                stage('Build front-end') {
                    when { environment name: 'BUILD_FRONTEND', value: 'true' }
                    steps {
                        sh 'docker build -t $DOCKERHUB_USER/front-end:$IMAGE_TAG ./front-end'
                    }
                }
            }
        }

        /* =========================
           DOCKER LOGIN
        ========================= */
        stage('Docker Login') {
            when {
                expression {
                    env.BUILD_API_GATEWAY == "true" ||
                    env.BUILD_USER_SERVICE == "true" ||
                    env.BUILD_PRODUCT == "true" ||
                    env.BUILD_ORDER == "true" ||
                    env.BUILD_FRONTEND == "true"
                }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-cred',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                }
            }
        }

        /* =========================
           PUSH STAGE
        ========================= */
        stage('Push Images') {
            parallel {

                stage('Push api-gateway') {
                    when { environment name: 'BUILD_API_GATEWAY', value: 'true' }
                    steps {
                        sh '''
                          docker tag  $DOCKERHUB_USER/api-gateway:$IMAGE_TAG $DOCKERHUB_USER/api-gateway:latest
                          docker push $DOCKERHUB_USER/api-gateway:$IMAGE_TAG
                          docker push $DOCKERHUB_USER/api-gateway:latest
                        '''
                    }
                }

                stage('Push user-service') {
                    when { environment name: 'BUILD_USER_SERVICE', value: 'true' }
                    steps {
                        sh '''
                          docker tag  $DOCKERHUB_USER/user-service:$IMAGE_TAG $DOCKERHUB_USER/user-service:latest
                          docker push $DOCKERHUB_USER/user-service:$IMAGE_TAG
                          docker push $DOCKERHUB_USER/user-service:latest
                        '''
                    }
                }

                stage('Push product-service') {
                    when { environment name: 'BUILD_PRODUCT', value: 'true' }
                    steps {
                        sh '''
                          docker tag  $DOCKERHUB_USER/product-service:$IMAGE_TAG $DOCKERHUB_USER/product-service:latest
                          docker push $DOCKERHUB_USER/product-service:$IMAGE_TAG
                          docker push $DOCKERHUB_USER/product-service:latest
                        '''
                    }
                }

                stage('Push order-service') {
                    when { environment name: 'BUILD_ORDER', value: 'true' }
                    steps {
                        sh '''
                          docker tag  $DOCKERHUB_USER/order-service:$IMAGE_TAG $DOCKERHUB_USER/order-service:latest
                          docker push $DOCKERHUB_USER/order-service:$IMAGE_TAG
                          docker push $DOCKERHUB_USER/order-service:latest
                        '''
                    }
                }

                stage('Push front-end') {
                    when { environment name: 'BUILD_FRONTEND', value: 'true' }
                    steps {
                        sh '''
                          docker tag  $DOCKERHUB_USER/front-end:$IMAGE_TAG $DOCKERHUB_USER/front-end:latest
                          docker push $DOCKERHUB_USER/front-end:$IMAGE_TAG
                          docker push $DOCKERHUB_USER/front-end:latest
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }
        success {
            echo "✅ Build & Push pipeline completed successfully"
        }
    }
}
