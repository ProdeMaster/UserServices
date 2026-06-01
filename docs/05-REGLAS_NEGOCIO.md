# Reglas de Negocio

## Usuarios

### Creación de Usuario

| Regla | Estado | Validación | Ubicación |
|-------|--------|-----------|-----------|
| Username requerido | ✅ Sí | NO en código (BD constraint) | UserModel:13 |
| Username único | ✅ Sí | Constraint `UNIQUE` en BD | Handled by SQLException |
| Username longitud | ❌ No | Sin validación | — |
| Password requerido | ✅ Sí | NO en código (BD constraint) | UserModel:16 |
| Password hasheado con BCrypt | ✅ Sí | `BCryptPasswordEncoder.encode()` | `registerUser()` L.41 |
| Email requerido | ✅ Sí | NO en código (BD constraint) | UserModel:19 |
| Email único | ✅ Sí | Constraint `UNIQUE` en BD | Handled by SQLException |
| Email validado | ❌ No | Sin validación `@Email` | — |
| Rol por defecto | ✅ Sí | `@DefaultValue("USER")` | UserModel:23 |
| Rol permitido | ❌ No | Sin enum, sin validación | — |

### Modificación de Usuario

| Regla | Estado | Nota |
|-------|--------|------|
| Solo username y email se pueden cambiar | ✅ Sí | `UpdateUserDto` solo tiene esos 2 campos |
| No se puede cambiar password aquí | ✅ Sí | Hay endpoint separado planeado (changePassword — D7) |
| No se puede cambiar rol | ✅ Sí | No hay endpoint (solo TODO `ChangeRol`) |
| Al cambiar username → generar nuevo token | ✅ Sí | `updateUser()` L.84 |
| Al cambiar solo email → generar nuevo token | ⚠️ Sí pero innecesario | Se genera nuevo token aunque sea innecesario |
| Username nuevo debe ser único | ❌ No | Constraint en BD, no manejado explícitamente |
| Email nuevo debe ser único | ❌ No | Constraint en BD, no manejado explícitamente |

### Eliminación de Usuario

| Regla | Estado | Ubicación | Nota |
|-------|--------|-----------|------|
| Soft delete (no borrar datos) | ✅ Sí | `softDeleteUser()` L.96 | Marca `deleted=true` |
| Registrar timestamp de eliminación | ❌ No | — | Deuda D5 |
| Registrar quién eliminó | ❌ No | — | Deuda D5 |
| No se puede eliminar usuario ya eliminado | ❌ No | — | Deuda D5 |
| Usuario puede auto-eliminarse | ✅ Sí | `/user` DELETE endpoint | Buen diseño |
| Usuario NO puede eliminar a otro usuario | ✅ Sí | Token + método autenticado | Buen diseño |

### Reactivación de Usuario

| Regla | Estado | Ubicación | Nota |
|-------|--------|-----------|------|
| Método existe | ✅ Sí (parcialmente) | `ActiveUser()` L.105 | Deuda D6: mal diseñado |
| Solo admin puede reactivar | ❌ No | — | Requiere password usuario (no es admin-only) |
| Requiere autenticación | ✅ Sí | — | Pero no es admin-only |
| Limpiar `deleted_at` timestamp | ❌ No | — | Deuda D6 |
| Registrar quién reactivó | ❌ No | — | Deuda D6 |
| No reactivar usuario no-eliminado | ❌ No | — | Deuda D6 |

### Búsqueda y Listado

| Regla | Estado | Ubicación |
|-------|--------|-----------|
| Listar solo usuarios no-eliminados | ✅ Sí | `getUsersNames()` + `findByDeletedFalse()` |
| Búsqueda excluye usuarios eliminados | ✅ Sí | `searchUsers()` JPQL con `u.deleted = false` |
| Búsqueda por username (partial) | ✅ Sí | `searchUsers()` con `LIKE` |
| Búsqueda por email (partial) | ✅ Sí | `searchUsers()` con `LIKE` |
| Búsqueda case-insensitive | ✅ Sí | `LOWER(u.username) LIKE LOWER(...)` |
| Paginación en búsqueda | ❌ No | Retorna `List` sin límite |
| Ordenamiento en búsqueda | ❌ No | — |

