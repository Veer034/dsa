## OAUTH2 & KEYCLOAK
### OAuth2 Core Concepts
* [x] **What is OAuth2?**
    * OAuth 2.0 is an authorization framework that allows third-party applications to access user resources without sharing passwords.
    *  It works by issuing access tokens from an authorization server after user consent.
    *  The client uses these tokens to securely access protected APIs with limited scope and time.
---
* [x] **Difference between authentication and authorization**
    * **Authentication** verifies **who you are** (identity).
        * **Example**: Logging in with username and password.

    * **Authorization** verifies **what you are allowed to do** (permissions).
        * **Example**: Checking if a user can access admin APIs.

  **Order:** Authentication happens first, then authorization.
---
* [x] **What are OAuth2 grant types you've used? (Authorization Code, Client Credentials, Refresh Token)**
    * **Authorization Code Grant**: Used for user-based login (e.g., web apps). User authenticates, and the client exchanges the code for an access token.
    * **Client Credentials Grant**: Used for service-to-service communication. No user involved; the client authenticates using client ID and secret.
    * **Refresh Token Grant**: Used to obtain a new access token without re-authenticating the user when the access token expires.
---
* [x] **When to use which grant type?**

    * **Authorization Code Grant**
      Use when a **user is involved** (web/mobile apps).
      *Example:* User logs in via Google to access profile data.

    * **Client Credentials Grant**
      Use for **service-to-service** communication.
      *Example:* Microservice A calling Microservice B securely.

    * **Refresh Token Grant**
      Use when you need **long-lived sessions** without re-login.
      *Example:* Refresh access token silently after expiry.

  **Rule of thumb:**
  User context → Authorization Code
  Machine-to-machine → Client Credentials
---
### Tokens
* [x] **What is Access Token vs Refresh Token?**

    * **Access Token**: Short-lived token used to **access protected APIs**.
        * **Example**: Sent in `Authorization: Bearer <token>` header.

    * **Refresh Token**: Long-lived token used to **get a new access token** without re-login.
        * **Example**: Used when access token expires.

  **Key Difference:**
  Access token = API access
  Refresh token = token renewal
---
* [x] **What is JWT structure? (Header, Payload, Signature)**
    * A **JWT (JSON Web Token)** has **three parts**, separated by dots (`.`):

    1. **Header** – Token type and signing algorithm
       *Example:* `{ "alg": "HS256", "typ": "JWT" }`

    2. **Payload** – Claims (user data, roles, expiry)
       *Example:* `{ "sub": "123", "role": "ADMIN", "exp": 1700000000 }`

    3. **Signature** – Ensures token **integrity and authenticity**
       *Created using header + payload + secret/private key*

  **Format:**
  `header.payload.signature`
---
* [x] **How to validate JWT?**

    1. **Verify signature** using the secret key or public key (ensures token is not tampered).
    2. **Check expiry (`exp`)** to ensure the token is not expired.
    3. **Validate claims** like issuer (`iss`), audience (`aud`), and scopes/roles.
    4. **Authorize request** based on roles or permissions in the payload.

  **Example:**
  If signature is valid and `exp` is not passed → token is accepted.
---
* [x] **What is token introspection?**
    * Token introspection is a process where a **resource server calls the authorization server** to **verify a
      token’s validity**.
      It checks whether the token is **active**, not expired, and retrieves metadata like scopes or user info.
      Used mainly with **opaque tokens** (not self-contained like JWTs).

### OAuth2 Implementation
* [x] **How to store tokens securely?**

    * **Access Tokens**: Store in **memory** (backend) or short-lived cache; never persist in DB or localStorage.
        * **Example**: Keep in server session or API gateway context.

    * **Refresh Tokens**: Store **encrypted in database or secure vault** with strict access control.
        * **Example**: Encrypt using KMS and rotate on reuse.

    * **Client-side**: If needed, use **HTTP-only, Secure cookies** (never localStorage).
---
* [x] **You mentioned AES-256 encryption and SHA-256 hashing - explain your approach**
    * **AES-256 Encryption:** A symmetric encryption algorithm that uses a 256-bit key to encrypt and decrypt data. The same key is used for both operations, making it fast and efficient. It processes data in 128-bit blocks through multiple rounds of substitution and permutation, providing strong confidentiality for sensitive data like passwords and files.

    * **SHA-256 Hashing:** A one-way cryptographic hash function that converts any input into a fixed 256-bit (32-byte)
      output called a hash or digest. It's deterministic (same input always produces same hash) but irreversible - you cannot recover the original data from the hash. Commonly used for password storage, data integrity verification, and digital signatures, as even tiny input changes produce completely different hashes.
---

