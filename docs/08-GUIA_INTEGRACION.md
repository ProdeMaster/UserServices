# Guía de Integración para Otros Servicios

## ⚠️ Estado Actual vs Futuro

Esta sección describe cómo integrar con UserService durante el período de transición.

### Timeline

```
AHORA (Actual):        HS256 + JWT validado localmente en UserService
SEMANA 1-2 (Futuro):   RS256 + JWT validado solo en API Gateway
SEMANA 3+ (Futuro):    + OAuth2 Google/Facebook
```

---

## Estado Actual (MIENTRAS se implementan los cambios)

### Flujo de Autenticación Actual

**Paso 1:** Tu servicio obtiene JWT de UserService

```bash
POST /auth/login HTTP/1.1
Host: userservice:8081
Content-Type: application/json

{
  "username": "juan123",
  "password": "miContraseña"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Paso 2:** Tu servicio usa el JWT en requests posteriores

```bash
GET /user/1 HTTP/1.1
Host: userservice:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Endpoints Disponibles Actualmente

| Método | Ruta | Requiere JWT | Descripción |
|--------|------|---|---|
| `POST` | `/auth/login` | ❌ No | Obtener JWT |
| `POST` | `/auth/register` | ❌ No | Registrar usuario |
| `GET` | `/user` | ✅ Sí | Listar usuarios |
| `GET` | `/user/{id}` | ✅ Sí | Perfil por ID |
| `PUT` | `/user` | ✅ Sí | Actualizar perfil propio |
| `DELETE` | `/user` | ✅ Sí | Eliminar usuario propio |
| `GET` | `/user/search` | ✅ Sí | Buscar usuarios |

### Token JWT — Estructura Actual

**Algoritmo:** HS256 (HMAC256 con secret simétrico)

**Payload:**
```json
{
  "sub": "juan123",
  "iat": 1748000000,
  "exp": 1748086400
}
```

**Expiración:** 24 horas (86400000ms)

**Formato:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqdWFuMTIzIiwiaWF0IjoxNzQ4MDAwMDAwLCJleHAiOjE3NDgwODY0MDB9.HMAC256_SIGNATURE`

### Headers Requeridos

```
Authorization: Bearer <token>
```

**Nota:** El header debe incluir el prefijo "Bearer " antes del token.

### Códigos HTTP

| Código | Significado | Ejemplo |
|--------|------------|---------|
| 200 | Operación exitosa | GET /user/1 |
| 400 | Credenciales inválidas | POST /auth/login con password incorrecto |
| 404 | Usuario no encontrado | GET /user/999 |
| 500 | Error interno (no manejado) | Constraint violation sin try-catch |

### Limitaciones Actuales a Recordar

- ❌ **No hay validación de entrada:** Acepta cualquier input
- ❌ **No hay rate limiting:** Puedes hacer 1000 intentos de login por segundo
- ❌ **No hay refresh token:** Si el token expira, necesitas hacer login de nuevo
- ❌ **Sin OAuth2:** No se puede usar Google/Facebook
- ❌ **Sin changePassword:** Los usuarios no pueden cambiar contraseña
- ⚠️ **Validación JWT local:** UserService valida el token; esto se eliminará próximamente

---

## Estado Futuro (Post-Semana 2)

### Cambios Principales

**1. Algoritmo RS256 (post Semana 1)**
- Mismo formato de token
- Cambio transparente para consumidores
- API Gateway necesita actualizar

**2. Eliminación de validación JWT local (post Semana 1-2)**
- API Gateway es responsable de validar
- UserService confia en headers inyectados por API Gateway
- Los servicios deben pasar a través de API Gateway (no hablar directo con UserService)

**3. Nuevos endpoints (post Semana 2)**
- `POST /user/{id}/change-password` — cambiar contraseña
- `POST /user/{id}/reactivate` — reactivar usuario (admin)

**4. OAuth2 (post Semana 3-4)**
- `GET /auth/oauth/{provider}/authorize` — obtener URL de OAuth
- `POST /auth/oauth/callback` — procesar callback
- `POST /auth/oauth/refresh` — refrescar token OAuth

### Flujo Futuro (A través de API Gateway)

```
┌─────────────────┐
│  Tu Servicio    │
│   (Consumer)    │
└────────┬────────┘
         │
         │ REQUESTS (con JWT)
         ↓
