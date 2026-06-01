# Apéndices — Referencia Técnica

## Apéndice A — Estructura del JWT Actual (HS256)

### Anatomía del Token

Un JWT consta de 3 partes separadas por `.`:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqdWFuMTIzIiwiaWF0IjoxNzQ4MDAwMDAwLCJleHAiOjE3NDgwODY0MDB9.SIGNATURE
│──────────────────────────────────────│ │──────────────────────────────────────────────────────────────│ │────────────│
                  Header                                         Payload                                    Signature
```

### Header (Base64Url decodificado)

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

### Payload (Base64Url decodificado)

```json
{
  "sub": "juan123",
  "iat": 1748000000,
  "exp": 1748086400
}
```

**Campos:**
- `sub` (Subject): Username del usuario
- `iat` (Issued At): Timestamp Unix de cuándo se emitió
- `exp` (Expires At): Timestamp Unix de cuándo expira

### Signature

Calculado como:
```
HMAC256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret_key
)
```

El `secret_key` actual es: `MI-CLAVE-SUPER-SEGURA`

### Cómo Decodificar Manualmente

```javascript
// En el navegador (dev console)
const token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
const parts = token.split('.');

const header = JSON.parse(atob(parts[0]));
const payload = JSON.parse(atob(parts[1]));

console.log(header);  // { alg: "HS256", typ: "JWT" }
console.log(payload); // { sub: "juan123", iat: ..., exp: ... }
```

### ⚠️ Nota de Seguridad

Decodificar un JWT NO verifica la firma. Cualquiera puede decodificar y leer el payload. La validez del token depende de verificar la **firma**, que requiere el `secret` (HS256) o `public_key` (RS256).

---

## Apéndice B — Generación de Certificados RSA

### Generar Nuevo Par de Claves (Si se compromete la actual)

```bash
# 1. Generar private key RSA 2048 bits (formato PKCS#8)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out src/main/resources/certs/private.pem

# 2. Extraer public key desde private key
openssl rsa -in src/main/resources/certs/private.pem \
  -pubout -out src/main/resources/certs/public.pem

# 3. Verificar integridad de private key
openssl rsa -in src/main/resources/certs/private.pem -check

# 4. Ver contenido de public key (para auditoría)
cat src/main/resources/certs/public.pem
```

### Estructura de Private Key (PKCS#8)

```
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDLnYgYGYcwJ/JP
aoQhg7DO5sPApolnh4k/JV/kfMaEpS4d5/upudHKBKUvkxguWj3C4HUZk/CqPOs8
...
(base64-encoded key data)
...
RSEJH2RFAgMBAAECggEAG7r9JXcYk8Tk+c7iZuQWCN+hylOEBB8P4JM4/5fEu5z5
-----END PRIVATE KEY-----
```

**Formato:** PKCS#8 (compatible con Java/Spring Boot)

### Estructura de Public Key (PKCS#1 con headers)

```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQIjANBgkqhkiG9w0BAQEFAAOCAQIjANBgkqh
...
(base64-encoded key data)
...
-----END PUBLIC KEY-----
```

---

## Apéndice C — Configuración OAuth2 (Futura — Deuda D3)

### Archivo: `application-oauth2.properties`

```properties
# Google OAuth2
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=email,profile
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/auth/oauth/callback
spring.security.oauth2.client.provider.google.authorization-uri=https://accounts.google.com/o/oauth2/v2/auth
spring.security.oauth2.client.provider.google.token-uri=https://www.googleapis.com/oauth2/v4/token
spring.security.oauth2.client.provider.google.user-info-uri=https://www.googleapis.com/oauth2/v1/userinfo

# Facebook OAuth2
spring.security.oauth2.client.registration.facebook.client-id=${FACEBOOK_CLIENT_ID}
spring.security.oauth2.client.registration.facebook.client-secret=${FACEBOOK_CLIENT_SECRET}
spring.security.oauth2.client.registration.facebook.scope=email,public_profile
spring.security.oauth2.client.registration.facebook.redirect-uri={baseUrl}/auth/oauth/callback
spring.security.oauth2.client.provider.facebook.authorization-uri=https://www.facebook.com/v12.0/dialog/oauth
spring.security.oauth2.client.provider.facebook.token-uri=https://graph.facebook.com/v12.0/oauth/access_token
spring.security.oauth2.client.provider.facebook.user-info-uri=https://graph.instagram.com/me?fields=id,name,email
```

### Variables de Entorno Requeridas

```bash
export GOOGLE_CLIENT_ID="your-google-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"

