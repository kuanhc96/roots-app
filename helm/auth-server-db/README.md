# auth-server-db (Helm chart)

Kubernetes equivalent of the `auth-server-db` docker-compose service.

## Layout

```
auth-server-db/
├── Chart.yaml
├── values.yaml
├── values-local.yaml.example
├── files/
│   └── initdb/            # put your .sql files here (create_*.sql, initialize_*.sql)
└── templates/
    ├── _helpers.tpl
    ├── secret.yaml        # holds MYSQL_ROOT_PASSWORD
    ├── configmap.yaml     # built from files/initdb/*.sql
    ├── statefulset.yaml   # the MySQL pod + PVC for data
    └── service.yaml       # LoadBalancer :3307
```

## 1. Add your init scripts

Copy your existing files into the chart:

```bash
cp auth-server/src/main/resources/initialize_db/*.sql \
   helm/auth-server-db/files/initdb/
```

They get loaded into a ConfigMap and mounted at `/docker-entrypoint-initdb.d`,
same as the bind mount in compose. The MySQL entrypoint script still runs them
in alphabetical order, so no changes needed there.

## 2. Install

```bash
helm install auth-server-db ./helm/auth-server-db \
  -n roots --create-namespace \
  --set mysql.rootPassword="$MYSQL_AUTH_SERVER_ROOT_PASSWORD"
```

Or with a values file:

```bash
cp values-local.yaml.example values-local.yaml   # fill in a real password
helm install auth-server-db ./helm/auth-server-db -n roots -f values-local.yaml
```

Because the release name contains the chart name, the fullname helper collapses it,
so resources are named exactly `auth-server-db`. Install under a different release
name and they become `<release>-auth-server-db`.

For anything beyond local dev, don't pass the password as plain --set / values.yaml.
Instead create the Secret yourself and point the chart at it:

```bash
kubectl create secret generic auth-server-db-creds \
  --from-literal=mysql-root-password="$MYSQL_AUTH_SERVER_ROOT_PASSWORD" -n roots
helm install auth-server-db ./helm/auth-server-db -n roots \
  --set mysql.existingSecret=auth-server-db-creds
```

## 3. Connect from another pod in the cluster

Same idea as compose's `roots_backend` network — any pod in the same namespace
can reach it by Service name:

```
host: auth-server-db.roots.svc.cluster.local   (or just "auth-server-db" in-namespace)
port: 3307
```

## 4. Check it came up healthy

```bash
kubectl get pods -n roots -w
kubectl logs -n roots auth-server-db-0
```

The `livenessProbe`/`readinessProbe` run `mysqladmin ping`, mirroring the
compose `healthcheck` block (30s start delay, 10s interval, 5s timeout, 10 retries).

## 5. Uninstall

```bash
helm uninstall auth-server-db -n roots
```

Note: uninstalling does **not** delete the PVC (Helm/StatefulSets leave data
volumes behind on purpose). Delete it explicitly if you want a clean slate:

```bash
kubectl delete pvc -n roots -l app.kubernetes.io/instance=auth-server-db
```