* [x] **Where did you store tokens? (Azure Key Vault)**
    * I stored authentication tokens and API keys in Azure Key Vault, which provides hardware-level encryption (HSM-backed) and centralized secret management. The application authenticates to Key Vault using Managed Identity, eliminating hardcoded credentials in code. Tokens are retrieved at runtime through the Key Vault SDK with proper access policies and RBAC controls. Key Vault also provides audit logging, automatic secret rotation capabilities, and versioning for all secrets. This approach ensures tokens are never exposed in source code, configuration files, or environment variables, following security best practices and compliance requirements.
### Keycloak Basics
* [x] **What is Keycloak and why did you use it?**
    * **What is Keycloak:** Keycloak is an open-source Identity and Access Management (IAM) solution that provides centralized authentication and authorization services. It supports industry-standard protocols like OAuth 2.0, OpenID Connect, and SAML 2.0 for secure single sign-on (SSO) across multiple applications."
    * **Why I used it:** I used Keycloak to implement centralized user authentication and role-based access control (RBAC) across our microservices architecture. It eliminated the need to build custom authentication logic in each service, providing features like SSO, multi-factor authentication, user federation with LDAP/Active Directory, and social login integration out of the box. Keycloak issues JWT tokens that our services validate for stateless authentication, and its admin console simplified user management and role assignments. This approach improved security, reduced development time, and provided a scalable identity solution that could integrate with existing enterprise systems."
    * Key benefits to highlight:
        * Centralized authentication (SSO)
        * Standards-based (OAuth 2.0, OIDC, SAML)
        * JWT token-based authentication
        * Built-in MFA and social login
        * User federation capabilities
        * Reduced custom authentication code
---
* [x] **What is a Realm in Keycloak?**
    * A Realm in Keycloak is an isolated administrative domain that manages a complete set of users, credentials, roles, and groups. Think of it as a tenant or workspace where you configure authentication and authorization for a specific set of applications. Each realm has its own configuration, including login themes, authentication flows, clients (applications), and identity providers
* [x] **How did you register microservices as clients in Keycloak?**
    * I registered each microservice as a client in Keycloak through the Admin Console under the respective realm. For backend microservices, I created clients with 'confidential' access type and enabled 'Service Accounts' for machine-to-machine communication. Each client was assigned a unique Client ID and Client Secret, which the microservice uses to authenticate with Keycloak and obtain access tokens
* [x] **What are client types in Keycloak? (confidential, public, bearer-only)**

    * **1. Confidential Clients:**
      "Used for backend services and server-side applications that can securely store credentials. They require a Client ID and Client Secret for authentication. Examples include REST APIs, microservices, and server-side web applications. These clients can use both authorization code flow and client credentials grant for obtaining tokens."

    * **2. Public Clients:**
      "Used for frontend applications like SPAs (React, Angular), mobile apps, or desktop applications that cannot securely store secrets since their code is exposed to end-users. They use Client ID only without a secret and typically rely on PKCE (Proof Key for Code Exchange) for security. Authentication happens through browser redirects with authorization code flow."

    * **3. Bearer-Only Clients:**
      "Used exclusively for resource servers or APIs that only validate tokens but never initiate login flows themselves. They don't request tokens from Keycloak - they only verify incoming JWT bearer tokens sent by other clients. Common for pure backend APIs that serve authenticated requests but don't handle user login directly."

    * **Practical Usage:**
        - Confidential: Microservices, backend APIs
        - Public: React/Angular frontends, mobile apps
        - Bearer-only: Stateless REST APIs validating tokens
---
* [x] **Difference between realm roles and client roles?**
    * **Realm Roles:** Realm roles are global roles defined at the realm level and are available across all clients
      within that realm. They represent general user permissions like 'admin', 'user', or 'manager' that apply organization-wide. When a user is assigned a realm role, it's included in their token and can be used by any application in the realm for authorization decisions.

    * **Client Roles:** Client roles are specific to individual clients (applications/microservices) and provide
      application-level permissions. For example, an 'order-service' client might have roles like 'view-orders' or 'create-orders' that only apply to that specific service. These roles are namespaced under the client and aren't automatically available to other applications.

    * **Practical Example:** In my project, I used realm roles for broad organizational permissions like 'ADMIN',
      'EMPLOYEE', and 'CUSTOMER'. For the order-service microservice, I created client-specific roles like 'order-manager' and 'order-viewer' that provided granular access control for that service's endpoints. This allowed me to have both global authorization (realm roles) and fine-grained, service-specific permissions (client roles) in a single authentication token.

    * **Key Difference:**
        - Realm roles: Global, shared across all applications
        - Client roles: Application-specific, isolated to one client
