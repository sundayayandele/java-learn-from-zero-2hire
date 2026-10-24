## What you will be able to do

- Read and write a **declarative Jenkins pipeline** (`Jenkinsfile`): agents, stages, steps, `post` blocks, credentials, parallel stages and input approvals.
- Explain how enterprise Jenkins is organised: controllers and agents, shared libraries, multibranch pipelines.
- Read and write an **Azure Pipelines** YAML file: triggers, stages, jobs, tasks, variable groups, service connections and environments with approvals.
- Map concepts between GitHub Actions, GitLab CI, Jenkins and Azure DevOps so you can work in any of them.

## Why this matters in a bank or fintech

Many banks and large consultancies in Nigeria and abroad run Jenkins (often for over a decade) or Azure DevOps (common where Microsoft licensing is in place). New fintechs mostly use GitHub Actions or GitLab. In your first role you are as likely to inherit a `Jenkinsfile` as a GitHub workflow, and interviewers at banks often ask "have you worked with Jenkins?". You do not need to administer these systems, but you must be able to read, fix and extend your service's pipeline.

## 1. Jenkins in an enterprise

- A **controller** schedules builds and stores configuration; **agents** (VMs, containers or Kubernetes pods) run them. Builds should run on agents, never on the controller.
- A **Pipeline** is defined as code in a `Jenkinsfile` at the root of the repository. A **multibranch pipeline** job discovers branches and pull requests automatically and runs each one's `Jenkinsfile`.
- **Credentials** are stored in Jenkins (or fetched from Vault) and injected into steps by ID; they are masked in logs.
- **Shared libraries** let a platform team publish standard pipeline steps (`standardSpringBootPipeline()`) that every service calls.
- **Plugins** add almost everything (Git, Docker, JUnit reports, Sonar, Kubernetes agents). Plugin sprawl and outdated plugins are Jenkins's main operational risk; keep them updated.

## 2. A declarative `Jenkinsfile`

This is the same pipeline as topic 8.3, in Jenkins form (the Phase 1 service starter ships a validated version):

```groovy
pipeline {
    agent { label 'docker' }                         // run on an agent with Docker for Testcontainers
    tools { jdk 'temurin-21' }                        // a JDK configured under Manage Jenkins → Tools
    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds(abortPrevious: true)
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }
    environment {
        IMAGE = "registry.example.ng/payments/wallet-api:${env.GIT_COMMIT}"
    }
    stages {
        stage('Build and test') {
            steps {
                sh './mvnw -B verify'
            }
            post {
                always { junit 'target/surefire-reports/*.xml, target/failsafe-reports/*.xml' }
            }
        }
        stage('Quality gates') {
            parallel {
                stage('SonarQube') {
                    steps {
                        withSonarQubeEnv('sonarqube') { sh './mvnw -B sonar:sonar' }
                        timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true }
                    }
                }
                stage('Secret scan') {
                    steps { sh 'gitleaks detect --source . --verbose' }
                }
            }
        }
        stage('Image') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry', usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                    sh '''
                      echo "$REG_PASS" | docker login registry.example.ng -u "$REG_USER" --password-stdin
                      docker build -t "$IMAGE" .
                      trivy image --severity CRITICAL --ignore-unfixed --exit-code 1 "$IMAGE"
                      docker push "$IMAGE"
                    '''
                }
            }
        }
        stage('Deploy to production') {
            when { branch 'main' }
            input { message 'Deploy to production?'; submitter 'release-managers' }
            steps { sh "./deploy/deploy.sh production ${IMAGE}" }
        }
    }
    post {
        failure { echo "Notify the team channel here (Slack or Teams plugin)" }
    }
}
```

Things to notice:

- **`agent`, `stages`, `steps`, `post`**: the declarative skeleton; `post { always / success / failure }` for reports and notifications.
- **`when { branch 'main' }`** limits stages to the main branch in a multibranch job.
- **`withCredentials`** injects secrets as environment variables for one block; use single-quoted shell strings so Groovy does not interpolate (and leak) them.
- **`input`** pauses for a manual approval, restricted to a group with `submitter`.
- **`parallel`** runs independent stages at the same time.

You can validate a `Jenkinsfile` without running it through the declarative linter: `curl -X POST -F "jenkinsfile=<Jenkinsfile" $JENKINS_URL/pipeline-model-converter/validate`.

## 3. Azure DevOps Pipelines

Azure DevOps (Azure Repos, Boards, Pipelines, Artifacts) is common in banks with Microsoft estates. Pipelines are YAML (`azure-pipelines.yml`):