---

## Contraseñas

### Almacenamiento

| Regla | Estado | Implementación |
|-------|--------|---|
| Password nunca se almacena en plain text | ✅ Sí | BCrypt hash en BD |
| Hash es irreversible | ✅ Sí | BCrypt properties |
| Hash incluye salt | ✅ Sí | BCrypt auto-genera salt |
| Mismo password → distinto hash (por salt) | ✅ Sí | BCrypt properties |

### Comparación

| Regla | Estado | Ubicación |
|-------|--------|-----------|
| Comparación usa BCrypt.matches() | ✅ Sí | `getAuthenticatedUser()` L.30 |
| Comparación es case-sensitive | ✅ Sí | BCrypt properties |
| Intento fallido NO retorna "password incorrecto" | ✅ Sí | Retorna "Credenciales inválidas" genérico |

### Cambio de Contraseña

| Regla | Estado | Nota |
|-------|--------|------|
| Endpoint `changePassword` | ❌ No | Deuda D7 |
| Requiere contraseña actual | ❌ No | Deuda D7 |
| Requiere nueva contraseña + confirmación | ❌ No | Deuda D7 |
| Nueva password != password actual | ❌ No | Deuda D7 |
| Validar política de complejidad | ❌ No | Deuda D7 |
| Hashear nueva password con BCrypt | ❌ No | Deuda D7 |
| Registrar cambio en auditoría | ❌ No | Deuda D7 |

### Reset de Contraseña

| Regla | Estado | Nota |
|-------|--------|------|
| Endpoint `resetPassword` | ❌ No | TODO comentado L.153-158 |
| Enviar email con link reset | ❌ No | Depende de servicio de email |
| Token de reset temporal | ❌ No | — |
| Expiración de token reset | ❌ No | — |
| Actualizar password sin verificar actual | ❌ No | — |

---

## Tokens JWT

### Generación

| Regla | Estado | Ubicación |
|-------|--------|-----------|
| Token generado en login | ✅ Sí | `getAuthenticatedUser()` L.31 |
| Token generado al cambiar username | ✅ Sí | `updateUser()` L.84 |
| Subject = username | ✅ Sí | `generateToken()` L.33 |
| Token incluye iat (issued at) | ✅ Sí | `generateToken()` L.34 |
| Token incluye exp (expires at) | ✅ Sí | `generateToken()` L.35 |
| Algoritmo = HS256 (débil) | ⚠️ Sí | Deuda D2: migrar a RS256 |

### Expiración

| Regla | Estado | Valor |
|-------|--------|-------|
| Token expira después de X tiempo | ✅ Sí | 86400000ms = 24 horas |
| Expiración es absolute (no renovada) | ✅ Sí | No hay sliding window |
| Refresh token | ❌ No | No está implementado |
| Renovación de token expirado | ❌ No | No hay endpoint |

### Validación

| Regla | Estado | Ubicación |
|-------|--------|-----------|
| Token validado en filtro JWT | ✅ Sí | `JwtAuthenticationFilter.doFilterInternal()` L.46 |
| Token validado en cada endpoint protegido | ⚠️ Sí | Validación redundante (Deuda D1) |
| Excepción si token inválido | ✅ Sí | Lanza `RuntimeException` |
| Excepción si token expirado | ✅ Sí | Lanza `RuntimeException` |
| Token renovación | ❌ No | Deuda: Implement refresh endpoint |

### Revocación/Blacklist

| Regla | Estado | Nota |
|-------|--------|------|
| Token puede ser revocado | ❌ No | No hay mecanismo |
| Blacklist de tokens | ❌ No | No hay tabla/cache |
| Logout invalida token | ❌ No | Sin endpoint logout |
| Logout borra token cliente | ❌ No | Responsabilidad del cliente |

---

## Autorización y Roles

### Roles

| Regla | Estado | Ubicación |
|-------|--------|-----------|
| User tiene rol | ✅ Sí | UserModel.role field |
| Rol por defecto es "USER" | ✅ Sí | `@DefaultValue("USER")` |
| Roles soportados | ⚠️ Sí | Sin enum definido; valores: "USER", "ADMIN" (asumido) |
| Validación de rol permitido | ❌ No | Sin `@Enumerated` |
| Cambio de rol | ❌ No | TODO comentado L.135-140 |