### Service Account Tokens
* [x] **What are service account tokens?**
    * Service account tokens are access tokens obtained through the OAuth 2.0 client credentials grant flow, used for machine-to-machine (M2M) authentication without user involvement. When you enable 'Service Accounts' for a confidential client in Keycloak, that client can authenticate using its Client ID and Client Secret to obtain tokens on behalf of itself, not a human user.
---
* [x] **How did microservices authenticate with each other using service account tokens?**
    * Each microservice was registered as a confidential client in Keycloak with Service Accounts enabled. When Service A needed to call Service B, it first requested a service account token from Keycloak's token endpoint using the client credentials grant - sending its Client ID and Client Secret. Keycloak validated the credentials and returned a JWT access token with the service's roles and permissions
### JWT Token Handling
* [x] Why did you implement custom JWT decoder instead of using Spring Security's default?
    * **Reasons for Custom JWT Decoder:** I implemented a custom JWT decoder to handle specific requirements that
      Spring Security's default decoder didn't fully address. Our application needed to extract and map Keycloak-specific claims like realm roles and client roles into Spring Security's GrantedAuthority format in a custom way. The default decoder didn't automatically convert Keycloak's nested JSON structure for roles (realm_access.roles and resource_access.<client>.roles) into the format our application expected.

    * **Additional Customizations:** The custom decoder also allowed me to implement additional validation logic
      beyond standard JWT validation - such as checking custom claims, handling token refresh logic, and adding specific business validations. I needed to extract user metadata from the token and populate our application's UserContext with information like tenant ID, department, or custom attributes that weren't handled by the default converter. Additionally, it provided better error handling and logging for token validation failures, making debugging authentication issues easier.

    * **Key Customizations:**
        - Custom role extraction from Keycloak's nested structure
        - Mapping to Spring Security GrantedAuthority
        - Additional claim validations
        - Custom user context population
        - Enhanced error handling and logging
        - Support for multi-tenancy or custom attributes
---

Based on your code, here are interview questions and answers about your custom authorization implementation:

---

* [x] **Why did you create a custom authorization filter instead of using Spring Security's default?**

    * I implemented a custom `CustomAuthorizationFilter` as a `WebFilter` in Spring WebFlux to handle specific business
      requirements that Spring Security's default authorization didn't address. Our application needed multi-tenant validation where we had to verify that the `tenantId` and `agentId` from the JWT token matched the path variables in the URL. Additionally, we had custom authorization logic based on user privileges and subscription plans that required custom annotations."

    * **Key requirements:**
        - Multi-tenant path variable validation (tenantId/agentId in URL must match JWT claims)
        - Custom `@CompositeCustomAuthorization` annotation support for privilege and plan-based access control
        - Service account bypass logic for machine-to-machine communication
        - Specific excluded paths that don't require authentication
        - Better error handling with detailed JSON error responses

---

* [x] **Explain your CustomAuthorizationManager and how it works?**

    * I implemented `CustomAuthorizationManager` which implements Spring's `ReactiveAuthorizationManager` to provide
      fine-grained, annotation-based authorization. It works in conjunction with custom annotations placed on controller methods."

    * When a request comes in, the manager first validates JWT expiration. For service accounts (identified by
      `client_id` claim), it grants immediate access. For user requests, it uses `AnnotationMapping` to find the matching controller method and extracts the `@CompositeCustomAuthorization` annotation. The annotation contains required privileges and plan levels. The manager then checks if the user's JWT contains the necessary privileges and whether their subscription plan meets the minimum required level using a hierarchical plan comparison (e.g., PREMIUM >= BASIC)."

    * **Key validation logic:**
        - JWT expiration check
        - Service account bypass
        - Privilege matching (user must have at least one required privilege)
        - Plan level validation using `Plan.isAtLeast()` method
        - Returns `AuthorizationDecision(true/false)` based on validation

---

* [x] **How does your AnnotationMapping work and why did you implement it?**

    * I created `AnnotationMapping` to dynamically map incoming HTTP requests to their corresponding controller methods
      and extract custom authorization annotations. During application startup (`@PostConstruct`), it scans all `RequestMappingInfoHandlerMapping` beans, finds methods annotated with `@CompositeCustomAuthorization`, and builds a sorted mapping of `HTTP_METHOD + URL_PATTERN` to annotation instances."

    * **Key features:** The mappings are sorted by specificity score—patterns with more static segments rank higher
      than those with path variables. This ensures that specific routes like `/v1/tenants/{tenantId}/agents/admin` match before generic routes like `/v1/tenants/{tenantId}/agents/{agentId}`. When a request arrives, it constructs the request key from HTTP method and path, then finds the most specific matching pattern using Spring's `PathPattern` matching, and returns the associated authorization annotation."

    * **Why needed:**
        - Dynamic annotation discovery without hardcoding routes
        - Proper pattern matching with path variables
        - Specificity-based resolution to avoid ambiguous matches

