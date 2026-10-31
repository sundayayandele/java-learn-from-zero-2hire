## What you will be able to do

- Configure startup, liveness and readiness probes for a Spring Boot service, and say what each must and mustn't check.
- Set CPU and memory requests and limits for a JVM, and size the heap to fit.
- Roll out new versions with zero dropped requests, using readiness, a pre-stop pause and graceful shutdown.
- Scale on load with the Horizontal Pod Autoscaler, avoid the JVM warm-up trap, and know when to scale on something other than CPU.
- Protect availability during maintenance with a PodDisruptionBudget.

## Why this matters in a bank or fintech

Kubernetes keeps services running only if it can tell when they're healthy, how much they need, and when to stop sending them traffic. Get this wrong and the platform hurts you: liveness probes that restart every pod when the database blips, JVMs killed for using more memory than their limit, deployments that drop a few payments every release, and autoscalers that double the pods every time you deploy. The last two happened while building this module's starter on kind, and the measurements below come from those runs.

## 1. Three probes, three questions

| Probe | Question | On failure | ITL Pay endpoint |
| --- | --- | --- | --- |
| **Startup** | Has the app finished starting? | Keep waiting (up to a limit), then restart | `/actuator/health/liveness` |
| **Liveness** | Is the process broken beyond repair? | **Restart** the container | `/actuator/health/liveness` |
| **Readiness** | Should it receive traffic right now? | **Remove** it from the Service's endpoints | `/actuator/health/readiness` |

Spring Boot detects that it's running on Kubernetes and exposes both health groups automatically, backed by its **availability states**: liveness is `CORRECT` unless the app marks itself broken; readiness is `ACCEPTING_TRAFFIC` once started and switches to `REFUSING_TRAFFIC` when shutdown begins.

```yaml
startupProbe:                         # up to 5 minutes before liveness counts
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 60
livenessProbe:                        # "restart me": only the app's own state, never its dependencies
  httpGet: { path: /actuator/health/liveness, port: http }
  periodSeconds: 10
  timeoutSeconds: 3                   # the 1 s default restarted busy JVMs on a shared node
  failureThreshold: 3
readinessProbe:                       # "send me traffic": false while starting and while shutting down
  httpGet: { path: /actuator/health/readiness, port: http }
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

**Size the probes from measurements.** The first version of the chart used a 3-minute startup budget and Kubernetes' default 1-second probe timeout, and it worked, until Module 13 added OpenTelemetry and a Helm upgrade restarted every service at once. On the one-node kind cluster, transfers started in **19 seconds alone**, but JVMs starting **together** took **117 to 179 seconds** (they all compile code at full speed with small CPU requests), and probes timed out at 1 second while the JVMs were busy. Four pods were killed (exit code 143, `SIGTERM` from the kubelet) and restarted. With a 3-second timeout and a 5-minute startup budget, restarting all five Deployments at once gave **0 restarts**. Read `Started ... in N seconds (process running for M)` in your logs under realistic conditions, and leave a margin.

The rule that matters most: **liveness must never check dependencies.** If transfers' liveness included the database, a 30-second database failover would restart every transfers pod at once, turning a blip into an outage, and the restarts wouldn't fix the database (topic 10.3 made the same point about health endpoints). Readiness may include a dependency the service truly can't work without; even then, think about whether you want every pod to drop out together.

## 2. Requests and limits

| | Meaning | Used for |
| --- | --- | --- |
| **Request** | What the pod is guaranteed | Scheduling (does it fit on a node?) and the HPA's percentage |
| **Limit** | The most it may use | CPU: **throttled** above it. Memory: **killed** (`OOMKilled`) above it |

The starter's defaults, from `values.yaml`:

```yaml
defaults:
  javaOptions: "-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
  resources:
    requests: { cpu: 100m, memory: 384Mi }   # measured: about 200-270 MiB each after start-up
    limits: { memory: 640Mi }  # no CPU limit: throttling makes JVM start-up and GC pauses slow (topic 12.2)
```

**Memory.** The JVM is container-aware: `MaxRAMPercentage` sizes the heap as a share of the container's memory limit. But the JVM also needs memory outside the heap (metaspace, thread stacks, code cache, direct buffers), so a heap of 75% or more of a small limit invites `OOMKilled`. At 640 MiB, 60% gives a 384 MiB heap and leaves room for the rest. `ExitOnOutOfMemoryError` makes a JVM with a full heap exit, so Kubernetes restarts it, instead of limping on. The images set `JAVA_TOOL_OPTIONS`, and the chart overrides that variable, so the same image works in Compose and in a small pod. `kubectl top pods` on kind showed each service using 200–270 MiB after start-up, which is how the request was chosen.

**CPU.** Many teams set **no CPU limit** for JVM services: a JVM uses several cores while starting and during garbage collection, and a tight CPU limit throttles exactly those moments, making start-up take minutes and pauses longer. Set a realistic CPU **request** instead, so the scheduler and the autoscaler work with true numbers. If your platform requires CPU limits, set them generously and consider `-XX:ActiveProcessorCount` so the JVM sizes its thread pools to match.

## 3. Zero-downtime rollouts

During a rolling update, Kubernetes stops old pods while new ones take over. The trap: when a pod is told to stop, the Service, the Envoy gateway and kube-proxy on every node learn about it **slightly later**. For a moment, requests still arrive at a process that is shutting down.

Three pieces close the gap:

1. **A pre-stop pause.** `lifecycle.preStop.sleep.seconds: 5` (Kubernetes' built-in sleep action, no shell needed) keeps the old pod serving while everyone else stops routing to it.
2. **Graceful shutdown in Spring Boot** (the default since Boot 3.4): stop accepting new requests and finish the ones in flight, up to `spring.lifecycle.timeout-per-shutdown-phase` (30 s in the chart).
3. **A long enough `terminationGracePeriodSeconds`** (45 s in the chart): more than the pause plus the shutdown timeout, or Kubernetes kills the process mid-request.

We measured it on kind: 700 requests, one every 50 ms, sent to the gateway while `kubectl rollout restart` replaced both gateway pods.

| Gateway configuration | Result |
| --- | --- |
| With the pre-stop pause | **700 of 700** succeeded |
| Without it, first run | 696 succeeded, **4 failed** (connection refused) |
| Without it, second run | 692 succeeded, **8 failed** |

A few failed requests per deployment sound small until you deploy ten times a day and each one is a payment.

## 4. Horizontal Pod Autoscaler

The **HPA** adds or removes pods to keep a metric near a target. The chart enables it for the gateway:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: gateway
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: gateway }
  minReplicas: 2
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }   # 70% of the CPU *request*
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 120           # ignore the CPU burst of JVMs warming up after a rollout
    scaleDown:
      stabilizationWindowSeconds: 300
```

