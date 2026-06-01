# Endpoints — Documentación Completa

## Tabla General de Endpoints

| Método | Ruta | Autenticación Actual | Cambios Planeados | Estado |
|--------|------|---|---|---|
| `POST` | `/auth/login` | ❌ Pública | Migrar firma a RS256 | ✅ |
| `POST` | `/auth/register` | ❌ Pública | Recibir DTO en lugar de entidad | ⚠️ |
| `GET` | `/user` | ⚠️ JWT (a eliminar) | Eliminar validación local | ⚠️ |
| `GET` | `/user/{id}` | ⚠️ JWT (a eliminar) | Eliminar validación en L.50 | ⚠️ |
| `PUT` | `/user` | ⚠️ JWT (a eliminar) | Eliminar validaciones en L.38 y L.74 | ⚠️ |
| `DELETE` | `/user` | ⚠️ JWT (a eliminar) | Eliminar validación en L.93 | ⚠️ |
| `GET` | `/user/search` | ⚠️ JWT (a eliminar) | Eliminar validación en L.62, agregar paginación | ⚠️ |

---

## Endpoints — Detalles por Sección

### 🔐 AUTH — Autenticación

#### `POST /auth/login`

**Descripción:** Autentica un usuario y retorna un JWT.

**Autenticación:** ❌ No requerida (pública)

**Controller:** `AuthController.java:23`  
**Service:** `UserService.java:28` — `getAuthenticatedUser()`

**Request:**
```json
{
  "username": "juan123",
  "password": "miContraseña"
}
```

