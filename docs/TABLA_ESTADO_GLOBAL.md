# Tabla de Estado Global — UserService

## Estado de Todas las Funcionalidades

| # | Funcionalidad | Estado | Ubicación | Deuda/Notas | Prioridad |
|---|---|---|---|---|---|
| 1 | Registro de usuario (`/auth/register`) | ✅ Implementado | `AuthController:32`, `UserService:40` | Sin validaciones de formato | MEDIA |
| 2 | Login con JWT (`/auth/login`) | ✅ Implementado | `AuthController:23`, `UserService:28` | Algoritmo HS256 (debe migrar a RS256) | 🔴 CRÍTICA |
| 3 | Listar usuarios (`GET /user`) | ✅ Implementado | `UserController:28`, `UserService:55` | Solo usernames de activos | BAJA |
| 4 | Perfil por ID (`GET /user/{id}`) | ⚠️ Implementado | `UserController:47` | Validación JWT local (a eliminar) | 🔴 CRÍTICA |
| 5 | Actualizar perfil (`PUT /user`) | ⚠️ Implementado | `UserController:33`, `UserService:72` | Validación JWT en 2 lugares | 🔴 CRÍTICA |
| 6 | Eliminar usuario (`DELETE /user`) | ⚠️ Implementado | `UserController:55`, `UserService:91` | Soft delete sin timestamp | MEDIA |
| 7 | Buscar usuarios (`GET /user/search`) | ⚠️ Implementado | `UserController:64`, `UserService:60` | Sin paginación, validación JWT | MEDIA |
| D1 | **Eliminar validación JWT local** | ❌ PENDIENTE | 6 ubicaciones (ver 02-DEUDAS_TECNICAS.md) | Bloqueador de arquitectura | 🔴 CRÍTICA |
| D2 | **Firma JWT con RSA (RS256)** | ⚠️ Parcial | `certs/private.pem` existe, no conectado | `private.pem` listo, falta integrar | 🔴 CRÍTICA |
| D3 | **OAuth2 Google/Facebook** | ❌ No implementado | — | Cero código, cero dependencias | MEDIA |
| D4 | **Colección Postman JSON** | ❌ No existe | — | Facilita testing manual | MEDIA |
| D5 | **`softDeleteUser()` completo** | ⚠️ Parcial | `UserService:91` | Falta timestamp, auditoría | MEDIA |
| D6 | **`reactivateUser()` admin** | ⚠️ Borrador | `UserService:105` (`ActiveUser`)| Mal diseñado, requiere password | MEDIA |
| D7 | **`changePassword()`** | ❌ No implementado | `UserService:159 (TODO)` | Funcionalidad crítica de seguridad | ALTA |
| 8 | `myProfile()` (tu propio perfil) | ❌ No implementado | `UserService:116 (TODO)` | — | BAJA |
| 9 | Rate limiting de login | ❌ No implementado | `UserService:130 (TODO)` | — | BAJA |
| 10 | `ChangeRol()` cambiar rol | ❌ No implementado | `UserService:135 (TODO)` | — | BAJA |
| 11 | `resetPassword()` restablecer | ❌ No implementado | `UserService:153 (TODO)` | — | BAJA |
| 12 | `updateAvatar()` avatar | ❌ No implementado | `UserService:147 (TODO)` | — | BAJA |
| 13 | `changeEmail()` cambiar email | ❌ No implementado | `UserService:165 (TODO)` | — | BAJA |
| 14 | `changeUsername()` cambiar username | ❌ No implementado | `UserService:171 (TODO)` | — | BAJA |
| 15 | Paginación en búsqueda | ❌ No implementado | `UserController:62 (TODO)` | — | BAJA |
| 16 | Manejo de errores global | ❌ No implementado | `UserService:127 (TODO)` | Se lanza `RuntimeException` cruda | MEDIA |
| 17 | Validaciones de input | ❌ No implementado | — | No hay `@Valid`, confianza en BD | MEDIA |
| 18 | Documentación OpenAPI/Swagger | ❌ No implementado | — | Facilita integración de otros servicios | BAJA |

---

## Leyenda

| Símbolo | Significado |
|---------|------------|
| ✅ | Implementado y funciona correctamente |
| ⚠️ | Implementado pero con deudas/problemas |
| ❌ | No implementado o debe ser eliminado |
| 🔴 CRÍTICA | Bloquea progreso de otras tareas |
| ALTA | Debe hacerse esta semana |
| MEDIA | Debe hacerse en 2-3 semanas |
| BAJA | Puede hacerse después, no es urgente |
| D#N | Deuda técnica #N |

---

## Resumen de Conteos

```
TOTAL FUNCIONALIDADES:           18
✅ IMPLEMENTADAS CORRECTAMENTE:   3  (17%)
⚠️ IMPLEMENTADAS CON DEUDAS:      5  (28%)
❌ NO IMPLEMENTADAS:             10  (55%)

DEUDAS CRÍTICAS (🔴):             2  (D1, D2)
DEUDAS ALTAS:                     1  (D7)
DEUDAS MEDIANAS:                  5  (D3, D4, D5, D6, otras)
DEUDAS BAJAS:                     7  (myProfile, rate limiting, etc.)
```

---

