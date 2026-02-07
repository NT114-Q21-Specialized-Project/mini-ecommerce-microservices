pipeline {
    agent any

    options {
        timestamps()
        ansiColor('xterm')
    }

    environment {
        DOCKERHUB_USER = "tienphatng237"
        PROJECT        = "mini-ecommerce"
        IMAGE_TAG      = "${env.GIT_COMMIT.take(7)}"

        GITOPS_REPO = "https://github.com/NT114-Q21-Specialized-Project/kubernetes-hub.git"
        GITOPS_DIR  = "kubernetes-hub"
    }

    stages {

        /* =========================
           CHECKOUT APP REPO
        ========================= */
        stage('Checkout App Repo') {
            steps {
                checkout scm
            }
        }

        /* =========================
           DETECT CHANGED SERVICES
        ========================= */
        stage('Detect Changed Services') {
            steps {
                script {

                    // Get list of changed files between the last two commits
                    def changedFilesRaw = sh(
                        script: "git diff --name-only HEAD~1 HEAD || true",
                        returnStdout: true
                    ).trim()

                    def changedFiles = changedFilesRaw
                        ? changedFilesRaw.split("\n")
                        : []

                    echo "Changed files:"
                    changedFiles.each { echo " - ${it}" }

                    // Detect Jenkinsfile changes (CI configuration changes)
                    def isCiChange = changedFiles.any { it == "Jenkinsfile" }

                    // Detect changes per service directory
                    env.BUILD_API_GATEWAY  = changedFiles.any { it.startsWith("api-gateway/") }  ? "true" : "false"
                    env.BUILD_USER_SERVICE = changedFiles.any { it.startsWith("user-service/") } ? "true" : "false"
                    env.BUILD_PRODUCT      = changedFiles.any { it.startsWith("product-service/") } ? "true" : "false"
                    env.BUILD_ORDER        = changedFiles.any { it.startsWith("order-service/") } ? "true" : "false"
                    env.BUILD_FRONTEND     = changedFiles.any { it.startsWith("front-end/") } ? "true" : "false"

                    // If Jenkinsfile is changed → force rebuild ALL services
                    if (isCiChange) {
                        echo "⚠️ Jenkinsfile changed → rebuild ALL services"
                        env.BUILD_API_GATEWAY  = "true"
                        env.BUILD_USER_SERVICE = "true"
                        env.BUILD_PRODUCT      = "true"
                        env.BUILD_ORDER        = "true"
                        env.BUILD_FRONTEND     = "true"
                    }

                    echo """
        ================= CHANGE SUMMARY =================
        Jenkinsfile   : ${isCiChange}
        api-gateway   : ${env.BUILD_API_GATEWAY}
        user-service  : ${env.BUILD_USER_SERVICE}
        product       : ${env.BUILD_PRODUCT}
        order         : ${env.BUILD_ORDER}
        front-end     : ${env.BUILD_FRONTEND}
        =================================================
        """
                }
            }
        }


        /* =========================
           BUILD IMAGES
        ========================= */
        stage('Build Images') {
            parallel {

                stage('Build api-gateway') {
                    when { environment name: 'BUILD_API_GATEWAY', value: 'true' }
                    steps {
                        sh "docker build -t ${DOCKERHUB_USER}/${PROJECT}-api-gateway:${IMAGE_TAG} ./api-gateway"
                    }
                }

                stage('Build user-service') {
                    when { environment name: 'BUILD_USER_SERVICE', value: 'true' }
                    steps {
                        sh "docker build -t ${DOCKERHUB_USER}/${PROJECT}-user-service:${IMAGE_TAG} ./user-service"
                    }
                }

                stage('Build product-service') {
                    when { environment name: 'BUILD_PRODUCT', value: 'true' }
                    steps {
                        sh "docker build -t ${DOCKERHUB_USER}/${PROJECT}-product-service:${IMAGE_TAG} ./product-service"
                    }
                }

                stage('Build order-service') {
                    when { environment name: 'BUILD_ORDER', value: 'true' }
                    steps {
                        sh "docker build -t ${DOCKERHUB_USER}/${PROJECT}-order-service:${IMAGE_TAG} ./order-service"
                    }
                }

                stage('Build frontend') {
                    when { environment name: 'BUILD_FRONTEND', value: 'true' }
                    steps {
                        sh "docker build -t ${DOCKERHUB_USER}/${PROJECT}-frontend:${IMAGE_TAG} ./front-end"
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
                    env.BUILD_API_GATEWAY  == "true" ||
                    env.BUILD_USER_SERVICE == "true" ||
                    env.BUILD_PRODUCT      == "true" ||
                    env.BUILD_ORDER        == "true" ||
                    env.BUILD_FRONTEND     == "true"
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
           PUSH IMAGES
        ========================= */
        stage('Push Images') {
            parallel {

                stage('Push api-gateway') {
                    when { environment name: 'BUILD_API_GATEWAY', value: 'true' }
                    steps {
                        sh """
                          docker push ${DOCKERHUB_USER}/${PROJECT}-api-gateway:${IMAGE_TAG}
                        """
                    }
                }

                stage('Push user-service') {
                    when { environment name: 'BUILD_USER_SERVICE', value: 'true' }
                    steps {
                        sh "docker push ${DOCKERHUB_USER}/${PROJECT}-user-service:${IMAGE_TAG}"
                    }
                }

                stage('Push product-service') {
                    when { environment name: 'BUILD_PRODUCT', value: 'true' }
                    steps {
                        sh "docker push ${DOCKERHUB_USER}/${PROJECT}-product-service:${IMAGE_TAG}"
                    }
                }

                stage('Push order-service') {
                    when { environment name: 'BUILD_ORDER', value: 'true' }
                    steps {
                        sh "docker push ${DOCKERHUB_USER}/${PROJECT}-order-service:${IMAGE_TAG}"
                    }
                }

                stage('Push frontend') {
                    when { environment name: 'BUILD_FRONTEND', value: 'true' }
                    steps {
                        sh "docker push ${DOCKERHUB_USER}/${PROJECT}-frontend:${IMAGE_TAG}"
                    }
                }
            }
        }

        /* =========================
           UPDATE GITOPS REPO
        ========================= */
        stage('Update GitOps Repo') {
            when {
                expression {
                    env.BUILD_API_GATEWAY  == "true" ||
                    env.BUILD_USER_SERVICE == "true" ||
                    env.BUILD_PRODUCT      == "true" ||
                    env.BUILD_ORDER        == "true" ||
                    env.BUILD_FRONTEND     == "true"
                }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'gitops-cred',
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )
                ]) {
                    sh '''
                    rm -rf ${GITOPS_DIR}
                    git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/NT114-Q21-Specialized-Project/kubernetes-hub.git
                    cd ${GITOPS_DIR}

                    update_image () {
                      SERVICE=$1
                      yq e -i '.images[] |=
                        (select(.name == "tienphatng237/mini-ecommerce-'$SERVICE'").newTag = "'$IMAGE_TAG'")' \
                        overlays/dev/kustomization.yaml
                    }

                    [ "$BUILD_API_GATEWAY" = "true" ]  && update_image api-gateway
                    [ "$BUILD_USER_SERVICE" = "true" ] && update_image user-service
                    [ "$BUILD_PRODUCT" = "true" ]      && update_image product-service
                    [ "$BUILD_ORDER" = "true" ]        && update_image order-service
                    [ "$BUILD_FRONTEND" = "true" ]     && update_image frontend

                    if git diff --quiet; then
                      echo "No GitOps changes"
                      exit 0
                    fi

                    git commit -am "gitops(dev): update image tags to ${IMAGE_TAG}"
                    git push origin main
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }
        success {
            echo "✅ CI + GitOps completed. Argo CD will sync automatically."
        }
    }
}