```yaml
trigger:
  branches: { include: [main] }
pr:
  branches: { include: [main] }

variables:
  - group: wallet-api-common          # a variable group (can link to Azure Key Vault)
  - name: imageTag
    value: $(Build.SourceVersion)

stages:
  - stage: Build
    jobs:
      - job: verify
        pool: { vmImage: ubuntu-latest }
        steps:
          - task: JavaToolInstaller@1
            inputs: { versionSpec: '21', jdkArchitectureOption: x64, jdkSourceOption: PreInstalled }
          - script: ./mvnw -B verify
            displayName: Build, test, coverage gate
          - task: PublishTestResults@2
            condition: always()
            inputs: { testResultsFiles: '**/TEST-*.xml' }

  - stage: Image
    dependsOn: Build
    condition: and(succeeded(), eq(variables['Build.SourceBranch'], 'refs/heads/main'))
    jobs:
      - job: push
        pool: { vmImage: ubuntu-latest }
        steps:
          - task: Docker@2
            inputs:
              containerRegistry: acr-service-connection     # a service connection, not a stored password
              repository: payments/wallet-api
              command: buildAndPush
              tags: $(imageTag)

  - stage: Production
    dependsOn: Image
    jobs:
      - deployment: deploy
        environment: wallet-production                     # approvals and checks are configured on the environment
        pool: { vmImage: ubuntu-latest }
        strategy:
          runOnce:
            deploy:
              steps:
                - script: ./deploy/deploy.sh production $(imageTag)
```

Key Azure DevOps concepts:

- **Stages → jobs → steps**, with `dependsOn` and `condition` expressions.
- **Tasks** (`Docker@2`, `Maven@4`, `PublishTestResults@2`) are versioned building blocks, similar to GitHub actions.
- **Variable groups** hold shared configuration; secret variables can come from **Azure Key Vault**.
- **Service connections** authenticate to Azure, container registries, Kubernetes or other clouds (with workload identity federation, the Azure equivalent of OIDC), so pipelines do not hold raw credentials.
- **Environments** with **approvals and checks** (required approvers, business hours, branch control) gate deployments.
- **Templates** (`template: templates/spring-boot.yml`) play the role of reusable workflows and shared libraries.

## 4. Concept map

| Concept | GitHub Actions | GitLab CI | Jenkins | Azure Pipelines |
| --- | --- | --- | --- | --- |
| Pipeline file | `.github/workflows/*.yml` | `.gitlab-ci.yml` | `Jenkinsfile` | `azure-pipelines.yml` |
| Unit of work | job / step | job / script | stage / step | stage / job / step |
| Reusable building block | action | `include` / CI component | shared library / plugin step | task / template |
| Where it runs | runner | runner | agent | agent (pool) |
| Secrets | secrets / environments | CI/CD variables (masked, protected) | credentials | secret variables, Key Vault |
| Cloud auth without keys | OIDC | OIDC (`id_tokens`) | Vault / cloud plugins | workload identity federation |
| Manual approval | environment reviewers | `when: manual` + protected environment | `input` step | environment approvals |
| Run only on main | `if: github.ref == 'refs/heads/main'` | `rules: if $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH` | `when { branch 'main' }` | `condition: eq(variables['Build.SourceBranch'], 'refs/heads/main')` |
| Test report | upload-artifact / test summary | `artifacts:reports:junit` | `junit` step | `PublishTestResults@2` |

Once you know one system well, the others are mostly a matter of syntax.

## 5. Migrating and coexisting

Banks moving from Jenkins to GitHub Actions or Azure DevOps usually run both for a while: migrate one service at a time, keep the same stages and gates so evidence stays comparable, and retire the old job only after the new pipeline has deployed to production successfully. GitHub's **Actions Importer** can convert Jenkins, Azure DevOps and GitLab pipelines as a starting point.

## Common mistakes

- Running builds on the Jenkins controller.
- Interpolating credentials in double-quoted Groovy strings (they can leak in logs and process lists).
- Clicking jobs together in the Jenkins UI instead of keeping the pipeline in a `Jenkinsfile` under version control.
- Hundreds of unmaintained plugins.
- Azure pipelines with passwords in variables instead of service connections and Key Vault.
- Approvals implemented as a comment or a chat message instead of a pipeline control.

## Practice

1. Run Jenkins LTS in Docker, create a multibranch pipeline for your capstone using the starter's `Jenkinsfile`, and get it green (you will need to configure a JDK tool and a Docker-capable agent).
2. Add an `input` approval stage and a `parallel` quality stage; validate the file with the declarative linter.
3. Create a free Azure DevOps organisation, import your repository, and run the starter's `azure-pipelines.yml`; add an environment with an approval.
4. Translate your GitHub Actions workflow into the other three systems using the concept map, and note what was easy and what was awkward.

## Key terms

Jenkins controller, agent, label, `Jenkinsfile`, declarative pipeline, `agent`, `stages`, `steps`, `post`, `when`, `parallel`, `input`, `withCredentials`, credentials ID, multibranch pipeline, shared library, plugin, declarative linter, Azure DevOps, Azure Pipelines, stage, job, task, pool, variable group, Key Vault, service connection, workload identity federation, environment, approvals and checks, template, Actions Importer.

## Further reading

- [Jenkins User Handbook: Pipeline](https://www.jenkins.io/doc/book/pipeline/)
- [Jenkins: Pipeline syntax (declarative)](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Jenkins: Using credentials](https://www.jenkins.io/doc/book/using/using-credentials/)
- [Microsoft Learn: Azure Pipelines YAML schema](https://learn.microsoft.com/en-us/azure/devops/pipelines/yaml-schema/)
- [Microsoft Learn: Approvals and checks in Azure Pipelines](https://learn.microsoft.com/en-us/azure/devops/pipelines/process/approvals)
- [GitHub Docs: Automating migration with GitHub Actions Importer](https://docs.github.com/en/actions/tutorials/migrate-to-github-actions/automated-migrations/use-github-actions-importer)
