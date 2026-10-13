# production-lab-service

A small Spring Boot REST API that serves as a workload for production engineering / SRE labs:
deploying, monitoring, troubleshooting, scaling and recovering a service on Kubernetes.
Business logic is deliberately trivial. Operational behaviour is the point.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker (for the container image)

## Build, test, run

```bash
mvn test                       # unit + integration tests
mvn package                    # tests + executable jar at target/app.jar
mvn -DskipTests package        # jar only

# Run locally (readable logs, health details visible)
java -jar target/app.jar --spring.profiles.active=local
# or
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Container

```bash
docker build -t production-lab-service:local .

docker run --rm -p 8080:8080 -m 512m \
  -e SPRING_PROFILES_ACTIVE=lab \
  production-lab-service:local

# With failure simulation (isolated lab only)
docker run --rm -p 8080:8080 -m 512m \
  -e SPRING_PROFILES_ACTIVE=lab -e LAB_FAILURE_ENABLED=true \
  production-lab-service:local
```

`docker stop` sends SIGTERM. The service stops accepting new requests, lets in-flight requests finish
(up to `SHUTDOWN_TIMEOUT`) and then exits with code 143 (128 + SIGTERM). This is the normal exit
code for a JVM stopped by a signal.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/orders` | Three fixed sample orders |
| `GET /api/v1/orders/{id}` | One order, or 404 (RFC 9457 problem JSON) |
| `GET /api/v1/lab/delay?ms=1000` | Sleeps `ms` (default 1000, max `LAB_DELAY_MAX_MS`); 400 when out of range |
| `GET /api/v1/lab/failure` | Always 500. **Only exists when `LAB_FAILURE_ENABLED=true`**, otherwise 404 |
| `GET /actuator/health` | Overall health (details hidden outside `local`) |
| `GET /actuator/health/liveness` | Liveness group |
| `GET /actuator/health/readiness` | Readiness group |
| `GET /actuator/prometheus` | Metrics in Prometheus format |

```bash
curl -s localhost:8080/api/v1/orders
curl -s localhost:8080/api/v1/orders/1002
curl -i localhost:8080/api/v1/orders/9999
curl -s "localhost:8080/api/v1/lab/delay?ms=1500"
curl -i localhost:8080/api/v1/lab/failure
curl -s localhost:8080/actuator/health/readiness
curl -s localhost:8080/actuator/prometheus | grep http_server_requests
```

All other actuator endpoints (`env`, `beans`, `heapdump`, ...) are not exposed.

## Configuration

Profiles:

| Profile | Use | Differences |
|---|---|---|
| *(none)* | Base defaults | Plain-text logs, health details hidden |
| `local` | Workstation | Health details shown |
| `lab` | Lab cluster | One-line JSON logs (Elastic Common Schema) with `service.environment=lab` |

No profile enables failure simulation. It always needs an explicit `LAB_FAILURE_ENABLED=true`.

Environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | – | `local` or `lab` |
| `SERVER_PORT` | `8080` | HTTP port |
| `SHUTDOWN_TIMEOUT` | `20s` | Max time to drain in-flight requests after SIGTERM |
| `LAB_DELAY_MAX_MS` | `5000` | Upper bound for `/lab/delay`. Must be 0–30000, otherwise startup fails |
| `LAB_FAILURE_ENABLED` | `false` | Registers `/lab/failure`. Never `true` in production |
| `SERVER_TOMCAT_THREADS_MAX` | `200` | Worker threads (lower it to reproduce saturation faster) |
| `MANAGEMENT_SERVER_PORT` | same as app | Set e.g. `8081` to serve actuator on a separate, non-public port |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` | JVM flags (set in the image) |

Any other Spring property can be overridden the same way (relaxed binding:
`server.tomcat.connection-timeout` → `SERVER_TOMCAT_CONNECTION_TIMEOUT`). The service needs no secrets.

## Observability

**Logs:** one access-log line per request with `method`, `path`, `status` and `durationMs`.
With the `lab` profile these are separate JSON fields. 5xx responses are logged at WARN.
Query strings, headers and bodies are never logged. `/actuator/**` requests (probes, scrapes) are
not logged, because each pod gets one every few seconds.

**Metrics:** Spring Boot's built-in `http_server_requests_seconds_*` provides everything needed:

```promql
# Request rate
sum by (uri) (rate(http_server_requests_seconds_count{application="production-lab-service"}[5m]))
# Error ratio (5xx)
sum(rate(http_server_requests_seconds_count{outcome="SERVER_ERROR"}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))
# p95 latency per endpoint
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))
```

Labels are bounded: `uri` is the route template (`/api/v1/orders/{id}`), never the raw path.
Unmatched paths collapse into `uri="/**"`. Histogram buckets are fixed
(50ms … 10s) so the number of series stays predictable.
JVM (`jvm_memory_*`, `jvm_gc_pause_*`, `jvm_threads_*`), process (`process_cpu_usage`,
`process_uptime_seconds`) and Tomcat (`tomcat_threads_*`) metrics are included automatically.

## Running on Kubernetes (guidance for your manifests)

**Probes**

```yaml
startupProbe:            # protects slow JVM starts (e.g. under a low CPU limit) from liveness kills
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  periodSeconds: 5
  failureThreshold: 24   # up to 120s to start
livenessProbe:           # "is the process wedged?" Restart only if so
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  periodSeconds: 10
  timeoutSeconds: 2
  failureThreshold: 3
readinessProbe:          # "should this pod get traffic?"
  httpGet: { path: /actuator/health/readiness, port: 8080 }
  periodSeconds: 5
  timeoutSeconds: 2
  failureThreshold: 3
```

Liveness checks only the application's internal state, never downstream dependencies. Otherwise a
dependency outage restarts every pod at once. Readiness goes `OUT_OF_SERVICE` as soon as shutdown
begins.

**Resources and JVM memory**

```yaml
resources:
  requests: { cpu: 250m, memory: 512Mi }
  limits:   { memory: 512Mi }   # memory request == limit: predictable, no surprise OOMKills from overcommit
```

- The JVM sizes its heap from the container limit: `MaxRAMPercentage=75` → ~384 MiB heap with a
  512 MiB limit. The remaining 25% covers metaspace, code cache, thread stacks and direct buffers.
  Going much above 75–80% risks the kernel OOM-killing the container (exit 137) even though the
  heap is fine.
- `-XX:+ExitOnOutOfMemoryError` turns a Java heap OOM into a clean exit and restart instead of a
  JVM that limps along.
- A CPU limit causes CFS throttling and inflated tail latency, and the JVM derives
  `availableProcessors` from it, which affects GC choice and thread pools. Below 1 CPU it picks
  SerialGC. Either omit the CPU limit or set it ≥ 1 and watch
  `container_cpu_cfs_throttled_periods_total`.

**Termination**

```yaml
terminationGracePeriodSeconds: 30
lifecycle:
  preStop:
    sleep: { seconds: 5 }   # K8s ≥ 1.30; or exec: ["sleep","5"]
```

The order of events is: pod marked terminating → preStop sleep (lets endpoints and ingress stop
routing to the pod) → SIGTERM → Spring drains in-flight requests for up to `SHUTDOWN_TIMEOUT` (20s)
→ exit. Keep `preStop + SHUTDOWN_TIMEOUT` below `terminationGracePeriodSeconds`, or the kubelet
sends SIGKILL.

**Security context.** The image runs as UID/GID 10001, so `runAsNonRoot: true` works. With
`readOnlyRootFilesystem: true`, mount an `emptyDir` at `/tmp`, because Tomcat writes temp files
there.

**Failure endpoint.** Enable it only in an isolated lab namespace, and don't route
`/api/v1/lab/*` through any public ingress.

## Operational design decisions

- **No custom metrics code.** The built-in HTTP server metric already gives count, errors and
  latency with templated URIs. Writing our own would add code and could introduce
  cardinality bugs.
- **Failure endpoint is conditional, not guarded.** With `@ConditionalOnProperty` the controller
  doesn't exist unless enabled, so there is no code path to bypass, and it logs a WARN at startup
  when it is enabled.
- **Fail fast on bad config.** An invalid `LAB_DELAY_MAX_MS` stops startup. In Kubernetes that
  shows up as a CrashLoopBackOff, rather than surprising behaviour later at runtime.
- **Delay blocks a real worker thread.** Parallel `/lab/delay` calls can exhaust Tomcat's pool
  (see `tomcat_threads_busy_threads`), which makes saturation and latency incidents realistic.
  Virtual threads are intentionally not enabled.
- **Timeouts.** `connection-timeout=5s` drops idle or slow clients. `keep-alive-timeout=75s` is
  longer than typical ingress/LB upstream keep-alives (60s), which avoids intermittent 502s from the
  proxy reusing a connection that the backend has just closed.
- **Image.** Multi-stage build; the runtime is JRE-only Alpine (it keeps a shell for lab
  debugging; distroless is the stricter option). Layered jar extraction means code changes rebuild
  only the last layer. Java runs as PID 1 via exec form, so it receives SIGTERM directly.
  For real use, pin base images by digest.
- **Tests run outside the image build.** CI should run `mvn test` as its own stage, and
  `docker build` only packages.