It needs **metrics-server** (installed by `up.sh`), and when the HPA owns a Deployment, the chart leaves `replicas` out of it, so `helm upgrade` doesn't fight the autoscaler.

**The JVM warm-up trap.** The first version of the chart had no `behavior` block and a 100m CPU request. Right after a rolling restart, with almost no traffic, the HPA scaled the gateway from **2 to 4 pods**: new JVMs compile code at full speed for their first minute, and 200m of CPU against a 100m request reads as 200% utilisation. Two fixes: a realistic CPU request for an autoscaled service (250m for the gateway), and a **scale-up stabilisation window**, so CPU must stay high for two minutes before the HPA acts. After the fix, a restart left it at 2 pods.

**Under real load** (12 parallel request loops for four minutes), it behaved as intended: CPU rose to 361% of the request, the HPA waited out its window, then scaled to **5 pods** (the maximum) and back down after the load ended.

**Scale on the right signal.** CPU suits request-handling services. For a Kafka consumer such as notifications, the real signal is **consumer lag**, and extra pods help only up to the partition count (topic 11.1). **KEDA** scales Deployments on lag, queue length or custom metrics, and is the usual choice for event-driven services.

## 5. PodDisruptionBudgets

Node upgrades and cluster maintenance **evict** pods voluntarily. A **PodDisruptionBudget** limits how many can be evicted at once:

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: gateway
spec:
  maxUnavailable: 1
  selector:
    matchLabels: { app.kubernetes.io/name: gateway }
```

The chart creates one for every service with more than one replica. It doesn't help a single-replica service (accounts): there, a drain means downtime, which is one more reason accounts needs a database and a second replica in your `v0.1`.

## Worked example: a release on salary day

1. Traffic is high; the HPA has transfers at 6 pods and the gateway at 4.
2. A new transfers image is rolled out. Each new pod passes its startup probe in about 20 seconds, then readiness; only then does Kubernetes stop an old one (`maxUnavailable` 25% by default).
3. Each old pod's readiness switches to refusing traffic, the pre-stop pause lets routing catch up, and Spring finishes in-flight requests before the JVM exits.
4. The HPA's stabilisation window ignores the warm-up CPU of the new pods, so it doesn't add pods it doesn't need.
5. A node upgrade later the same night drains nodes one at a time; the PodDisruptionBudgets keep at least three gateway pods serving throughout.

## Common mistakes

- Liveness probes that check the database or Kafka, so a dependency blip restarts everything.
- No startup probe, so slow JVM starts are killed by liveness.
- `MaxRAMPercentage=75` or more in a small container, then surprise `OOMKilled`.
- Tight CPU limits on JVMs, making start-up and GC painfully slow.
- No pre-stop pause, and a few dropped requests on every deployment.
- `terminationGracePeriodSeconds` shorter than the shutdown timeout.
- Setting `replicas` in a Deployment that an HPA manages.
- Autoscaling Kafka consumers on CPU beyond the partition count.

## Practice

1. Reproduce the rollout measurement: run the load loop from the lesson's numbers against the gateway while restarting it, with and without the pre-stop pause.
2. Set the memory limit to 300Mi and `MaxRAMPercentage=90` for fraud, generate traffic, and use `kubectl describe pod` to find the `OOMKilled` reason. Put it back.
3. Add a readiness check that includes the database for notifications only, stop `postgres-0` for 30 seconds, and describe what happened to notifications' endpoints and to transfers.
4. Remove the HPA's `behavior` block, restart the gateway, and record what the HPA does. Explain it in two sentences.
5. Install KEDA on kind and scale notifications on the consumer lag of `transfer-events` (a `ScaledObject` with the Kafka scaler).

## Key terms

Startup probe, liveness probe, readiness probe, availability state, health group, request, limit, throttling, `OOMKilled`, container-aware JVM, `MaxRAMPercentage`, non-heap memory, `ExitOnOutOfMemoryError`, rolling update, pre-stop hook, graceful shutdown, `terminationGracePeriodSeconds`, endpoints, Horizontal Pod Autoscaler, metrics-server, stabilisation window, KEDA, PodDisruptionBudget, voluntary disruption, drain.

## Further reading

- [Kubernetes: Configure liveness, readiness and startup probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Spring Boot reference: Kubernetes probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes)
- [Spring Boot reference: graceful shutdown](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)
- [Kubernetes: Horizontal Pod Autoscaling](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [Kubernetes: Pod disruptions](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/)
- [KEDA: Apache Kafka scaler](https://keda.sh/docs/latest/scalers/apache-kafka/)