### Permisos por Endpoint

| Endpoint | Requiere Auth | Requiere Admin | Requiere Ownership |
|----------|---|---|---|
| POST /auth/login | ❌ No | ❌ No | N/A |
| POST /auth/register | ❌ No | ❌ No | N/A |
| GET /user | ✅ Sí | ❌ No | N/A |
| GET /user/{id} | ✅ Sí | ❌ No | ❌ No (puede consultar otro) |
| PUT /user | ✅ Sí | ❌ No | ✅ Sí (solo tu usuario) |
| DELETE /user | ✅ Sí | ❌ No | ✅ Sí (solo tu usuario) |
| POST /user/{id}/reactivate | ✅ Sí (Futuro) | ⚠️ SÍ (Futuro, a diseñar) | N/A |
| POST /user/{id}/change-password | ✅ Sí (Futuro) | ❌ No (Futuro) | ✅ Sí (Futuro) |

**Nota:** `/user/{id}` permite consultar datos de CUALQUIER usuario (sin ownership check). Esto puede ser intencional (directorio público) o ser una vulnerabilidad (Information Disclosure).

---

## Base de Datos

### Tabla `users`

| Columna | Tipo | Nullable | Unique | Default | Nota |
|---------|------|----------|--------|---------|------|
| `id` | BIGINT | NO | SÍ (PK) | AUTO | Auto-generado |
| `username` | VARCHAR | NO | SÍ | — | — |
| `password` | VARCHAR | NO | NO | — | BCrypt hash |
| `email` | VARCHAR | NO | SÍ | — | — |
| `role` | VARCHAR | NO | NO | "USER" | Sin ENUM |
| `deleted` | BOOLEAN | SÍ | NO | false | Soft delete flag |

**Campos FALTANTES:**
- `deleted_at` (TIMESTAMP) — Deuda D5
- `created_at` (TIMESTAMP) — Auditoría
- `updated_at` (TIMESTAMP) — Auditoría
- `oauth_provider` (VARCHAR) — Deuda D3
- `oauth_id` (VARCHAR) — Deuda D3
- `oauth_email` (VARCHAR) — Deuda D3

---

## Auditoría y Logging

### Estado Actual

| Acción | Log | Base Datos |
|--------|-----|-----------|
| Registro usuario | ✅ L.34 | ❌ No |
| Login | ✅ L.25 | ❌ No |
| Actualización usuario | ✅ L.39 | ❌ No |
| Eliminación usuario | ✅ L.58 | ❌ No |
| Búsqueda usuario | ✅ L.66 | ❌ No |

### Recomendaciones

1. Agregar tabla `audit_logs` para rastrear cambios:
   ```sql
   CREATE TABLE audit_logs (
     id BIGSERIAL PRIMARY KEY,
     action VARCHAR(50),
     user_id BIGINT,
     affected_user_id BIGINT,
     details JSONB,
     created_at TIMESTAMP DEFAULT NOW()
   );
   ```

2. Guardar: `deleted_at`, `created_at`, `updated_at` en tabla `users`

3. Registrar: quién hizo qué acción y cuándo

---

## Validaciones Faltantes

| Tipo | Requerido | Ubicación |
|------|-----------|-----------|
| Username: longitud mínima (3) | MEDIA | `@Size(min=3)` en RegisterRequestDto |
| Username: caracteres permitidos | BAJA | `@Pattern("[a-zA-Z0-9_]")` |
| Email: formato válido | ALTA | `@Email` en RegisterRequestDto |
| Password: longitud mínima (8) | ALTA | `@Size(min=8)` |
| Password: complejidad (uppercase + number) | MEDIA | `@ValidPassword` custom validator |
| Email: duplicate check explícito | MEDIA | Excepción handler para SQL constraint |
| Username: duplicate check explícito | MEDIA | Excepción handler para SQL constraint |

---

**Documento:** 05-REGLAS_NEGOCIO.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
