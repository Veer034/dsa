
## DOCKER
### Core Concepts

* [x] **What is Docker and containerization?**
    * **Containerization:** Packaging app + dependencies into isolated units that run consistently anywhere
    * **Docker**: Platform to build, ship, and run containers
    * **Key Benefits:**
        * Consistency: Works same everywhere
        * Isolation: No dependency conflicts
        * Portability: Dev to prod identical
        * Fast: Seconds to start
        * Efficient: Less resources than VMs


---
* [x] **Difference between virtualization and containerization**
    * VMs virtualize hardware with full OS per instance via hypervisor, while containers virtualize OS with shared kernel. Containers are lighter and faster but VMs provide stronger isolation. Use VMs for different OS requirements, containers for microservices and cloud-native apps.


---
* [x] **Explain Docker architecture (Docker Engine, Docker Daemon, Docker CLI)**

![Image](https://www.tutorialkart.com/wp-content/uploads/2017/09/docker-architecture.png)

* **Docker Engine**
* The core platform that builds, runs, and manages containers.
* It bundles **Docker Daemon**, **REST API**, and **Docker CLI**.
* **Example:** When you run `docker run nginx`, Docker Engine coordinates image pull, container creation, and start.

* **Docker Daemon (`dockerd`)**
* A background service that does the actual work: image builds, container lifecycle, networking, and volumes.
* It listens to Docker API requests (local socket or TCP).
* **Example:** CLI sends “run container” → Daemon pulls image from registry, creates namespaces/cgroups, starts container.


* **Docker CLI (`docker`)**
* The command-line client used by users.
* It does **not** run containers itself; it only sends commands to the Daemon via REST API.
* **Example:** `docker build -t app .` → CLI sends build request → Daemon executes build steps.

* **Simple Flow Example**

  ```
  docker run redis
  ↓
  Docker CLI → Docker API
  ↓
  Docker Daemon
  ↓
  Pull image → Create container → Start container
  ```

**In one line:**
CLI = *command sender*, Daemon = *worker*, Engine = *overall system*.


---
* [x] **What is a Docker image vs Docker container?**
    * A **Docker Image** is a **read-only template** containing application code, runtime, libraries, and config.
    * It is **immutable** and used to create containers (like a class in OOP).
    * A **Docker Container** is a **running instance of an image** (like an object).
    * Containers are **lightweight, isolated**, and have a writable layer.
    * **Example:** Image = `nginx:latest`, Container = running Nginx server created from that image.


---
* [x] **What is Docker Hub and Docker Registry?**

![Image](https://www.tutorialspoint.com/docker/images/docker_hub_1.jpg)
  * A **Docker Registry** is a **storage service** for Docker images (push/pull images).
  * It can be **public or private** (e.g., self-hosted registry, cloud registry).
  * **Docker Hub** is the **default public Docker registry** provided by Docker.
  * It hosts **official, community, and private images**.
  * **Example:** `docker pull nginx` → pulls from **Docker Hub** (a registry).


### Dockerfile & Images

* [x] **Explain Dockerfile instructions (FROM, RUN, CMD, ENTRYPOINT, COPY, ADD, ENV, EXPOSE, WORKDIR)**
  * **FROM** – Sets the **base image** for the Docker image.
    *Example:* `FROM openjdk:17-jdk`

  * **RUN** – Executes commands **at build time** to install dependencies or build code.
    *Example:* `RUN apt-get install -y curl`

  * **CMD** – Defines the **default command** to run when the container starts (can be overridden).
    *Example:* `CMD ["java","-jar","app.jar"]`

  * **ENTRYPOINT** – Defines the **main executable**; harder to override than CMD.
    *Example:* `ENTRYPOINT ["java","-jar","app.jar"]`

  * **COPY** – Copies files from **host to image** (simple and preferred).
    *Example:* `COPY app.jar /app/`

  * **ADD** – Like COPY, but also supports **URL fetch and tar extraction**.
    *Example:* `ADD app.tar.gz /app/`

  * **ENV** – Sets **environment variables** inside the container.
    *Example:* `ENV JAVA_OPTS="-Xmx512m"`

  * **EXPOSE** – Documents the **port** the container listens on.
    *Example:* `EXPOSE 8080`

  * **WORKDIR** – Sets the **working directory** for subsequent instructions.
    *Example:* `WORKDIR /app`


---
* [x] **Difference between CMD and ENTRYPOINT**

    * **CMD** – Provides a **default command/arguments**; can be **easily overridden** at runtime.
      *Example:* `CMD ["java","-jar","app.jar"]` → `docker run app bash` overrides it.

    * **ENTRYPOINT** – Defines the **main executable**; runtime arguments are **appended, not replaced**.
      *Example:* `ENTRYPOINT ["java","-jar","app.jar"]` → `docker run app --debug` passes `--debug`.
    * **Best practice:** Use **ENTRYPOINT** for the fixed executable and **CMD** for default arguments.

---
* [x] **Difference between COPY and ADD**
    * **COPY** – Copies files/directories **only from host to image**; simple and predictable.
      *Example:* `COPY app.jar /app/`

    * **ADD** – Does everything COPY does **plus** supports **URL download** and **auto-extracts tar files**.
      *Example:* `ADD app.tar.gz /app/`

    * **Best practice:** Prefer **COPY**; use **ADD** only when you need tar extraction or URL fetch.


---
* [x] **How to optimize Docker image size?**

![Image](https://miro.medium.com/1%2AzEhVBwQmSYOfWONtBf7CMQ.png)


  * Use **small base images** (e.g., `alpine`, `distroless`).
    *Example:* `FROM openjdk:17-jdk-alpine`
  * Use **multi-stage builds** to keep only runtime artifacts.
    *Example:* build JAR in stage-1, copy JAR to stage-2.
  * **Minimize layers**: combine RUN commands and clean cache.
    *Example:* `RUN apt-get update && rm -rf /var/lib/apt/lists/*`
  * **Exclude files** using `.dockerignore`.
  * Avoid unnecessary tools (curl, vim) in production images.
---
* [x] **What are layers in Docker images?**
    * In Docker, **layers** are the building blocks of images. Each layer represents a set of filesystem changes—like adding, modifying, or deleting files—that are stacked on top of each other to form a complete image.
    * **How layers are created:**Each instruction in a Dockerfile (like `RUN`, `COPY`, `ADD`) creates a new layer. For example:
  ```dockerfile
  FROM ubuntu:20.04          # Layer 1: base Ubuntu image
  RUN apt-get update         # Layer 2: package index update
  RUN apt-get install -y nginx  # Layer 3: nginx installation
  COPY index.html /var/www/  # Layer 4: copy your file
  ```

    * **Key characteristics:**
        * **Read-only and immutable:** Once created, layers don't change. If you modify a file, Docker creates a new layer with those changes rather than modifying the existing layer.
        * **Reusable and cached:** Docker caches layers, so if you rebuild an image and early layers haven't changed, Docker reuses them instead of rebuilding from scratch. This makes builds much faster.
        * **Shared across images:** Multiple images can share the same base layers. If you have 10 images all based on `ubuntu:20.04`, that base layer is stored only once on disk.
        * **Union filesystem:** When you run a container, Docker uses a union filesystem to stack all the layers together, making them appear as a single filesystem. The container also gets a thin writable layer on top where any changes during runtime are stored.


* [x] **How to run, stop, and remove containers?**

![Image](https://miro.medium.com/0%2A3_uIz_YMiyZxMwKn)


* **Run a container**

  ```bash
  docker run -d --name myapp nginx
  ```

Starts a new container from an image.

* **Stop a running container**

  ```bash
  docker stop myapp
  ```

Gracefully stops the container.

* **Remove a container**

  ```bash
  docker rm myapp
  ```

Deletes a stopped container.

* **Shortcut:**

  ```bash
  docker rm -f myapp
  ```
Stops **and** removes in one command.
---
* [x] **What are Docker volumes and why use them?**
    * **Docker volumes** are a mechanism for persisting data generated by and used by Docker containers. They exist outside the container's filesystem and are managed by Docker itself.

    * **Why use them:**
        * **Data persistence:** The main reason is to keep data even after a container stops or is removed. Without volumes, any data written inside a container is lost when that container is deleted.
        * **Sharing data:** Multiple containers can mount the same volume simultaneously, making it easy to share data between containers. For example, a web server container and a backup container could both access the same volume containing website files.
        * **Performance:** Volumes have better I/O performance than storing data in the container's writable layer, especially on Docker Desktop for Mac and Windows where containers run in a VM.
        * **Decoupling data from containers:** You can upgrade, replace, or rebuild containers without losing your data. This separation makes managing stateful applications much easier.
        * **Easier backups:** Since volumes are stored in a specific location on the host, they're easier to backup, migrate, or restore.
---
* [x] **Difference between bind mount and volume**
    * **Volume:**
        - Docker creates and manages a storage space for you
        - You don't know/care where it's stored on your computer
        - Example: `docker run -v mydata:/app`

    * **Bind mount:**
        - You pick a folder from your computer and share it with the container
        - Example: `docker run -v /home/user/code:/app`

    * **Simple difference:**
        - Volume = Docker handles it
        - Bind mount = You point to your own folder

    * **Use case:**
        - Volume: Save database data
        - Bind mount: Edit code on your computer, see changes in container immediately
---

* [x] **What is docker-compose and when to use it?**
    * Tool to run multiple containers together using a single YAML file.

    * **What it does:**
        - Define all your containers, networks, volumes in one file (`docker-compose.yml`)
        - Start everything with one command: `docker-compose up`
        - Stop everything: `docker-compose down`

    * **When to use:**
        - Running multi-container apps (e.g., web app + database + cache)
        - Development environments
        - When you're tired of typing long `docker run` commands
        - Need containers to talk to each other

  **Example:**
  ```yaml
  version: '3'
  services:
    web:
      image: nginx
    db:
      image: postgres
  ```

  Instead of:
  ```bash
  docker run nginx
  docker run postgres
  docker network create...
  ```

  You just run:
  ```bash
  docker-compose up
  ```

    * **Key benefit:** Everything in one file, easy to share and reproduce.

---
## KUBERNETES
### Core Concepts
* [x] **What is Kubernetes and why is it used?**
    * Kubernetes is a container orchestration platform for managing Docker containers at scale.
    * It automates deployment, scaling, load balancing, and self-healing.
    * Containers are grouped as Pods and run across a cluster of nodes.
    * Used to run highly available, scalable applications.
    * **Example:** If one container crashes, Kubernetes automatically restarts it and routes traffic to healthy pods.
---

* [x] **Explain Kubernetes architecture (Master node, Worker node, Control Plane)**
    * ![Image](https://www.tutorialworks.com/assets/images/kubernetes-architecture.jpg)

![Image](https://kubernetes.io/images/docs/components-of-kubernetes.svg)


* Kubernetes architecture follows a master-worker pattern, where a control plane manages a cluster of worker nodes that run your actual applications.


* **Control Plane (Master Node):**
    * The control plane is the brain of the cluster, making global decisions about what should run where and responding to cluster events. It consists of several key components:

    * **API Server:** acts as the front door to Kubernetes - every command, whether from kubectl, a dashboard, or
      automation tools, goes through here. It validates and processes requests, then updates the cluster state.

    * **etcd:** is a distributed key-value store that serves as Kubernetes' database, holding all cluster
      configuration data and the current state of everything in the cluster. It's the single source of truth.

    * **Scheduler:** watches for newly created pods (groups of containers) that haven't been assigned to a node yet,
      and decides which worker node should run them based on resource requirements, constraints, and availability.

    * **Controller Manager:** runs various controllers that regulate the cluster state. For example, the Replication
      Controller ensures the right number of pod replicas are running, while the Node Controller monitors node health.


* **Worker Nodes:**
    * Worker nodes are the machines that actually run your containerized applications. Each worker node contains:

    * **kubelet:** is an agent that runs on every node, communicating with the control plane. It ensures containers
      are running in pods as expected, reporting status back to the API server and executing commands.

    * **kube-proxy:** manages network rules on each node, enabling communication between pods across the cluster and
      handling load balancing for services.

    * **container runtime:** (like Docker, containerd, or CRI-O) is the software responsible for actually running
      containers.

* **How they work together:**

    1. **You submit a deployment** - You run something like `kubectl create deployment nginx --image=nginx`. This request goes to the **API Server**.

    2. **API Server stores it** - The API Server validates your request and stores the desired state in **etcd**. At this point, nothing is running yet - it's just a specification saying "I want a pod with nginx."

    3. **Controller Manager notices** - The **Deployment Controller** (part of the Controller Manager) is constantly
       watching the API Server. It sees the new deployment and creates a **ReplicaSet**, which then creates the
       **Pod specification** and sends it back to the API Server which stores it in etcd with status "Pending" and no
       node assignment yet.

    4. **Scheduler assigns it** - The **Scheduler** watches for pods that don't have a node assignment yet. It picks a suitable worker node based on resources and constraints, then updates the pod's specification in etcd to say "this pod should run on worker-node-2."

    5. **Kubelet creates the pod** - The **kubelet** on worker-node-2 is constantly watching the API Server for pods assigned to it. When it sees this new pod assignment, it:
        - Pulls the container image (nginx)
        - Tells the container runtime (Docker/containerd) to create and start the containers
        - Monitors the pod and reports status back to the API Server

---

* [x] **What are Pods, Nodes, and Clusters?**
  ![Image](https://kubernetes.io/docs/tutorials/kubernetes-basics/public/images/module_03_nodes.svg)


* **Pod** – Smallest deployable unit; one or more containers sharing **network and storage**.
  *Example:* Java app + sidecar container in one Pod.

* **Node** – A worker machine (VM/physical) that runs Pods.
  *Example:* One EC2 VM running multiple Pods.

* **Cluster** – A group of Nodes managed by Kubernetes.
  *Example:* 1 control plane + multiple worker nodes running the application.

---
### Workload Resources
* [x] **What is a Pod? Can a Pod contain multiple containers?**
    * A Pod is the smallest deployable unit in Kubernetes.
    * It wraps one or more containers with shared network (same IP/ports) and shared volumes.
    * Yes, a Pod can contain multiple containers, but they are scheduled and managed together.
---

* [ ] **Difference between Deployment, StatefulSet, DaemonSet, Job, CronJob**
    * ![Image](https://www.cherryservers.com/v3/assets/blog/2025-05-19/img-01.png)

![Image](https://miro.medium.com/v2/resize%3Afit%3A1200/1%2Ay02_WQcb6DUugimnodPSxw.png)

![Image](https://miro.medium.com/1%2ArCB97-0fd96m5A1akZsrSA.png)
*These are different types of Kubernetes **workload resources** that define how your pods should be created and
managed. Each solves a specific use case.
* **Deployment**
    * The most common type for stateless applications. It manages a set of identical pods and ensures the desired number of replicas are always running. Deployments are great for web servers, APIs, or any application where pods are interchangeable and don't need persistent identity. They support rolling updates and rollbacks. If a pod dies, the Deployment creates a new one to replace it, but the new pod has a different name and IP.
    * **Example use case:** Running 3 replicas of your web application

* **StatefulSet:**
    * For stateful applications that need stable, persistent identities. Unlike Deployments, StatefulSets give each pod a persistent identifier that maintains across rescheduling. Pods are created in order (pod-0, pod-1, pod-2) and have predictable network identities. Each pod can have its own persistent storage that follows it even if the pod is recreated.
    * **Example use case:** Databases like MySQL, MongoDB, Kafka clusters, or anything requiring stable network identity or persistent data per pod

* **DaemonSet**
    * Ensures that a copy of a pod runs on every node (or selected nodes) in your cluster. When you add a new node, the DaemonSet automatically schedules the pod there. When a node is removed, the pod is cleaned up. You typically don't specify replica counts - it's one per node automatically.
    * **Example use case:** Log collectors (Fluentd), monitoring agents (Prometheus node exporter), network plugins, or any system-level service that needs to run on every machine

* **Job**
    * Creates one or more pods and ensures they successfully complete a task and then terminate. Jobs run to completion - they're not meant to run forever. If a pod fails, the Job can retry it. Once the task succeeds, the pod terminates and the Job is complete.
    * **Example use case:** Batch processing, database migrations, sending emails, one-time data imports, running a script

* **CronJob**
    * Creates Jobs on a schedule, just like cron in Linux. It's a Job wrapper that runs repeatedly at specified times. Each scheduled execution creates a new Job, which creates pods to do the work.
    * **Example use case:** Nightly backups, periodic report generation, cleanup tasks that run every hour, sending weekly summary emails

* **Quick comparison:**
    - **Deployment:** "Keep 3 web servers running all the time"
    - **StatefulSet:** "Keep 3 database instances running with persistent storage and stable names"
    - **DaemonSet:** "Run a log collector on every single node"
    - **Job:** "Process this batch of data once, then stop"
    - **CronJob:** "Run a backup every night at 2 AM"
---

* [x] **How does Canary & Blue-Green deployment works in Kubernetes?**
    * **Blue-Green Deployment**
        * Blue-Green involves running two identical production environments simultaneously:

        * **How it works:**
            - **Blue environment**: Current production version serving all traffic
            - **Green environment**: New version deployed alongside, receives no traffic initially
            - You test the green environment thoroughly while blue continues serving users
            - When ready, you switch traffic from blue to green (typically via Service selector change)
            - If issues arise, you can instantly roll back by switching traffic back to blue
            - Once stable, the old blue environment can be decommissioned or kept as the new standby

        * **In Kubernetes:**
            * You create two separate Deployments and switch traffic by updating the Service's selector labels:

          ```yaml
          # Blue Deployment (current)
          selector:
          app: myapp
          version: blue
    
          # Green Deployment (new)
          selector:
          app: myapp
          version: green
    
          # Service switches between them
          selector:
          app: myapp
          version: blue  # Change to "green" to switch
          ```

        * **Pros:** Instant rollback, full testing before switch, zero downtime

        * **Cons:** Requires double the resources, all-or-nothing switch

    * **Canary Deployment**
        * Canary gradually rolls out changes to a small subset of users before full deployment:

        * **How it works:**
            - Deploy new version alongside old version
            - Route small percentage of traffic (e.g., 10%) to new version
            - Monitor metrics, errors, and performance
            - Gradually increase traffic to new version if stable (20%, 50%, 100%)
            - Roll back immediately if issues detected
            - Eventually phase out old version completely

        * **In Kubernetes:**
            * You run both versions simultaneously and control traffic distribution:

          ```yaml
          # Old version: 9 replicas
          apiVersion: apps/v1
          kind: Deployment
          metadata:
            name: myapp-stable
          spec:
            replicas: 9
            selector:
              matchLabels:
                app: myapp
                version: stable
    
          # New version: 1 replica (10% traffic)
          apiVersion: apps/v1
          kind: Deployment
          metadata:
            name: myapp-canary
          spec:
            replicas: 1
            selector:
              matchLabels:
                app: myapp
                version: canary
    
          # Service routes to both
          selector:
            app: myapp  # Matches both versions
          ```
            * For more sophisticated traffic control, you'd use service meshes like Istio or ingress controllers that support
              weighted routing.

            * **Pros:** Lower risk, gradual rollout, real user testing, early detection of issues

            * **Cons:** More complex monitoring needed, requires traffic splitting capability, longer deployment time

    * **Key Differences**
        * **Blue-Green** is like a light switch—you flip from old to new instantly.
        * **Canary** is like a dimmer—you gradually increase the new version's exposure.
        * Blue-Green is simpler but requires more resources and is all-or-nothing.
        * Canary is more conservative and catches issues with minimal user impact but requires more sophisticated traffic management and monitoring.
---
### Networking
* [x] **How does networking work in Kubernetes?**
    * **Core Principles**
        * Every pod gets its own IP address
        * All pods can communicate with each other across nodes without NAT
        * Containers in the same pod share the same IP and communicate via localhost

    * **Network Layers**
        1. Container-to-Container (same pod): Share network namespace, use localhost
        2. Pod-to-Pod: Direct IP communication. CNI plugins (Calico, Flannel, Cilium) handle routing between nodes
        3. Pod-to-Service: Services provide stable IPs and DNS names. kube-proxy uses iptables/IPVS to load balance traffic to pods
        4. **External-to-Service:**
            1. NodePort: Exposes service on node IPs at static port
            2. LoadBalancer: Creates external load balancer (cloud provider)
            3. Ingress: HTTP/HTTPS routing with path-based rules

  ```
  External Request → LoadBalancer → Ingress Controller
          → Service (ClusterIP) → kube-proxy → Pod
  ```      

---

* [x] **What is a Service? Types of Services (ClusterIP, NodePort, LoadBalancer, ExternalName)**
    * A Service is an abstraction that provides a stable network endpoint for a dynamic set of pods. Since pods are ephemeral and their IPs change, Services provide:
        - **Stable IP address** (doesn't change when pods restart)
        - **DNS name** for service discovery
        - **Load balancing** across multiple pod replicas
        - **Service discovery** mechanism

    * **Service Types**
        1. **ClusterIP (Default)**
            1. **Purpose:** Internal-only access within the cluster
            2. **Use case:** Backend services, databases, internal APIs

            ```yaml
            apiVersion: v1
            kind: Service
            metadata:
              name: backend-service
            spec:
              type: ClusterIP
              selector:
                app: backend
              ports:
              - port: 80
                targetPort: 8080
            ```

            3. **Access:** Only from within cluster via `backend-service:80` or ClusterIP

        2. **NodePort**
            1. **Purpose:** Exposes service on each node's IP at a static port (30000-32767)
            2. **Use case:** Development, testing, or when you need direct node access

           ```yaml
            apiVersion: v1
            kind: Service
            metadata:
              name: web-service
            spec:
    
              type: NodePort
              selector:
                app: web
              ports:
              - port: 80
                targetPort: 8080
                nodePort: 30080  # Optional, auto-assigned if not specified
            ```
            3. **Access:** `http://<any-node-ip>:30080` from outside cluster
            4. **Note:** Also creates a ClusterIP automatically

        3. **LoadBalancer**
            1. **Purpose:** Provisions an external load balancer (requires cloud provider support)
            2. **Use case:** Production applications needing public internet access

            ```yaml
            apiVersion: v1
            kind: Service
            metadata:
              name: frontend-service
            spec:
              type: LoadBalancer
              selector:
                app: frontend
              ports:
              - port: 80
                targetPort: 8080
            ```
      **Access:** Cloud provider assigns external IP, accessible at `http://<external-ip>:80`
      **Note:** Creates NodePort and ClusterIP automatically. Only works on cloud platforms (AWS, GCP, Azure)
        4. **ExternalName**
            1. **Purpose:** Maps a service to an external DNS name (CNAME record)
            2. **Use case:** Accessing external services with Kubernetes service abstraction

            ```yaml
            apiVersion: v1
            kind: Service
            metadata:
              name: external-api
            spec:
              type: ExternalName
              externalName: api.external-service.com
            ```

        * **Access:** Pods access `external-api` which resolves to `api.external-service.com`
        * **Note:** No proxy, no port mapping - just DNS redirection


* Quick Comparison

  | Type | Scope | IP Type | Use Case |
      |------|-------|---------|----------|
  | **ClusterIP** | Internal | Virtual IP | Internal services |
  | **NodePort** | External | Node IPs + Port | Dev/Testing |
  | **LoadBalancer** | External | External IP | Production apps |
  | **ExternalName** | External | DNS CNAME | External services |


* **How Services Route Traffic**

  ```yaml
  # Service configuration
  selector:
    app: myapp  # Matches pods with this label

  # Traffic flow
  Client → Service (10.96.0.10:80) 
         → kube-proxy (load balances)
         → Pod A (10.244.1.5:8080) or
         → Pod B (10.244.2.10:8080) or
         → Pod C (10.244.3.15:8080)
  ```

  Services automatically update their endpoints as pods are created/destroyed.
---
* [x] **What is Ingress and Ingress Controller?**

![Image](https://media.geeksforgeeks.org/wp-content/uploads/20240506105314/Kubernetes-Ingress-Architecture-%281%29.webp)

* **Ingress**
    * **Ingress** is a Kubernetes object that defines **HTTP/HTTPS routing rules**.
    * Routes traffic using **paths** (`/api`) **and hosts/subdomains** (`test.service.com`).
    * It does **not handle traffic itself**; it only declares rules.

* **Ingress Controller**
    * A **running component** (NGINX, ALB, Traefik) that **implements Ingress rules**.
    * Ingross Controller starts with a load balancer in kubernates which gets a public IpAddress.
    * That Ip Address in mapped in Cloud DNS like squarespace.
    * Reads Ingress objects and configures a **Layer-7 reverse proxy**.


* **Why not just LoadBalancer Service?**

| LoadBalancer Service | Ingress                         |
  | -------------------- | ------------------------------- |
| One LB per service   | **Single LB for many services** |
| L4 routing (TCP/UDP) | **L7 routing (HTTP/HTTPS)**     |
| No host/path rules   | **Path + subdomain routing**    |
| Higher cost          | **Cost-efficient**              |



* Example routing

    * `test.service.com` → test-service
    * `data.service.com` → data-service
    * `service.com/api` → api-service

* **Flow:**
  ```
  DNS → LoadBalancer → **Ingress Controller** → Service → Pod
  ```

* **Bottom line:** LoadBalancer exposes **one service**; Ingress exposes **many services** using **paths and
  subdomains** through a **single entry point**.
---

* [x] **Explain how DNS works in Kubernetes**

![Image](https://www.f5.com/_next/image?q=75\&url=https%3A%2F%2Fcdn.studio.f5.com%2Fimages%2Fk6fem79d%2Fproduction%2F37ec3ecece1ea253c12d2e63aaf0011c06ccdde4-2048x1400.png\&w=1600)

![Image](https://miro.medium.com/v2/resize%3Afit%3A1400/1%2ApfpAcO8pAqXWbB8QSQFdKw.png)


* 1️⃣ External DNS (Public Internet)

  ```
  User → DNS (Squarespace / Route53)
       → LoadBalancer IP / DNS
  ```

    * DNS **does NOT know Kubernetes**
    * It only maps:

  ```
  test.service.com → LoadBalancer IP
  ```


* 2️⃣ Cloud Load Balancer

  ```
  LoadBalancer → Ingress Controller
  ```

    * LB is created by Kubernetes
    * Just forwards traffic (no routing logic)



* 3️⃣ Ingress Controller (L7 routing)

  ```
  Ingress Controller
    - checks Host: test.service.com
    - checks Path: /
    → routes to Service
  ```

Ingress rules live **here**, not in DNS.


* 4️⃣ Kubernetes Internal DNS (CoreDNS)

  ```
  Service name → ClusterIP
  ```

    * CoreDNS resolves:

  ```
  my-service.default.svc.cluster.local
  ```
    * This DNS is used **only inside the cluster**


* 5️⃣ Service → Pod

  ```
  Service → Pod IP
  ```
    * Service load-balances to healthy Pods


* Complete correct flow (end-to-end)

  ```
  User
   → Public DNS
   → Cloud LoadBalancer
   → Ingress Controller
   → Service (via CoreDNS)
   → Pod
  ```


* One-line truth
    * > DNS outside Kubernetes maps domains to LoadBalancers; DNS inside Kubernetes maps Services to Pods — Ingress sits between them doing HTTP routing.


### Storage
* [x] **What are Volumes in Kubernetes?**
    * In Kubernetes, **Volumes** are a way to provide storage to containers that persists beyond the lifecycle of individual containers and can be shared between containers in a Pod.
    * **Why Volumes are needed:** Containers are ephemeral(short period of time) by design - when a container crashes or restarts, any data
      written to its filesystem is lost. Volumes solve this problem by providing persistent or shared storage that exists independently of any single container's lifecycle.

    * **Key characteristics:**
        * Volumes in Kubernetes have a lifetime tied to the Pod (not individual containers). This means the volume persists across container restarts within the same Pod, but typically gets cleaned up when the Pod is deleted (though this depends on the volume type).
        * Multiple containers within the same Pod can mount and share the same volume, making it useful for scenarios where containers need to exchange data or one container produces data that another consumes.

    * **Common volume types**
        - **emptyDir**: Creates an empty directory when the Pod starts, useful for temporary scratch space or sharing data between containers in a Pod
        - **hostPath**: Mounts a file or directory from the host node's filesystem into the Pod
        - **configMap** and **secret**: Mount configuration data or sensitive information as files
        - **persistentVolumeClaim (PVC)**: References a PersistentVolume for durable storage that survives Pod deletion
        - Cloud provider volumes like **awsElasticBlockStore**, **gcePersistentDisk**, **azureDisk**
        - Network storage like **nfs**, **cephfs**, **glusterfs**

---
* [x] Difference between PersistentVolume (PV) and PersistentVolumeClaim (PVC)
    * **PersistentVolume (PV)**
        * A **PersistentVolume** is a piece of storage in the cluster that has been provisioned by an administrator or dynamically provisioned using Storage Classes. It's a **cluster-level resource** that exists independently of any Pod.
        * Think of a PV as the actual storage resource itself - like a physical disk, a cloud storage volume, or a network file system share. It has its own lifecycle independent of Pods and contains details about the storage like capacity, access modes, and the underlying storage technology.

        * **Key characteristics:**
            - Created by cluster administrators (or automatically via dynamic provisioning)
            - Cluster-wide resource, not namespaced
            - Defines the actual storage backend (NFS, cloud disk, etc.)
            - Specifies capacity, access modes, and reclaim policy
            - Exists independently of any application

    * **PersistentVolumeClaim (PVC)**
        * A **PersistentVolumeClaim** is a **request for storage** by a user or Pod. It's similar to how a Pod consumes node resources - a PVC consumes PV resources.
        * Think of a PVC as a "storage request form" where developers specify what they need (size, access mode) without worrying about where the actual storage comes from. Kubernetes then binds the PVC to an appropriate PV that satisfies the requirements.

        * **Key characteristics:**
            - Created by developers/users
            - Namespaced resource (belongs to a specific namespace)
            - Specifies desired storage size and access modes
            - Gets bound to a matching PV
            - Used by Pods to mount storage

    * The relationship works like this:

        1. **Admin provisions** a PV (or it's created automatically via Storage Class)
        2. **Developer creates** a PVC requesting storage with specific requirements
        3. **Kubernetes binds** the PVC to a suitable PV that meets the requirements
        4. **Pod references** the PVC in its volume definition to use the storage

    * This separation provides **abstraction** - developers don't need to know the details of the underlying storage
      infrastructure. They just request what they need, and Kubernetes handles matching it to available storage.

### Configuration & Secrets
* [x] **What are ConfigMaps and Secrets?**
    * **ConfigMaps**
        * **ConfigMaps** are Kubernetes objects used to store non-confidential configuration data in key-value pairs. They allow you to decouple configuration from your container images, making your applications more portable and easier to manage.

        * **Common use cases:**
            - Application configuration files
            - Command-line arguments
            - Environment variables
            - Configuration data like database URLs, feature flags, or application settings

        * **Key characteristics:**
            - Store plain text data (not encrypted)
            - Can hold entire configuration files or individual key-value pairs
            - Data is stored unencrypted in etcd (Kubernetes' data store)
            - Can be consumed as environment variables, command-line arguments, or mounted as files in volumes

  **Example data in a ConfigMap:**
  ```
  database_url: "mysql://db.example.com:3306"
  app_mode: "production"
  max_connections: "100"
  ```

    * **Secrets**
        * **Secrets** are similar to ConfigMaps but are specifically designed to store sensitive information like passwords, tokens, SSH keys, or certificates. While they provide some additional protections, they're not encrypted by default in etcd (though encryption can be enabled).

        * **Common use cases:**
            - Database passwords
            - API keys and tokens
            - TLS certificates
            - SSH keys
            - OAuth tokens

        * **Key characteristics:**
            - Intended for sensitive data
            - Base64 encoded (not encrypted by default, but can be encrypted at rest with additional configuration)
            - Stored separately from Pods to reduce exposure risk
            - Can be consumed as environment variables or mounted as files
            - Kubernetes takes extra precautions like only sending secrets to nodes that need them

  **Example data in a Secret:**
  ```
  username: YWRtaW4=  (base64 encoded "admin")
  password: cGFzc3dvcmQxMjM=  (base64 encoded "password123")
  ```

    * **Key differences**
        * **Security**: Secrets are designed for sensitive data and have additional security considerations, while ConfigMaps are for non-sensitive configuration.
        * **Encoding**: Secret values are base64 encoded, ConfigMap values are stored as plain text.
        * **Access control**: Secrets typically have stricter RBAC policies to limit who can read them.
        * **Best practice**: Never put sensitive information in ConfigMaps - always use Secrets for passwords, keys, and tokens, even though Secrets aren't encrypted by default without additional configuration.

    * **How Pods use them**

  Both can be consumed in similar ways:

  **As environment variables:**
  ```yaml
  env:
    - name: DATABASE_URL
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: database_url
  ```

  **As mounted volumes:**
  ```yaml
  volumes:
    - name: config-volume
      configMap:
        name: app-config
  ```

This approach keeps configuration separate from code, making it easier to change settings without rebuilding container images.

---
* [x] **How to inject environment variables in Pods?**
  ![Image](https://k21academy.com/wp-content/uploads/2020/10/image-9.png)


* 1) Directly in Pod spec

```yaml
env:
- name: APP_ENV
  value: prod
```

Use for **simple, static values**.



* 2) From ConfigMap

```yaml
envFrom:
- configMapRef:
    name: app-config
```

Use for **non-sensitive configuration** shared across Pods.



* 3) From Secret

```yaml
envFrom:
- secretRef:
    name: db-secret
```

Use for **passwords, tokens, keys**.



* 4) Downward API (Pod metadata)

```yaml
env:
- name: POD_NAME
  valueFrom:
    fieldRef:
      fieldPath: metadata.name
```

Use to inject **Pod/Node metadata**.

**Rule of thumb:**
Hardcoded → Env, Config → ConfigMap, Secrets → Secret, Metadata → Downward API.

---

### Scaling & Autoscaling
* [x] **What is Horizontal Pod Autoscaler (HPA)?**
  ![Image](https://docs.sisense.com/main/Resources/Images/HPA_Flow_789x561.png)


* **Horizontal Pod Autoscaler (HPA) – Concise**
    * **HPA** automatically **scales the number of Pods** based on **metrics**.
    * Common metrics: **CPU**, **memory**, or **custom metrics** (QPS, latency).
    * It continuously adjusts replicas to match the **target utilization**.

* **Example:**
  If CPU > 70% → HPA **adds Pods**
  If CPU < 70% → HPA **removes Pods**

* **Key requirement:** Metrics Server must be installed.

* **One-line summary:**

> HPA keeps applications responsive by scaling Pods up or down automatically.
---

* [x] **What is Vertical Pod Autoscaler (VPA)?**

![Image](https://developers.redhat.com/sites/default/files/vpa_diagram_0.png)

![Image](https://miro.medium.com/v2/resize%3Afit%3A1400/1%2A0wJBUCAWTLAe62PHmhoLOQ.gif)

* **Vertical Pod Autoscaler (VPA) – Concise**

    * **VPA** automatically **adjusts CPU and memory requests/limits** of Pods.
    * It analyzes **historical usage** and recommends or applies better resource values.
    * Can **restart Pods** to apply new resource settings.

* **Modes:**

    * **Off** – only recommendations
    * **Auto** – applies changes (Pod restarts)
    * **Initial** – sets resources at Pod creation

* **One-line summary:**

> VPA optimizes Pod resource sizing, while HPA optimizes Pod count.

---
* [x] **How to manually scale deployments?**


* 1) Using `kubectl scale`

  ```bash
  kubectl scale deployment my-app --replicas=5
  ```

Immediately sets the desired number of Pods.


* 2) By editing Deployment YAML

  ```yaml
  spec:
    replicas: 5
  ```

Apply with:

  ```bash
  kubectl apply -f deployment.yaml
  ```


* 3) Verify scaling

  ```bash
  kubectl get pods
  ```

* **Key point:** Manual scaling updates **replica count** only; Kubernetes creates or removes Pods to match it.

* **One-line summary:**

> Manual scaling = change `replicas`, Kubernetes does the rest.

---
### Health & Monitoring
* [X] **Difference between Liveness probe, Readiness probe, and Startup probe**
    * ![Image](https://miro.medium.com/v2/resize%3Afit%3A1170/1%2Aoqt8GlUYvm-OrO7gNBJjNQ.png)



* **Liveness Probe**

    * Checks if the container is **alive**.
    * ❌ Fails → **container is restarted**.
    * Use when app can get stuck.

* **Readiness Probe**
    * Checks if the container is **ready to receive traffic**.
    * ❌ Fails → **removed from Service endpoints** (no restart).
    * Use during warm-up or temporary failures.

* **Startup Probe**
    * Checks if the app has **started successfully**.
    * ❌ Fails → **container is restarted**.
    * Disables liveness/readiness until startup completes.



> Startup = “did it start?”, Readiness = “can it take traffic?”, Liveness = “is it healthy?”.

---
* [x] **How configured pods logging into elasticsearch and monitoring in Kibana?*
  ![Image](https://miro.medium.com/v2/resize%3Afit%3A1400/1%2ARTRQN5_8S78yL9x7J4Lkjw.png)

    1. Application Pods write logs
        * Apps log to **stdout / stderr**.
        * Kubernetes (kubelet) writes these to node files:

          ```
          /var/log/containers/*.log
          ```

    2) Filebeat DaemonSet collects logs

        * **Filebeat runs as a DaemonSet** (one per node).
        * It **mounts node log directories** and tails all container logs.
        * Adds Kubernetes metadata (namespace, pod, container).

    3) Filebeat ships logs to Elasticsearch

        * Filebeat is configured with Elasticsearch endpoint:

          ```yaml
          output.elasticsearch:
            hosts: ["http://elasticsearch:9200"]
          ```
        * Logs are indexed (e.g., `filebeat-*`).

    4) Elasticsearch stores & indexes logs

        * Elasticsearch stores logs as **documents**.
        * Enables fast search and aggregation.

    5) Kibana visualizes logs

        * Kibana connects to Elasticsearch.
        * You create **index patterns** (`filebeat-*`) to:

            * Search logs
            * Filter by namespace/pod
            * Build dashboards & alerts


* Complete flow

  ```
  Pod stdout/stderr
   → Node log files
   → Filebeat (DaemonSet)
   → Elasticsearch
   → Kibana
  ```


* Why this works well

    * No app code changes required
    * Scales automatically with nodes
    * Centralized logging across all namespaces

* **One-line summary:**

  > Pods log to stdout, Filebeat collects from nodes, Elasticsearch stores, Kibana visualizes.
---
### Operations
* [x] **How to debug a failing Pod?**
  ![Image](https://miro.medium.com/1%2AkvU3t_01FqvojFO22BZsOg.png)

![Image](https://leading-bell-3e1c02e64d.media.strapiapp.com/g_A_Iu2_M_da1cc75bfa.png)

![Image](https://docs.bitnami.com/images/img/platforms/kubernetes/troubleshoot-kubernetes-deployments-1.png)


1) Check Pod status

  ```bash
  kubectl get pods
  ```

Look for `CrashLoopBackOff`, `ImagePullBackOff`, `Pending`.
  
---

2) Describe the Pod (most important)

  ```bash
  kubectl describe pod <pod-name>
  ```

Check **Events** section for errors (image pull, OOM, probe failure).
  
---

3) Check container logs

  ```bash
  kubectl logs <pod-name>
  kubectl logs <pod-name> -c <container-name>
  ```

For crashed Pods:

  ```bash
  kubectl logs <pod-name> --previous
  ```
  
---

4) Exec into Pod (if running)

  ```bash
  kubectl exec -it <pod-name> -- /bin/sh
  ```

Check config, env vars, file paths.
  
---

5) Common root causes
    * Wrong image / tag
    * Missing env vars / secrets
    * Liveness or readiness probe failure
    * Insufficient CPU/memory (OOMKilled)

---

### 6) Check node issues (if Pending)

  ```bash
  kubectl describe node <node-name>
  ```

> Describe → Logs → Events → Exec → Node — this order finds 90% of Pod failures.
---

* [x] **What are labels and selectors?**
  ![Image](https://miro.medium.com/v2/resize%3Afit%3A1400/1%2A7T1rKk04fUvBaDEQSczAJQ.png)

![Image](https://cdn.prod.website-files.com/61c02e339c11997e6926e3d9/61c093a693fd42c2d52eb62a_602c569e5e6e7537bc35799a_TYU0FzP808wO7i21lCVLrwNQHDid7p-DEEKPX7y61O4Yqe17MWvMU4gVS6ZcSWYEz0jbwQ6LSCRv4rw5zsKH-6CBYn95EDvZ5Sh4BprrkBx821ylBC85xb710oIBfirSbxtjzFs.png)

* **Labels and Selectors (Concise)**

    * **Labels** are **key–value pairs** attached to Kubernetes objects.
    * Used to **organize, group, and identify** resources.

  ```yaml
  labels:
    app: payments
    env: prod
  ```

* **Selectors** are **queries** used to **select objects by labels**.

  ```yaml
  selector:
    app: payments
  ```

* **Example:** A Service uses selectors to find all Pods with `app=payments`.


> Labels tag objects; selectors choose objects based on those tags.
---
* [x] **What are namespaces and their use?**

![Image](https://cdn.prod.website-files.com/6527fe8ad7301efb15574cc7/654cd76416fd0c35c9a3cf12_diagrams-V2-01-1024x576.png)

* **Namespaces in Kubernetes (Concise)**
    * **Namespaces** logically **isolate resources** within a cluster.
    * They help **organize, secure, and manage** large clusters.
    * Resources in different namespaces **do not conflict** by name.

* **Uses:**
    * Separate **environments** (dev, staging, prod)
    * Apply **RBAC, quotas, and limits** per team
    * Avoid resource name collisions

* **Example:**
  `payments` and `orders` namespaces can both have a `service-api` Service.

* **One-line summary:**

  > Namespaces provide logical isolation and access control inside a Kubernetes cluster.

---
## HELM
### Core Concepts
* [x] **What are Helm Charts?**

![Image](https://www.simplyblock.io/wp-content/media/Helam-chart.png?ver=70d5e0ceb27be4033da21fe5e99f47d42c58b68d)

* **Helm Charts** are **packages of Kubernetes manifests**.
* They define **what to deploy and how** using templates + values.
* Enable **reusable, versioned, and configurable** deployments.

* **Example:** One Helm chart can deploy the same app to **dev, staging, prod** using different `values.yaml`.

* **Chart contains:**

    * `Chart.yaml` → metadata
    * `templates/` → Kubernetes YAML templates
    * `values.yaml` → configurable values


> Helm Charts package Kubernetes resources into reusable, configurable deployments.
---

### Chart Structure
* [x] **Explain the structure of a Helm Chart (Chart.yaml, values.yaml, templates/)**

![Image](https://razorops.com/images/blog/helm-3-tree.png)

1) `Chart.yaml`
    * Contains **metadata** about the chart.
    * Name, version, appVersion, description, maintainers.

2) `values.yaml`

    * Holds **default configuration values**.
    * Can be overridden per environment.
    * Example:* image tag, replicas, resources.

3) `templates/`

    * Contains **Kubernetes YAML templates**.
    * Uses Go templating to inject values.
    * Example:* Deployment, Service, Ingress.


* How they work together

  ```
  values.yaml → templates/ → rendered Kubernetes YAML
  ```



> Chart.yaml = metadata, values.yaml = config, templates/ = manifests.
---

* [x] **What is values.yaml and how to use it?**

![Image](https://devopscube.com/content/images/2025/03/helm-template-1.png)


* `values.yaml` stores **default configuration values** for a Helm chart.
* These values are **injected into templates** at install/upgrade time.
* Same chart can be reused across environments by **overriding values**.

**Example (`values.yaml`):**

  ```yaml
  replicaCount: 3
  image:
    repository: my-app
    tag: "1.0.0"
  ```

**Template usage:**

  ```yaml
  replicas: {{ .Values.replicaCount }}
  ```

**Override values:**

  ```bash
  helm install app ./chart -f prod-values.yaml
  helm upgrade app ./chart --set replicaCount=5
  ```



> `values.yaml` makes Helm charts configurable without changing templates.
--


* [x] **What are Helm templates and how do they work?**
    * **Helm templates** are parameterized Kubernetes manifest files that use the **Go templating engine** to generate valid YAML at install/upgrade time.
        1. **Templates** live in `templates/` (e.g., `deployment.yaml`).
        2. **Values** come from `values.yaml` (or `--set` / `-f custom.yaml`).
        3. **Rendering** happens when you run `helm install/upgrade`; placeholders are replaced with actual values.
        4. **Output** is standard Kubernetes YAML applied to the cluster.

    * Simple example

  **templates/deployment.yaml**

  ```yaml
  apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: {{ .Release.Name }}
  spec:
    replicas: {{ .Values.replicaCount }}
    template:
      spec:
        containers:
          - name: app
            image: {{ .Values.image.repository }}:{{ .Values.image.tag }}
  ```

  **values.yaml**

  ```yaml
  replicaCount: 2
  image:
    repository: nginx
    tag: "1.25"
  ```

  **Result after rendering**

  ```yaml
  name: my-app
  replicas: 2
  image: nginx:1.25
  ```

    * Key points (interview-ready)

    * Enables **reusability** and **environment-specific configs**.
    * Uses objects like `.Values`, `.Release`, `.Chart`.
    * Supports logic: `if`, `range`, `default`, and template helpers (`_helpers.tpl`).

---
### Commands & Operations
* [x] **Common Helm commands (install, upgrade, rollback, uninstall, list)**
    * Below are the **most common Helm commands**, with short explanations and examples.



* 1. `helm install`

Installs a chart as a new release.

```bash
helm install my-app ./my-chart
```

Example: Creates a release named `my-app` in the cluster.


* 2. `helm upgrade`

Upgrades an existing release (or installs if not present with `--install`).

```bash
helm upgrade my-app ./my-chart
helm upgrade --install my-app ./my-chart
```

Example: Used during new version deployment.


* 3. `helm rollback`

Reverts a release to a previous revision.

```bash
helm rollback my-app 2
```

Example: Roll back `my-app` to revision `2` after a failed upgrade.



* 4. `helm uninstall`

Removes a release and its Kubernetes resources.

```bash
helm uninstall my-app
```

Example: Deletes all objects created by the release.


* 5. `helm list`

Lists Helm releases.

```bash
helm list
helm list -A
```

Example: `-A` shows releases across all namespaces.


* Interview summary (1-liner)

    * **install** → deploy new app
    * **upgrade** → update app
    * **rollback** → revert version
    * **uninstall** → remove app
    * **list** → view releases

---  
* [x] **How to manage multiple environments with Helm?**
    * Managing **multiple environments (dev, qa, prod)** with Helm is mainly done using **separate values files** and controlled releases.

        1. Use environment-specific `values` files (recommended)

  **Structure**

  ```text
  my-chart/
    values.yaml          # default (safe baseline)
    values-dev.yaml
    values-qa.yaml
    values-prod.yaml
  ```

  **Example**

  `values-dev.yaml`

  ```yaml
  replicaCount: 1
  resources:
    limits:
      cpu: "500m"
  ```

  `values-prod.yaml`

  ```yaml
  replicaCount: 5
  resources:
    limits:
      cpu: "2"
  ```

  **Deploy**

  ```bash
  helm install my-app-dev ./my-chart -f values-dev.yaml
  helm install my-app-prod ./my-chart -f values-prod.yaml
  ```

    2. Use separate namespaces per environment

  ```bash
  kubectl create ns dev
  kubectl create ns prod
  
  helm install my-app ./my-chart -n dev
  helm install my-app ./my-chart -n prod
  ```

  **Why**: Strong isolation and easier rollback.

    3. Override values at runtime (small changes only)

  ```bash
  helm upgrade my-app ./my-chart \
    --set image.tag=1.2.3 \
    -f values-prod.yaml
  ```

  Use this mainly in **CI/CD pipelines**.

    4. Environment-aware templates (minimal logic)

  ```yaml
  replicas: {{ .Values.replicaCount | default 1 }}
  ```

  Avoid heavy `if-else` logic; prefer values files.



* Interview-ready summary:
    * Use **one chart**
    * Maintain **multiple values files**
    * Deploy to **separate namespaces**
    * Use `--set` only for minor overrides

---