┌─────────────────────────┐
│   API GATEWAY           │
│ (Valida JWT, inyecta    │
│  usuario en header)     │
└────────┬────────────────┘
         │
         │ REQUESTS (con X-Authenticated-User)
         ↓
┌─────────────────────────┐
│   UserService           │
│ (Confia en API Gateway) │
└─────────────────────────┘
```

### Endpoints Futuro

**Nuevos:**
```
POST /user/{id}/change-password
POST /user/{id}/reactivate (admin)
GET  /auth/oauth/{provider}/authorize
POST /auth/oauth/callback
```

**Existentes (sin cambios en interfaz):**
```
POST /auth/login
POST /auth/register
GET  /user
GET  /user/{id}
PUT  /user
DELETE /user
GET  /user/search
```

---

## Periodo de Transición — Compatibilidad Hacia Atrás

### Que NO Cambiará

- Endpoints públicos (`/auth/login`, `/auth/register`)
- Endpoints de CRUD (`GET /user`, `GET /user/{id}`, `PUT /user`, `DELETE /user`)
- Formato del JWT (mismo token, distinto algoritmo de firma)

### Que SÍ Cambiará

- **Punto crítico:** Donde se valida el JWT
  - Antes: UserService (local)
  - Después: API Gateway
  - **Impacto:** Los servicios DEBEN estar detrás de API Gateway

- **Punto crítico:** Token expiración
  - Posible cambio de 24h a 1h (post-Semana 1)
  - Si cambias, necesitarás implementar refresh token

### Plan de Comunicación Recomendado

**Semana 1:**
- "Migramos JWT a RS256 (transparente para consumidores)"
- "ALERTA: Validación de JWT se trasladará a API Gateway en Semana 2"

**Semana 1-2:**
- "Validación de JWT ahora ocurre en API Gateway"
- "Todo debe estar detrás de API Gateway"

**Semana 3-4:**
- "Nuevos endpoints disponibles (changePassword, reactivateUser, OAuth2)"

---

## Recomendaciones para Consumidores

### ✅ HACER

1. **Pasar a través de API Gateway**
   ```
   ✅ Tu Servicio → API Gateway → UserService
   ❌ Tu Servicio → UserService (directo)
   ```

2. **Manejar tokens JWT correctamente**
   ```java
   // ✅ Almacenar en sessionStorage (web)
   sessionStorage.setItem('token', response.token);
   
   // ❌ Almacenar en localStorage (menos seguro para XSS)
   ```

3. **Incluir Authorization header**
   ```java
   // ✅ CORRECTO
   headers.put("Authorization", "Bearer " + token);
   
   // ❌ INCORRECTO
   headers.put("Authorization", token);
   ```

4. **Manejar 401 (token expirado)**
   ```java
   // ✅ CORRECTO
   if (response.status == 401) {
     // Redirigir a login
     window.location.href = '/login';
   }
   ```

5. **Usar UTC para timestamps**
   ```java
   // ✅ CORRECTO (Los tokens usan Unix timestamps en UTC)
   long nowSeconds = System.currentTimeMillis() / 1000;
   ```

### ❌ NO HACER

1. **Decodificar el JWT en el cliente para confiar en datos**
   ```java
   // ❌ INCORRECTO (el cliente no puede verificar firma)
   String payload = decodeJWT(token);
   String username = parseJSON(payload).get("sub");
   ```

2. **Validar JWT localmente sin verificar firma**
   ```java
   // ❌ INCORRECTO (cualquiera puede generar un JWT falso)
   if (JWT.decode(token).exp > now) { /* trust it */ }
   ```

3. **Compartir el secret HMAC con otros servicios**
   - El secret es simétrico (post-RS256 ya no hay secret)
   - Quien tenga el secret puede firmar tokens falsos

4. **Asumir que `/user/{id}` retorna datos completos del usuario**
   - Solo retorna: `id`, `username`, `email`
   - No retorna: `password`, `role`, `deleted`, `createdAt`

5. **Mantener tokens por más tiempo del necesario**
   ```java
   // ❌ INCORRECTO (token expira después de 24h)
   // No hay refresh token aún, así que si expira, necesitas login de nuevo
   ```

---

## Ejemplos de Integración

### Ejemplo 1: Login desde otro servicio (Node.js)

```javascript
// 1. Hacer login
const loginResponse = await fetch('http://api-gateway/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'juan123',
    password: 'miContraseña'
  })
});

const { token } = await loginResponse.json();