**Response exitosa (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqdWFuMTIzIiwiaWF0IjoxNzQ4MDAwMDAwLCJleHAiOjE3NDgwODY0MDB9...."
}
```

**Response error (401):**
```
"Credenciales inválidas"
```

**Problemas identificados:**
- ⚠️ Respuesta de error es String plano, no JSON estructurado
- ⚠️ El token usa HS256 (será migrado a RS256 en Deuda D2)
- ❌ Sin validación de intentos fallidos (TODO comentado en L.130-133)

**Cambios planeados:**
1. Migrar firma JWT a RS256 (Deuda D2)
2. Estructurar respuesta de error como JSON
3. Agregar rate limiting de intentos fallidos

---

#### `POST /auth/register`

**Descripción:** Registra un nuevo usuario.

**Autenticación:** ❌ No requerida (pública)

**Controller:** `AuthController.java:32`  
**Service:** `UserService.java:40` — `registerUser()`

**Request:** Se recibe la entidad `UserModel` completa (deuda de diseño):
```json
{
  "username": "juan123",
  "password": "miContraseña",
  "email": "juan@example.com",
  "role": "USER"
}
```

**Response exitosa (200):**
```json
{
  "id": 1,
  "username": "juan123",
  "password": "$2a$10$...",
  "email": "juan@example.com",
  "role": "USER",
  "deleted": false
}
```

**Response error:**
- Si `username` ya existe: `500` (SQL constraint, no manejado)
- Si `email` ya existe: `500` (SQL constraint, no manejado)

**Problemas identificados:**
- ❌ La response incluye el hash de la contraseña (vulnerabilidad de información)
- ❌ La response incluye el campo `deleted` (innecesario)
- ❌ Sin validación de formato (email inválido, username muy corto, password débil)
- ❌ Recibe entidad en lugar de DTO
- ❌ Errors de constraint no son manejados explícitamente
- ⚠️ Debería retornar 201 Created, no 200 OK

**Cambios planeados:**
1. Crear `RegisterRequestDto` con campos: username, password, confirmPassword, email
2. Crear `RegisterResponseDto` con solo: id, username, email
3. Agregar validaciones: `@Valid`, `@NotBlank`, `@Email`, `@Size`
4. Manejar excepciones de uniqueness → retornar 409 Conflict
5. Retornar 201 Created

---

### 👥 USERS — Gestión de Usuarios

#### `GET /user`

**Descripción:** Lista los usernames de todos los usuarios activos (no eliminados).

**Autenticación:** ⚠️ Requiere JWT vía filtro (a eliminar en Deuda D1)

**Controller:** `UserController.java:28` — `getAllUsers()`  
**Service:** `UserService.java:55` — `getUsersNames()`

**Response exitosa (200):**
```json
[
  "juan123",
  "maria456",
  "pedro789"
]
```

**Notas:**
- ✅ Usa `findByDeletedFalse()` — excluye correctamente usuarios eliminados
- ⚠️ Sin paginación (todos los usuarios en una única respuesta)
- ⚠️ Sin búsqueda (usar `/user/search` para eso)

---

#### `GET /user/{id}`

**Descripción:** Retorna el perfil de un usuario por su ID.

**Autenticación:** ⚠️ Requiere JWT. Validación REDUNDANTE en:
- JwtAuthenticationFilter (a eliminar — D1)
- UserController L.50 (a eliminar — D1)

**Controller:** `UserController.java:47` — `getUser()`  
**Service:** `UserService.java:50` — `userProfile(Long)`

**Path variable:** `id` (Long)

**Header requerido actualmente:** `Authorization: Bearer <token>`

**Response exitosa (200):**
```json
{
  "id": 1,
  "username": "juan123",
  "email": "juan@example.com"
}
```

**Response error:**
- ❌ Si ID no existe: No hay manejo, probablemente `500` o `null`

**Cambios planeados:**
1. Eliminar validación JWT redundante en controller (Deuda D1)
2. Agregar manejo explícito de `userProfile()` retornando Optional
3. Retornar 404 si usuario no existe

---

#### `PUT /user`

**Descripción:** Actualiza los datos (username y/o email) del usuario autenticado.

**Autenticación:** ⚠️ Requiere JWT. Validación REDUNDANTE en:
- JwtAuthenticationFilter (a eliminar — D1)
- UserController L.38 (a eliminar — D1)
- UserService L.74 (a eliminar — D1)

**Controller:** `UserController.java:33` — `updateUser()`  
**Service:** `UserService.java:72` — `updateUser()`

**Header requerido actualmente:** `Authorization: Bearer <token>`

**Request:**
```json
{
  "username": "nuevoUsername",
  "email": "nuevo@email.com"
}
```

Ambos campos son opcionales. Si se pasa `null` o string vacío, el campo no se actualiza.

**Response exitosa (200):**
```
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**(Retorna un nuevo JWT como string plano)**

**Problemas identificados:**
- ❌ Respuesta es string plano (debe ser JSON estructurado)
- ⚠️ Si solo se actualiza email → genera nuevo token innecesariamente
- ❌ Si nuevo username ya existe → SQL constraint, no manejado
- ❌ Si nuevo email ya existe → SQL constraint, no manejado
- ❌ Sin validación de entrada

**Cambios planeados:**
1. Eliminar triple validación (Deuda D1)
2. Crear `UpdateUserResponse` con estructura clara
3. Manejar constraint violations → 409 Conflict
4. Separar en dos endpoints: `PUT /user/email` y `PUT /user/username`

---

#### `DELETE /user`

**Descripción:** Realiza soft delete del usuario autenticado (marca `deleted=true`).

**Autenticación:** ⚠️ Requiere JWT. Validación en:
- JwtAuthenticationFilter (a eliminar — D1)
- UserService L.93 (a eliminar — D1)

**Controller:** `UserController.java:55` — `deleteUser()`  
**Service:** `UserService.java:91` — `softDeleteUser()`

**Header requerido actualmente:** `Authorization: Bearer <token>`

**Response exitosa (200):**
```json
{
  "id": 1,
  "username": "juan123",
  "email": "juan@example.com"
}
```