---

* [x] **Explain the flow: CustomAuthorizationFilter vs CustomAuthorizationManager?**

    * **These two components work together in a layered security approach:**

        * **CustomAuthorizationFilter (First Layer - WebFilter):**
            - Executes early in the filter chain for every request
            - Handles path-based exclusions (public endpoints, health checks)
            - Validates multi-tenant security: ensures `tenantId` and `agentId` from URL path variables match JWT claims
            - Bypasses validation for service accounts (client_id present)
            - Performs basic JWT authentication context checks
            - Returns 401/403 with detailed JSON errors if validation fails

        * **CustomAuthorizationManager (Second Layer - Authorization):**
            - Executes after filter, before controller method
            - Focuses on business-level authorization using custom annotations
            - Checks user privileges and subscription plan levels
            - Makes the final authorization decision based on `@CompositeCustomAuthorization` requirements

    * **Flow:** Request → CustomAuthorizationFilter (tenant/agent validation) → Spring Security Context →
      CustomAuthorizationManager (privilege/plan validation) → Controller Method

---

* [x]  **Why did you use WebFilter instead of OncePerRequestFilter in WebFlux?**

* In Spring WebFlux, we use `WebFilter` instead of `OncePerRequestFilter` because WebFlux is built on reactive,
  non-blocking principles using Project Reactor. `OncePerRequestFilter` is part of Spring MVC's servlet-based architecture and doesn't support reactive streams.

*  **Key differences:**
    - `WebFilter` returns `Mono<Void>` for non-blocking, asynchronous processing
    - Uses `ServerWebExchange` (reactive) instead of `HttpServletRequest/Response` (blocking)
    - Integrates with reactive security context: `ReactiveSecurityContextHolder.getContext()`
    - Supports backpressure and reactive stream composition


* **Implementation:** My `CustomAuthorizationFilter implements WebFilter` uses reactive operators like `Mono.defer()`,
  `flatMap()`, and `onErrorResume()` to handle security validation asynchronously without blocking threads.

---

* [x]   **How do you handle service account vs user authentication differently?**
* I differentiate between service accounts and user accounts by checking the `client_id` claim in the JWT token:"


* **Service Accounts (Machine-to-Machine):**
    - Have `client_id` claim present in JWT
    - Bypass tenant/agent validation in `CustomAuthorizationFilter`
    - Skip privilege and plan checks in `CustomAuthorizationManager`
    - Used for microservice-to-microservice communication

* **User Accounts:**
    - No `client_id` claim
    - Must pass tenant/agent path variable validation
    - Subject to privilege and plan-level authorization checks
    - Token contains `tenantId`, `agentId` (sub), `privileges`, and `plan` claims

* **Code check:**
  ```java
  String clientId = jwt.getClaimAsString("client_id");
  if (StringUtils.hasText(clientId)) {
      // Service account - skip validations
      return new AuthorizationDecision(true);
  }
  ```

---

* [x]  **How does your privilege and plan validation work?**
* **Privilege Validation:** Privileges are extracted from the JWT's `privileges` claim as a string list. If the
  annotation requires specific privileges, the user must have at least ONE of them (OR logic). If `*` is specified
  or no privileges are required, access is granted. This provides flexible, role-agnostic permission checks.

* **Plan Validation:** Plans are hierarchical subscription levels (e.g., FREE < BASIC < PREMIUM < ENTERPRISE). The
  JWT contains the  user's current plan, and the annotation specifies the minimum required plan. I use `Plan.isAtLeast(required)` to check if the user's plan meets or exceeds the requirement. For example, a PREMIUM user can access BASIC-level features, but a BASIC user cannot access PREMIUM features.


* **Composite Logic:** The `@CompositeCustomAuthorization` can contain multiple `@CustomAuthorization` entries
  with different privilege-plan combinations. Access is granted if ANY one combination is satisfied (OR across annotations, AND within each annotation).

---

* [x] **Why did you calculate specificity for pattern matching?**

    * Specificity calculation ensures that when multiple URL patterns could match a request, the most specific one is
      selected. For example, `/v1/agents/{agentId}/profile` and `/v1/agents/admin` both match `/v1/agents/admin`, but the second is more specific because it has no path variables."

    * **Calculation logic:** I assign weights to each segment, with higher weights for segments earlier in the path.
      Static segments (without `{}` braces) add to the specificity score, while path variable segments don't. Patterns are sorted in descending order of specificity, so exact matches are checked before generic patterns.

    * **Why important:** Prevents authorization bypass where a generic pattern's permissions might incorrectly apply to a
      more specific, restricted endpoint.
