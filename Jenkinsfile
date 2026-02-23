/*
 * Dynamic Jenkins pipeline for a monorepo microservices setup.
 * Add a new service by appending one line in getServiceMatrix().
 */

def getServiceMatrix() {
    return [
        [name: 'api-gateway',    dir: 'api-gateway',   flag: 'BUILD_API_GATEWAY',  image: 'api-gateway'],
        [name: 'user-service',   dir: 'user-service',  flag: 'BUILD_USER_SERVICE', image: 'user-service'],
        [name: 'product-service',dir: 'product-service', flag: 'BUILD_PRODUCT',    image: 'product-service'],
        [name: 'inventory-service', dir: 'inventory-service', flag: 'BUILD_INVENTORY', image: 'inventory-service'],
        [name: 'payment-service', dir: 'payment-service', flag: 'BUILD_PAYMENT', image: 'payment-service'],
        [name: 'order-service',  dir: 'order-service', flag: 'BUILD_ORDER',        image: 'order-service'],
        [name: 'frontend',       dir: 'front-end',     flag: 'BUILD_FRONTEND',     image: 'frontend']
    ]
}

def getEnvFlag(String name) {
    def value = env.getProperty(name)
    return value ? value : 'false'
}

def setEnvFlag(String name, String value) {
    env.setProperty(name, value)
}

def buildServices() {
    return getServiceMatrix().findAll { service -> getEnvFlag(service.flag) == 'true' }
}

def anyServiceBuildEnabled() {
    return getServiceMatrix().any { service -> getEnvFlag(service.flag) == 'true' }
}

def imageRef(Map service) {
    return "${env.DOCKERHUB_USER}/${env.PROJECT}-${service.image}:${env.IMAGE_TAG}"
}

def runServiceTest(Map service) {
    switch (service.name) {
        case 'api-gateway':
        case 'product-service':
        case 'payment-service':
        case 'order-service':
            sh """
              docker run --rm \\
                -v "\$PWD/${service.dir}:/app" \\
                -w /app \\
                maven:3.9.6-eclipse-temurin-17 \\
                mvn -B test
            """
            break

        case 'user-service':
        case 'inventory-service':
            sh """
              docker run --rm \\
                -v "\$PWD/${service.dir}:/app" \\
                -w /app \\
                golang:1.24-alpine \\
                sh -c "go test ./..."
            """
            break

        case 'frontend':
            sh """
              docker run --rm \\
                -v "\$PWD/${service.dir}:/app" \\
                -w /app \\
                node:20-alpine \\
                sh -c "npm ci && npm run build"
            """
            break

        default:
            error "No test command configured for service: ${service.name}"
    }
}

def runParallelForSelected(String stagePrefix, Closure worker) {
    def tasks = [:]

    buildServices().each { svc ->
        def service = svc
        tasks["${stagePrefix} ${service.name}"] = {
            worker(service)
        }
    }

    if (tasks.isEmpty()) {
        echo "No services selected for ${stagePrefix.toLowerCase()}."
        return
    }

    parallel tasks
}

def updateGitOpsImageTag(Map service) {
    sh """
      yq e -i '.images[] |= (select(.name == "${env.DOCKERHUB_USER}/${env.PROJECT}-${service.image}").newTag = "${env.IMAGE_TAG}")' \\
      overlays/dev/kustomization.yaml
    """
}