**Problemas identificados:**
- ❌ Retorna `Optional` directamente, si está vacío → `.get()` lanza excepción
- ⚠️ Sin `deletedAt` timestamp (Deuda D5)
- ⚠️ Sin auditoría de quién eliminó
- ❌ Sin validación si usuario ya está eliminado
- ⚠️ Respuesta confusa (retorna datos del usuario eliminado)

**Cambios planeados:**
1. Eliminar validación en service (Deuda D1)
2. Mejorar `softDeleteUser()` (Deuda D5)
3. Retornar respuesta clara de confirmación

---

#### `GET /user/search`

**Descripción:** Busca usuarios activos por username y/o email (búsqueda parcial, case-insensitive).

**Autenticación:** ⚠️ Requiere JWT. Validación en:
- JwtAuthenticationFilter (a eliminar — D1)
- UserService L.62 (a eliminar — D1)

**Controller:** `UserController.java:64` — `getSearchUser()`  
**Service:** `UserService.java:60` — `searchUsers()`

**Header requerido actualmente:** `Authorization: Bearer <token>`

**Query params:**
- `username` (String, opcional) — búsqueda parcial LIKE
- `email` (String, opcional) — búsqueda parcial LIKE

**Response exitosa (200):**
```json
[
  {
    "id": 1,
    "username": "juan123",
    "email": "juan@example.com"
  },
  {
    "id": 2,
    "username": "juanita",
    "email": "juanita@example.com"
  }
]
```

**Problemas identificados:**
- ❌ Sin paginación (puede retornar miles de resultados)
- ❌ Sin validación de parámetros (search con strings vacíos)
- ⚠️ Usa `Stream` en lugar de `List` (deuda menor)

**Cambios planeados:**
1. Eliminar validación en service (Deuda D1)
2. Agregar paginación: `?page=0&size=10`
3. Agregar ordenamiento: `?sort=username,asc`
4. Validar que al menos un parámetro de búsqueda se proporcione

---

### 🔜 Endpoints Futuros (Por Implementar)

Estos endpoints están planificados pero no existen aún:

#### `POST /user/{id}/change-password`

Cambiar contraseña. Ver Deuda D7 y `07-HOJA_RUTA.md` para especificación.

#### `POST /user/{id}/reactivate`

Reactivar usuario eliminado (admin solo). Ver Deuda D6 y `07-HOJA_RUTA.md` para especificación.

#### `GET /auth/oauth/{provider}/authorize`

Obtener URL de autorización OAuth. Ver Deuda D3 y `07-HOJA_RUTA.md` para especificación.

#### `POST /auth/oauth/callback`

Callback de OAuth. Ver Deuda D3 y `07-HOJA_RUTA.md` para especificación.

---

## Patrones de Error Actual

Los errores actualmente se manejan de forma inconsistente:

| Situación | Response Actual | Debería ser |
|-----------|---|---|
| Credenciales inválidas | `"Credenciales inválidas"` (String) | `{"error": "Invalid credentials", "code": "AUTH_001"}` (JSON) |
| Usuario no encontrado | `RuntimeException` → 500 | `{"error": "User not found", "code": "USER_NOT_FOUND"}` → 404 |
| Username duplicado | `SQLIntegrityConstraintViolationException` → 500 | `{"error": "Username already exists", "code": "USERNAME_DUPLICATE"}` → 409 |
| Email duplicado | `SQLIntegrityConstraintViolationException` → 500 | `{"error": "Email already exists", "code": "EMAIL_DUPLICATE"}` → 409 |
| Token inválido | (Depende del contexto) | `{"error": "Invalid or expired token", "code": "JWT_INVALID"}` → 401 |
| Sin autenticación | (Depende del filtro) | `{"error": "Authentication required", "code": "AUTH_REQUIRED"}` → 401 |
| Sin permiso | No hay validación de roles | `{"error": "Access denied", "code": "FORBIDDEN"}` → 403 |

**Recomendación:** Implementar `@ControllerAdvice` global para centralizar el manejo de excepciones.

---

**Documento:** 03-ENDPOINTS.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