export FACEBOOK_CLIENT_ID="your-facebook-app-id"
export FACEBOOK_CLIENT_SECRET="your-facebook-app-secret"
```

---

## Apéndice D — Colección Postman (JSON)

### Plantilla Básica

Crear archivo: `src/postman/UserService.postman_collection.json`

```json
{
  "info": {
    "name": "UserService - ProdeMaster",
    "description": "API documentation for UserService microservice",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8081",
      "type": "string"
    },
    {
      "key": "access_token",
      "value": "",
      "type": "string"
    },
    {
      "key": "user_id",
      "value": "1",
      "type": "string"
    }
  ],
  "item": [
    {
      "name": "Auth",
      "item": [
        {
          "name": "POST Login",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function() {",
                  "  pm.response.to.have.status(200);",
                  "});",
                  "pm.test('Token exists', function() {",
                  "  var jsonData = pm.response.json();",
                  "  pm.expect(jsonData.token).to.exist;",
                  "  pm.environment.set('access_token', jsonData.token);",
                  "});"
                ]
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\"username\":\"testuser\",\"password\":\"password123\"}"
            },
            "url": {
              "raw": "{{base_url}}/auth/login",
              "host": ["{{base_url}}"],
              "path": ["auth", "login"]
            }
          },
          "response": []
        },
        {
          "name": "POST Register",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200 or 201', function() {",
                  "  pm.response.to.have.status(200 || 201);",
                  "});",
                  "pm.test('User object has id', function() {",
                  "  var jsonData = pm.response.json();",
                  "  pm.expect(jsonData.id).to.exist;",
                  "});"
                ]
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\"username\":\"newuser\",\"password\":\"password123\",\"email\":\"user@example.com\",\"role\":\"USER\"}"
            },
            "url": {
              "raw": "{{base_url}}/auth/register",
              "host": ["{{base_url}}"],
              "path": ["auth", "register"]
            }
          },
          "response": []
        }
      ]
    },
    {
      "name": "Users",
      "item": [
        {
          "name": "GET All Users",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{access_token}}"
              }
            ],
            "url": {
              "raw": "{{base_url}}/user",
              "host": ["{{base_url}}"],
              "path": ["user"]
            }
          }
        },
        {
          "name": "GET User by ID",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{access_token}}"
              }
            ],
            "url": {
              "raw": "{{base_url}}/user/{{user_id}}",
              "host": ["{{base_url}}"],
              "path": ["user", "{{user_id}}"]
            }
          }
        },
        {
          "name": "PUT Update User",
          "request": {
            "method": "PUT",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              },
              {
                "key": "Authorization",
                "value": "Bearer {{access_token}}"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\"username\":\"updateduser\",\"email\":\"updated@example.com\"}"
            },
            "url": {
              "raw": "{{base_url}}/user",
              "host": ["{{base_url}}"],
              "path": ["user"]
            }
          }
        },
        {
          "name": "DELETE User",
          "request": {
            "method": "DELETE",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{access_token}}"
              }
            ],
            "url": {
              "raw": "{{base_url}}/user",
              "host": ["{{base_url}}"],
              "path": ["user"]
            }
          }
        },
        {
          "name": "GET Search Users",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{access_token}}"
              }
            ],
            "url": {
              "raw": "{{base_url}}/user/search?username=test&email=",
              "host": ["{{base_url}}"],
              "path": ["user", "search"],
              "query": [
                {
                  "key": "username",
                  "value": "test"
                },
                {
                  "key": "email",
                  "value": ""
                }
              ]
            }
          }
        }
      ]
    }
  ]
}
```

---

## Apéndice E — Códigos de Error HTTP

| Código | Significado | Caso de Uso |
|--------|------------|-----------|
| **200** | OK | GET, PUT exitosos |
| **201** | Created | POST exitoso (debería ser, actualmente retorna 200) |
| **204** | No Content | DELETE exitoso (sin respuesta) |
| **400** | Bad Request | Validación fallida, request inválido |
| **401** | Unauthorized | Token inválido, expirado, o no proporcionado |
| **403** | Forbidden | Autenticado pero sin permiso para la acción |
| **404** | Not Found | Recurso no existe (usuario no encontrado) |
| **409** | Conflict | Recurso ya existe (username/email duplicado) |
| **500** | Internal Server Error | Error no capturado en el servidor |

### Respuestas de Error Propuestas

```json
// 401 - Token inválido
{
  "error": "Invalid or expired token",
  "code": "JWT_INVALID",
  "timestamp": "2026-05-22T10:30:00Z"
}

// 404 - Usuario no encontrado
{
  "error": "User not found",
  "code": "USER_NOT_FOUND",
  "userId": 999,
  "timestamp": "2026-05-22T10:30:00Z"
}

// 409 - Username duplicado
{
  "error": "Username already exists",
  "code": "USERNAME_DUPLICATE",
  "field": "username",
  "value": "juan123",
  "timestamp": "2026-05-22T10:30:00Z"
}

// 500 - Error interno
{
  "error": "Internal server error",
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "timestamp": "2026-05-22T10:30:00Z"
}
```

---

## Apéndice F — Versiones de Dependencias

### Spring Boot & Java

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.3</version>
</parent>

<properties>
  <java.version>17</java.version>
  <spring-cloud.version>2024.0.0</spring-cloud.version>
</properties>
```

### Librerías Clave

| Librería | Versión | Uso |
|----------|---------|-----|
| `spring-boot-starter-security` | 3.4.3 | Seguridad |
| `spring-boot-starter-web` | 3.4.3 | REST API |
| `spring-boot-starter-data-jpa` | 3.4.3 | ORM |
| `spring-cloud-starter-netflix-eureka-client` | 2024.0.0 | Service Discovery |
| `java-jwt` | 4.4.0 | JWT |
| `postgresql` | (runtime) | BD |

### Agregar para RS256 (Post-Semana 1)

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk15on</artifactId>
  <version>1.70</version>
</dependency>
```

### Agregar para OAuth2 (Post-Semana 3)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

---

## Apéndice G — Variables de Entorno

### Actual

```bash
POSTGRES_HOST=postgres-user
POSTGRES_PORT=5432
POSTGRES_DB=user_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=admin
```

### Futuro (Post-Semana 1)

```bash
POSTGRES_HOST=postgres-user
POSTGRES_PORT=5432
POSTGRES_DB=user_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=admin

# JWT (en producción, no comitear private.pem)
JWT_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----...-----END PRIVATE KEY-----
JWT_ALGORITHM=RS256
JWT_EXPIRATION=3600000
```

### Futuro con OAuth2 (Post-Semana 3)

```bash
GOOGLE_CLIENT_ID=your-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-secret
FACEBOOK_CLIENT_ID=your-id
FACEBOOK_CLIENT_SECRET=your-secret
```

---

**Documento:** APENDICES.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