// 2. Guardar token
sessionStorage.setItem('authToken', token);

// 3. Usar en requests posteriores
const userResponse = await fetch('http://api-gateway/user/1', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const user = await userResponse.json();
console.log(user); // { id: 1, username: 'juan123', email: 'juan@example.com' }
```

### Ejemplo 2: Registrar usuario (Python)

```python
import requests
import json

# 1. Registrar usuario
register_data = {
  'username': 'maria456',
  'password': 'muestrapassword123',
  'email': 'maria@example.com',
  'role': 'USER'
}

response = requests.post(
  'http://api-gateway/auth/register',
  json=register_data
)

if response.status_code == 200 or response.status_code == 201:
  user = response.json()
  print(f"Usuario creado: {user['id']} - {user['username']}")
else:
  print(f"Error: {response.status_code}")
  print(response.text)
```

### Ejemplo 3: Buscar usuarios (Java)

```java
// 1. Obtener token (login previo)
String token = loginAndGetToken("juan123", "miContraseña");

// 2. Buscar usuarios
HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(token);

RestTemplate restTemplate = new RestTemplate();
HttpEntity<?> entity = new HttpEntity<>(headers);

ResponseEntity<UserDto[]> response = restTemplate.exchange(
  "http://api-gateway/user/search?username=ma&email=",
  HttpMethod.GET,
  entity,
  UserDto[].class
);

List<UserDto> users = Arrays.asList(response.getBody());
users.forEach(user -> System.out.println(user.getUsername()));
```

---

## Manejo de Errores

### 401 Unauthorized (Token inválido o expirado)

**Causa:** Token expiró (24 horas) o es inválido

**Acción recomendada:**
```javascript
if (response.status === 401) {
  // Limpiar token local
  sessionStorage.removeItem('authToken');
  
  // Redirigir a login
  window.location.href = '/login';
}
```

### 404 Not Found

**Causa:** Usuario solicitado no existe

**Respuesta actual:** Inconsistente (a veces `500`)

**Acción recomendada:**
```javascript
if (response.status === 404) {
  console.log("Usuario no encontrado");
  // Mostrar mensaje al usuario
}
```

### 409 Conflict

**Causa:** Username o email ya existe (después de que sea manejado explícitamente)

**Acción recomendada:**
```javascript
if (response.status === 409) {
  const error = await response.json();
  console.log(error.error);  // "Username already exists"
}
```

### 500 Internal Server Error

**Causa:** Error no manejado (constraint violation, null pointer, etc.)

**Acción recomendada:**
```javascript
if (response.status === 500) {
  console.error("Error interno del servidor");
  // Notificar al usuario, luego contactar al equipo de soporte
}
```

---

## Preguntas Frecuentes (FAQ)

### P: ¿Puedo validar el JWT en mi servicio sin API Gateway?

**R:** Actualmente sí (UserService valida localmente). Post-Semana 2, deberías estar detrás de API Gateway. Si lo haces directamente, necesitarás actualizar tu código cuando se implemente RS256.

### P: ¿Puedo hacer requests directos a UserService sin pasar por API Gateway?

**R:** Actualmente sí. Post-Semana 2, DEBERÍAS pasar por API Gateway (requisito para obtener el header `X-Authenticated-User`).

### P: ¿Dónde debo almacenar el JWT en mi aplicación web?

**R:** `sessionStorage` (se borra al cerrar la pestaña) es más seguro que `localStorage` (persiste hasta eliminarlo manualmente). NO almacenes en cookies sin `HttpOnly` flag.

### P: ¿Cuál es la expiración del token?

**R:** Actualmente 24 horas. Post-Semana 1, posible cambio a 1 hora (será comunicado).

### P: ¿Hay refresh token?

**R:** No (actualmente). Se implementará post-Semana 2.

### P: ¿Puedo usar UserService sin autenticación?

**R:** Sí, para:
- `POST /auth/login`
- `POST /auth/register`

No, para todo lo demás.

### P: ¿Puedo cambiar la contraseña de otro usuario?

**R:** No. Ni siquiera los admins actualmente (Deuda: será implementado post-Semana 2 como `changePassword` de usuario propio).

### P: ¿Qué pasa si hago 1000 login requests por segundo?

**R:** Actualmente nada (no hay rate limiting). Se implementará post-Semana 2.

---

**Documento:** 08-GUIA_INTEGRACION.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