def cleanupBuiltImages() {
    def images = buildServices().collect { service -> imageRef(service) }

    if (images.isEmpty()) {
        echo 'No built images to clean up.'
        return
    }

    images.each { img ->
        sh "docker image rm -f ${img} || true"
    }

    // Remove dangling layers to keep Jenkins workers clean.
    sh 'docker image prune -f || true'
    sh 'rm -rf .trivy-cache || true'
}

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

        TRIVY_IMAGE          = "aquasec/trivy:0.57.1"
        TRIVY_SEVERITY       = "HIGH,CRITICAL"
        TRIVY_EXIT_CODE      = "1"
        TRIVY_IGNORE_UNFIXED = "true"
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
                    def changedFilesRaw = sh(
                        script: 'git diff --name-only HEAD~1 HEAD || true',
                        returnStdout: true
                    ).trim()

                    def changedFiles = changedFilesRaw ? changedFilesRaw.split('\n') : []
                    def isCiChange = changedFiles.any { it == 'Jenkinsfile' }

                    echo 'Changed files:'
                    changedFiles.each { echo " - ${it}" }

                    // Set build flags dynamically from the service matrix.
                    getServiceMatrix().each { service ->
                        def shouldBuild = changedFiles.any { it.startsWith("${service.dir}/") } ? 'true' : 'false'
                        setEnvFlag(service.flag, shouldBuild)
                    }

                    setEnvFlag('BUILD_CONTRACTS', changedFiles.any { it.startsWith('api-contracts/') } ? 'true' : 'false')

                    // If CI config changed, force rebuild/retest all services.
                    if (isCiChange) {
                        echo 'Jenkinsfile changed -> rebuild all services'
                        getServiceMatrix().each { service ->
                            setEnvFlag(service.flag, 'true')
                        }
                        setEnvFlag('BUILD_CONTRACTS', 'true')
                    }

                    echo '================= CHANGE SUMMARY ================='
                    echo "Jenkinsfile   : ${isCiChange}"
                    getServiceMatrix().each { service ->
                        echo String.format('%-18s : %s', service.name, getEnvFlag(service.flag))
                    }
                    echo "contracts    : ${getEnvFlag('BUILD_CONTRACTS')}"
                    echo '=================================================='
                }
            }
        }

        stage('Contract Validation') {
            when {
                expression {
                    env.BUILD_CONTRACTS == 'true' || anyServiceBuildEnabled()
                }
            }
            steps {
                sh '''
                  set -e

                  if [ ! -d "api-contracts" ]; then
                    echo "api-contracts directory not found"
                    exit 1
                  fi

                  for spec in api-contracts/*.openapi.yaml; do
                    [ -f "$spec" ] || continue
                    echo "Validating contract: $spec"
                    docker run --rm \
                      -v "$PWD:/work" \
                      -w /work \
                      node:20-alpine \
                      sh -c "npx --yes @apidevtools/swagger-cli@4.0.4 validate $spec"
                  done
                '''
            }
        }

        /* =========================
           TEST CHANGED SERVICES
        ========================= */
        stage('Test Services') {
            when {
                expression { anyServiceBuildEnabled() }
            }
            steps {
                script {
                    runParallelForSelected('Test') { service ->
                        runServiceTest(service)
                    }
                }
            }
        }

        /* =========================
           BUILD IMAGES
        ========================= */
        stage('Build Images') {
            when {
                expression { anyServiceBuildEnabled() }
            }
            steps {
                script {
                    runParallelForSelected('Build') { service ->
                        sh "docker build -t ${imageRef(service)} ./${service.dir}"
                    }
                }
            }
        }

        /* =========================
           SECURITY SCAN (TRIVY)
        ========================= */
        stage('Security Scan (Trivy)') {
            when {
                expression { anyServiceBuildEnabled() }
            }
            stages {
                stage('Update Trivy DB') {
                    steps {
                        sh '''
                          mkdir -p .trivy-cache
                          docker run --rm \
                            -v "$PWD/.trivy-cache:/root/.cache/" \
                            ${TRIVY_IMAGE} image --download-db-only --no-progress
                        '''
                    }
                }

                stage('Scan Images') {
                    steps {
                        script {
                            runParallelForSelected('Scan') { service ->
                                sh """
                                  docker run --rm \
                                    -v /var/run/docker.sock:/var/run/docker.sock \
                                    -v "\$PWD/.trivy-cache:/root/.cache/" \
                                    ${env.TRIVY_IMAGE} image \
                                    --no-progress \
                                    --skip-db-update \
                                    --scanners vuln \
                                    --severity ${env.TRIVY_SEVERITY} \
                                    --exit-code ${env.TRIVY_EXIT_CODE} \
                                    --ignore-unfixed=${env.TRIVY_IGNORE_UNFIXED} \
                                    ${imageRef(service)}
                                """
                            }
                        }
                    }
                }
            }
        }

        /* =========================
           DOCKER LOGIN
        ========================= */
        stage('Docker Login') {
            when {
                expression { anyServiceBuildEnabled() }
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
            when {
                expression { anyServiceBuildEnabled() }
            }
            steps {
                script {
                    runParallelForSelected('Push') { service ->
                        sh "docker push ${imageRef(service)}"
                    }
                }
            }
        }

        /* =========================
           UPDATE GITOPS REPO
        ========================= */
        stage('Update GitOps Repo') {
            when {
                expression { anyServiceBuildEnabled() }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'github-token',
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )
                ]) {
                    script {
                        sh '''
                          rm -rf "${GITOPS_DIR}"
                          REPO_NO_PROTO="${GITOPS_REPO#https://}"
                          git clone "https://${GIT_USER}:${GIT_TOKEN}@${REPO_NO_PROTO}" "${GITOPS_DIR}"
                        '''

                        dir(env.GITOPS_DIR) {
                            sh '''
                              git config user.name "jenkins-ci"
                              git config user.email "jenkins@ci.local"
                            '''

                            buildServices().each { service ->
                                updateGitOpsImageTag(service)
                            }

                            sh '''
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
        }
    }

    post {
        always {
            sh 'docker logout || true'
        }
        success {
            script {
                cleanupBuiltImages()
            }
            echo 'CI + GitOps completed. Argo CD will sync automatically.'
        }
    }
}
