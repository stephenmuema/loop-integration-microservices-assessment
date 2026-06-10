---
title: "NCBA Country Info Service — Kubernetes Troubleshooting Guide"
subtitle: "Step 10 — Troubleshooting the application on Kubernetes"
author: "NCBA Integration Microservices"
---

# Kubernetes Troubleshooting Guide

This guide helps diagnose and resolve common runtime problems for the
**Country Info microservice** running in the `ncba-countryinfo` namespace.

> All commands assume the namespace flag `-n ncba-countryinfo`. Set it once with
> `kubectl config set-context --current --namespace=ncba-countryinfo` to omit it.

---

## 0. First-line triage

Start every investigation with a broad snapshot, then drill in:

```bash
kubectl -n ncba-countryinfo get pods,svc,deploy,statefulset,hpa,ingress
kubectl -n ncba-countryinfo get events --sort-by=.lastTimestamp | tail -30
kubectl -n ncba-countryinfo describe pod <pod>          # 'Events:' section at the bottom
kubectl -n ncba-countryinfo logs <pod> --tail=200
kubectl -n ncba-countryinfo logs <pod> --previous       # logs from a crashed container
```

The application emits **structured JSON logs** (prod profile), so you can filter:
```bash
kubectl -n ncba-countryinfo logs deploy/countryinfo-app | grep '"level":"ERROR"'
```

---

## 1. Pod in `CrashLoopBackOff`

The container starts, exits, and Kubernetes keeps restarting it.

**Diagnose**
```bash
kubectl -n ncba-countryinfo describe pod <pod>          # look at Events + Last State (exit code)
kubectl -n ncba-countryinfo logs <pod> --previous       # the actual stack trace / startup error
```

**Common causes & fixes**

| Symptom in logs | Cause | Fix |
|---|---|---|
| `Communications link failure` / `Unknown database` | DB not reachable at startup | Ensure MySQL pod is Ready; check `DB_HOST=mysql`, credentials in Secret |
| `Access denied for user` | Wrong DB credentials | Reconcile `secret.yaml` with what MySQL was initialised with |
| `OOMKilled` (in `describe`, Last State) | Heap exceeds memory limit | Raise memory limit, or lower `JAVA_OPTS` `MaxRAMPercentage` |
| `Port 8080 already in use` | Misconfiguration | Usually transient on restart; check no custom `SERVER_PORT` clash |

> The liveness probe has a `startupProbe` guard (up to ~5 min) so a slow first
> boot is not mistaken for a crash. If a pod still crash-loops, it is a genuine
> startup failure — read `--previous` logs.

---

## 2. Pod stuck in `Pending`

The pod is scheduled but cannot start.

**Diagnose**
```bash
kubectl -n ncba-countryinfo describe pod <pod>          # reason is in Events
kubectl -n ncba-countryinfo get pvc                     # for the MySQL StatefulSet
kubectl get nodes -o wide
kubectl describe node <node> | grep -A5 Allocated
```

**Common causes & fixes**

| Event message | Cause | Fix |
|---|---|---|
| `Insufficient cpu` / `Insufficient memory` | No node has the requested resources | Lower `resources.requests`, or add/scale nodes |
| `pod has unbound immediate PersistentVolumeClaims` | PVC cannot bind | Ensure a default `StorageClass` exists (`kubectl get storageclass`); on bare clusters install one (e.g. local-path) |
| `0/N nodes are available: node(s) had taint` | Taints/affinity | Add tolerations or deploy to a schedulable node |

---

## 3. Service unreachable / no response

The app pods run, but requests to the Service time out or return nothing.

**Diagnose**
```bash
kubectl -n ncba-countryinfo get endpoints countryinfo-app    # MUST list pod IPs
kubectl -n ncba-countryinfo get pods --show-labels
kubectl -n ncba-countryinfo describe svc countryinfo-app
```

**Checklist**

- **Empty `ENDPOINTS`** → the Service selector (`app: countryinfo-app`) does not
  match pod labels, **or** no pod is *Ready* (only Ready pods are added to
  endpoints — check readiness probes).
- **Port mismatch** → Service `targetPort` must be `8080` (the container port);
  the Service `port` is `80`.
- **Test from inside the cluster** to isolate ingress vs. service issues:
  ```bash
  kubectl -n ncba-countryinfo run tmp --rm -it --image=busybox --restart=Never -- \
    wget -qO- http://countryinfo-app/actuator/health
  ```

---

## 4. Database connection refused

The app cannot reach MySQL.