## Mapa de Dependencias (Orden de Implementación)

```
SEMANA 1:
  D1 (Eliminar JWT local) ← BLOQUEA TODO
  D2 (RS256) ← BLOQUEA D1
        ↓
SEMANA 2:
  D7 (changePassword)
  D5 (softDeleteUser mejorado)
  D6 (reactivateUser)
        ↓
SEMANA 3-4:
  D3 (OAuth2)
  D4 (Postman)
        ↓
SEMANA 4+:
  Items bajos (myProfile, rate limiting, etc.)
```

---

## Por Archivo

### `JwtUtil.java`
| Funcionalidad | Estado | Deuda |
|---|---|---|
| `generateToken()` | ✅ Existe | D2: Cambiar de HS256 a RS256 |
| `validateToken()` | ✅ Existe | D1: Debe eliminarse |

### `JwtAuthenticationFilter.java`
| Funcionalidad | Estado | Deuda |
|---|---|---|
| Clase completa | ✅ Existe | D1: Eliminar clase entera |

### `SecurityConfig.java`
| Funcionalidad | Estado | Deuda |
|---|---|---|
| Registro de JwtAuthenticationFilter | ✅ Existe | D1: Eliminar línea 35 |

### `UserController.java`
| Funcionalidad | Estado | Deuda |
|---|---|---|
| `getAllUsers()` | ✅ OK | — |
| `updateUser()` | ⚠️ Existe | D1: Eliminar validación local L.38 |
| `getUser()` | ⚠️ Existe | D1: Eliminar validación local L.50 |
| `deleteUser()` | ✅ Existe | D5: Mejorar manejo de errores |
| `getSearchUser()` | ⚠️ Existe | D1: Eliminar validación en service, D4: Agregar paginación |

### `UserService.java`
| Funcionalidad | Estado | Deuda |
|---|---|---|
| `getAuthenticatedUser()` | ✅ OK | D2: Migrar a RS256 |
| `registerUser()` | ⚠️ OK | Validaciones de entrada |
| `userProfile()` (2 sobrecargas) | ✅ OK | — |
| `getUsersNames()` | ✅ OK | — |
| `searchUsers()` | ⚠️ Existe | D1: Eliminar validación L.62 |
| `updateUser()` | ⚠️ Existe | D1: Eliminar validación L.74 |
| `softDeleteUser()` | ⚠️ Existe | D1: Eliminar validación L.93, D5: Agregar timestamp |
| `ActiveUser()` | ⚠️ Existe | D6: Reemplazar por `reactivateUser()` |
| `changePassword()` | ❌ No existe | D7: Implementar |
| `myProfile()` | ❌ TODO | — |
| `ChangeRol()` | ❌ TODO | — |
| `resetPassword()` | ❌ TODO | — |
| `updateAvatar()` | ❌ TODO | — |
| `changeEmail()` | ❌ TODO | — |
| `changeUsername()` | ❌ TODO | — |

### `UserModel.java`
| Campo | Estado | Deuda |
|---|---|---|
| `id`, `username`, `password`, `email`, `role`, `deleted` | ✅ Existen | Agregar: `deletedAt`, `createdAt`, `updatedAt`, campos OAuth2 |

### `UserRepository.java`
| Método | Estado | Deuda |
|---|---|---|
| `findByUsername()` | ✅ Existe | — |
| `findById()` | ✅ Existe | — |
| `searchUsers()` | ✅ Existe | Agregar paginación |
| `findByDeletedFalse()` | ✅ Existe | — |

### `AuthController.java`
| Endpoint | Estado | Deuda |
|---|---|---|
| `POST /auth/login` | ✅ OK | D2: Cambiar algoritmo JWT |
| `POST /auth/register` | ⚠️ OK | Recibe entidad en lugar de DTO |

### DTOs
| DTO | Estado | Deuda |
|---|---|---|
| `UserDto` | ✅ OK | — |
| `UpdateUserDto` | ✅ OK | Separar en UpdateEmailDto, UpdateUsernameDto |

---

## Plan de Cierres por Semana

### Semana 1 (D1 + D2)
- [x] Integrar `private.pem` en JwtUtil → RS256
- [x] Generar `public.pem` y distribuir a API Gateway
- [x] Actualizar `application.properties`
- [x] Tests de RS256
- [x] Coordinar con API Gateway
- [x] Eliminar `JwtAuthenticationFilter.java`
- [x] Eliminar validación en `SecurityConfig`
- [x] Refactorizar 5 métodos (eliminar validación local)
- [x] Actualizar tests
- **Horas:** 8-10

### Semana 2 (D5, D6, D7)
- [x] Corregir `softDeleteUser()` → agregar `deletedAt`, auditoría
- [x] Implementar `reactivateUser()` (admin, sin password)
- [x] Implementar `changePassword()` endpoint
- [x] Tests de nuevas funcionalidades
- **Horas:** 5-7

### Semana 3-4 (D3, D4, 8+)
- [x] OAuth2 flujo completo (Google + Facebook)
- [x] Generar Postman collection
- [x] Implementar items bajos (myProfile, rate limiting, etc.)
- **Horas:** 19-24

---

**Documento:** TABLA_ESTADO_GLOBAL.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
