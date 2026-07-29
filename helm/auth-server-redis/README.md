# auth-server-redis (Helm chart)

Kubernetes equivalent of the `auth-server-redis` docker-compose service.

## What it is for

This Redis backs **only** the Google Sign-In `oauth_state` store
(`GoogleAuthorizeService`). auth-server's `HttpSession` stays in-memory. Every key
is a short-lived state string, so the dataset is disposable — losing all of it
costs one failed Google login.

That single fact drives most of the chart's design, and is why it differs from the
`auth-server-db` chart:

| | auth-server-db | auth-server-redis |
|---|---|---|
| Workload | StatefulSet | Deployment (`Recreate`) |
| Storage | PersistentVolumeClaim | `emptyDir` |
| Secret | MySQL root password | none |
| ConfigMap | init `.sql` scripts | none |

## Layout

```
auth-server-redis/
├── Chart.yaml
├── values.yaml
├── values-local.yaml.example
└── templates/
    ├── _helpers.tpl
    ├── deployment.yaml     # the Redis pod + emptyDir at /data
    └── service.yaml        # LoadBalancer :6380
```

## Install

```bash
helm install auth-server-redis ./helm/auth-server-redis -n roots --create-namespace
```

No required values — everything has a working default.

Because the release name contains the chart name, the fullname helper collapses it,
so resources are named exactly `auth-server-redis`. Install under a different release
name and they become `<release>-auth-server-redis`.

## Connect from another pod

Same idea as compose's `roots_backend` network:

```
REDIS_HOST: auth-server-redis        # or auth-server-redis.roots.svc.cluster.local
REDIS_PORT: 6380                     # matches auth-server's own default, so it can be omitted
```

## Connect from a locally-run auth-server

`service.type` defaults to `LoadBalancer`, which on Docker Desktop binds to
`localhost`, matching compose's published `6380:6380`:

```bash
redis-cli -p 6380 ping
```

If you switch to `ClusterIP`, use a port-forward instead:

```bash
kubectl port-forward -n roots svc/auth-server-redis 6380:6380
```

> Do not run this chart and `docker compose up auth-server-redis` at the same time
> with `LoadBalancer` — both bind host port 6380.

## Why port 6380?

Compose put this instance on 6380 so its `6380:6380` publish would not collide with
`bff-server-redis`'s `6379:6379` on the shared bridge network. That constraint does
**not** exist in Kubernetes — two Services can both listen on 6379 and be told apart
by DNS name.

It is kept anyway because 6380 is now the application's own default
(`spring.data.redis.port: ${REDIS_PORT:6380}`), so the future auth-server chart only
needs to set `REDIS_HOST`.

## Check it came up healthy

```bash
kubectl get pods -n roots -w
kubectl logs -n roots deploy/auth-server-redis
kubectl exec -n roots deploy/auth-server-redis -- redis-cli -p 6380 ping
```

Both probes run `redis-cli -p 6380 ping`. They are tuned differently on purpose:

- **readinessProbe** (2s delay, 5s period, 3 failures) — this is the true equivalent of
  the compose `healthcheck`, which gates `depends_on: service_healthy` but never
  restarts anything. It is fast because Redis with no RDB to load is serving in well
  under a second.
- **livenessProbe** (10s delay, 10s period, 10 failures) — a deliberate Kubernetes
  addition compose has no equivalent for. It restarts a wedged Redis, but only after
  ~100s of unbroken failure, so a slow node never triggers a spurious kill.

## Uninstall

```bash
helm uninstall auth-server-redis -n roots
```

Nothing is left behind — there is no PVC, unlike the `auth-server-db` chart.

## TODO before this leaves a local cluster

**This Redis has no `requirepass`,** matching the compose service (dev/CI-grade, like
the DB). Combined with the `LoadBalancer` default that means a *public* load balancer
in front of an unauthenticated Redis the day this lands on a cloud provider.

Enabling AUTH is not a chart-only change — auth-server currently binds no password:

```yaml
# auth-server/src/main/resources/application.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6380}
      # password: ${REDIS_PASSWORD:}   <- does not exist yet
```

So closing this needs, together:

1. `spring.data.redis.password` added to auth-server,
2. a Secret + `--requirepass` in this chart (model it on `auth-server-db`'s
   `existingSecret` / `existingSecretPasswordKey` pattern),
3. `-a` added to the probe commands,
4. and `service.type: ClusterIP` unless external reachability is genuinely required.

A chart-only toggle was deliberately **not** added, because a values knob that cannot
work without a matching source change is worse than no knob — it looks supported and
silently is not.