**Diagnose**
```bash
kubectl -n ncba-countryinfo get pods -l app=mysql
kubectl -n ncba-countryinfo logs statefulset/mysql --tail=100
kubectl -n ncba-countryinfo get svc mysql                       # headless service must exist
kubectl -n ncba-countryinfo get configmap countryinfo-config -o jsonpath='{.data.DB_HOST}'; echo
kubectl -n ncba-countryinfo get secret countryinfo-secret -o jsonpath='{.data.DB_PASSWORD}' | base64 -d; echo
```

**Checklist**

- MySQL pod **Ready**? If `0/1`, read its logs — first boot initialises the DB
  and can take 30–60s; the app's startup probe tolerates this.
- `DB_HOST` in the ConfigMap must equal the MySQL Service name (`mysql`).
- Secret values (`DB_USERNAME`, `DB_PASSWORD`) must match the values MySQL was
  created with. If you changed them after first boot, the existing PVC still has
  the old credentials — recreate the DB (`delete pvc`) or fix the user.
- Connectivity test:
  ```bash
  kubectl -n ncba-countryinfo exec -it statefulset/mysql -- \
    mysql -u appuser -p"$DB_PASSWORD" -e "SELECT 1;"
  ```

---

## 5. High CPU / memory (and autoscaling)

**Diagnose**
```bash
kubectl -n ncba-countryinfo top pods                    # needs metrics-server
kubectl -n ncba-countryinfo get hpa countryinfo-app
kubectl -n ncba-countryinfo describe hpa countryinfo-app
```

**Checklist**

- **HPA shows `<unknown>/70%`** → metrics-server is not installed or not ready.
  Install it (`kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/...`)
  and confirm `kubectl top pods` works.
- **Pods pinned at the CPU limit (`500m`)** under load → the HPA should add
  replicas up to 10. If it is not scaling, check the HPA conditions in
  `describe hpa` (`AbleToScale`, `ScalingActive`).
- **Frequent OOM restarts** → raise the memory limit, or reduce JVM heap via
  `JAVA_OPTS` (`-XX:MaxRAMPercentage`).
- **External SOAP latency** inflating request threads → the SOAP client has
  5s/10s timeouts + circuit breaker; inspect breaker state at
  `/actuator/health` and `resilience4j_*` metrics at `/actuator/prometheus`.

---

## 6. Rolling update stuck

A new revision will not finish rolling out.

**Diagnose**
```bash
kubectl -n ncba-countryinfo rollout status deployment/countryinfo-app
kubectl -n ncba-countryinfo get pods                    # new ReplicaSet pods not becoming Ready?
kubectl -n ncba-countryinfo describe pod <new-pod>      # readiness probe failing? image pull error?
```

**Common causes & fixes**

| Cause | How to confirm | Fix |
|---|---|---|
| New image fails readiness | `describe pod` → readiness probe errors | Fix the image/config; rollout halts safely (`maxUnavailable: 0` keeps old pods serving) |
| `ImagePullBackOff` | `describe pod` Events | Wrong image name/tag or registry auth; fix `image:` / imagePullSecret |
| Bad config in new revision | new pods crash-loop | Roll back, then fix |

**Roll back** to the last working revision:
```bash
kubectl -n ncba-countryinfo rollout undo deployment/countryinfo-app
kubectl -n ncba-countryinfo rollout history deployment/countryinfo-app
```

---

## 7. Ingress not routing

```bash
kubectl -n ncba-countryinfo describe ingress countryinfo-ingress
kubectl get pods -n ingress-nginx                       # controller installed & running?
```

- Confirm an ingress controller is installed and the `ingressClassName: nginx`
  matches it.
- Ensure the request `Host` header matches `countryinfo.local` (or your host),
  and that the host resolves to the ingress address (`/etc/hosts` or DNS).
- Test the Service directly (port-forward) to confirm the problem is the ingress
  layer, not the app.

---

## 8. Useful one-liners

```bash
# Tail only error logs across all app pods
kubectl -n ncba-countryinfo logs -l app=countryinfo-app --tail=-1 -f | grep ERROR

# Exec a shell into a running app pod
kubectl -n ncba-countryinfo exec -it deploy/countryinfo-app -- sh

# Watch pods during an incident
kubectl -n ncba-countryinfo get pods -w

# Recent events, newest last
kubectl -n ncba-countryinfo get events --sort-by=.lastTimestamp

# Restart the app cleanly (e.g. after a config change)
kubectl -n ncba-countryinfo rollout restart deployment/countryinfo-app
```

---

For deployment steps, see the companion **Kubernetes Deployment Guide**.
