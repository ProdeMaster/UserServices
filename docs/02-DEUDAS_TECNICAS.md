# Deudas Técnicas — Análisis Detallado

## 1.1 ❌ ELIMINAR: Validación JWT Local {#deuda-1}

**Estado verificado:** ✅ EXISTE y debe eliminarse completamente.

La validación JWT está distribuida en **6 ubicaciones críticas**:

### Ubicación 1: `JwtAuthenticationFilter.java` — Eliminar clase completa

```
Clase: JwtAuthenticationFilter (implements OncePerRequestFilter)
Método clave: doFilterInternal() — L.39-54
Línea crítica L.46: String username = jwtUtil.validateToken(token);
```

Este filtro intercepta TODAS las requests autenticadas, llama a `jwtUtil.validateToken()` y establece el `SecurityContext`. Con API Gateway tomando esta responsabilidad, esta clase completa debe eliminarse.

**Impacto de eliminación:**
- Sin validación local de JWT
- Requisito: API Gateway debe validar e inyectar usuario (ej. header `X-User-Name`)
- Referencia en `SecurityConfig.java` debe eliminarse

---

### Ubicación 2: `SecurityConfig.java` — Eliminar registro del filtro

```java
// L.16 — Campo
private final JwtAuthenticationFilter jwtAuthenticationFilter;

// L.18-21 — Constructor
@Autowired
public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
  this.jwtAuthenticationFilter = jwtAuthenticationFilter;
}

// L.35 — Línea a eliminar
.addFilterBefore(jwtAuthenticationFilter, SessionManagementFilter.class)
```

Después de eliminar esta línea, Spring Security no tendrá un filtro JWT local. El `SecurityContext` debe poblarse mediante otro mecanismo (ej. header injector desde API Gateway).

---

### Ubicación 3: `UserController.java` — Validación redundante en `updateUser()`

```java
// L.33-34 — Método
@PutMapping("")
public ResponseEntity<?> updateUser(
    @RequestHeader("Authorization") String token,
    @RequestBody UpdateUserDto updateData) {
  
  // L.37-39 — Validación que debe eliminarse
  String newToken = userService.updateUser(token, updateData);
  String tokenVerify = token.substring(7);
  String username = jwtUtil.validateToken(tokenVerify);  // ❌ ELIMINAR
  
  // ...
}
```

**Cambios requeridos:**
- Eliminar parámetro `@RequestHeader("Authorization") String token`
- Obtener `username` del `SecurityContext` (inyectado por API Gateway)
- Cambiar firma de servicio: `updateUser(UpdateUserDto)` en lugar de `updateUser(String token, UpdateUserDto)`

---

### Ubicación 4: `UserController.java` — Validación redundante en `getUser()`

```java
// L.47-48 — Método
@GetMapping("/{id}")
public ResponseEntity<?> getUser(
    @RequestHeader("Authorization") String token,
    @PathVariable Long id) {
  
  // L.49-51 — Validación que debe eliminarse
  String tokenVerify = token.substring(7);
  String username = jwtUtil.validateToken(tokenVerify);  // ❌ ELIMINAR
  
  // ...
}
```

**Cambios requeridos:**
- Eliminar parámetro token
- Obtener `username` del `SecurityContext` si es necesario (nota: este endpoint no lo usa)
- Esta validación es COMPLETAMENTE REDUNDANTE — el endpoint no hace nada con el username

---

### Ubicación 5: `UserService.java` — Validación en `searchUsers()`

```java
// L.60 — Método
public Stream<UserDto> searchUsers(
    String token,  // ❌ ELIMINAR PARÁMETRO
    String username,
    String email) {
  
  // L.61-63 — Validación que debe eliminarse
  String tokenVerify = token.substring(7);
  String userName = jwtUtil.validateToken(tokenVerify);  // ❌ ELIMINAR
  
  // Resto del método usa userName...
}
```

**Cambios requeridos:**
- Cambiar firma: `searchUsers(String username, String searchUsername, String email)`
- Eliminar validación
- El `username` viene del SecurityContext (populado por API Gateway)

---

### Ubicación 6: `UserService.java` — Validación en `updateUser()`

```java
// L.72 — Método
public String updateUser(
    String token,  // ❌ ELIMINAR PARÁMETRO
    UpdateUserDto userData) {
  
  // L.73-74 — Validación que debe eliminarse
  String tokenVerify = token.substring(7);
  String username = jwtUtil.validateToken(tokenVerify);  // ❌ ELIMINAR
  
  // Resto del método usa username...
}
```

**Cambios requeridos:**
- Cambiar firma: `updateUser(String username, UpdateUserDto userData)`
- Eliminar validación
- El `username` viene como parámetro directo del controller

---

### Ubicación 7: `UserService.java` — Validación en `softDeleteUser()`

```java
// L.91 — Método
public Optional<UserDto> softDeleteUser(
    String token) {  // ❌ ELIMINAR PARÁMETRO
  
  // L.92-93 — Validación que debe eliminarse
  String tokenVerify = token.substring(7);
  String userName = jwtUtil.validateToken(tokenVerify);  // ❌ ELIMINAR
  
  // Resto del método usa userName...
}
```

**Cambios requeridos:**
- Cambiar firma: `softDeleteUser(String username)`
- Eliminar validación
- El `username` viene como parámetro directo del controller

---

### Ubicación 8: `JwtUtil.java` — Método `validateToken()` se vuelve huérfano

```java
// L.45-55 — Método que debe eliminarse
public String validateToken(String token) {
  try {
    JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secretKey))
            .build();
    DecodedJWT jwt = verifier.verify(token);
    return jwt.getSubject();
  } catch (JWTVerificationException e) {
    throw new RuntimeException("Token inválido o expirado", e);
  }
}
```

Una vez eliminados los 6 puntos anteriores, este método queda sin uso y puede eliminarse.

---

### Impacto Total de Eliminación

| Archivo | Cambios |
|---------|---------|
| `JwtAuthenticationFilter.java` | Eliminar clase entera (55 líneas) |
| `SecurityConfig.java` | Eliminar 4 líneas (16, 18-21, 35) |
| `UserController.java` | Eliminar 2 validaciones (L.37-39, 49-51) + cambiar 2 firmas |
| `UserService.java` | Eliminar 3 validaciones (L.61-63, 73-74, 92-93) + cambiar 3 firmas |
| `JwtUtil.java` | Eliminar método `validateToken()` (11 líneas) |

**Total:** 1 clase eliminada, 5 métodos refactorizados, ~25 líneas de código redundante eliminadas.

### Requisito Previo: Sincronización con API Gateway

⚠️ **CRÍTICO:** Antes de eliminar esta validación local, confirmar que:

1. API Gateway **ya está validando** tokens JWT
2. API Gateway **inyecta el usuario autenticado** en un header conocido (ej. `X-Authenticated-User: juan123`)
3. UserService tiene un filtro que **popula el SecurityContext** a partir de ese header

**Alternativa temporal:** Si API Gateway aún no está listo, implementar un filtro alternativo que lea el header de usuario en lugar del JWT directo.

---

## 1.2 ⚠️ MODIFICAR: Firma JWT con RSA (RS256) {#deuda-2}

**Estado verificado:** `private.pem` EXISTE — pero NO está siendo usado.

### Estado Actual

```
Archivo: src/main/resources/certs/private.pem
Formato: PKCS#8 (BEGIN PRIVATE KEY) ✅ Compatible con RS256
Tamaño: RSA 2048 bits ✅ Estándar mínimo recomendado
Estado de uso: ❌ DESCONECTADO — JwtUtil usa HMAC256 con String secret
```

### Código Actual de `JwtUtil.java`

```java
// L.20-21 — Lee secret de properties como String
public JwtUtil(@Value("${jwt.secret}") String secretKey,
               @Value("${jwt.expiration}") long expirationTime) {
  this.secretKey = secretKey;
  this.expirationTime = expirationTime;
}

// L.31-37 — Genera token con HMAC256
public String generateToken(String username) {
  return JWT.create()
          .withSubject(username)
          .withIssuedAt(new Date())
          .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
          .sign(Algorithm.HMAC256(secretKey));  // ❌ HMAC256 = Simétrico
}

// L.45-55 — Valida con HMAC256
public String validateToken(String token) {
  try {
    JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secretKey))
            .build();
    DecodedJWT jwt = verifier.verify(token);
    return jwt.getSubject();
  } catch (JWTVerificationException e) {
    throw new RuntimeException("Token inválido o expirado", e);
  }
}
```

### Problemas Identificados

1. **Secret inseguro en properties:**
   ```properties
   jwt.secret=MI-CLAVE-SUPER-SEGURA  # ❌ Hardcodeado, visible en repo
   ```
   Este secret es simétrico — cualquiera que lo tenga puede EMITIR tokens falsificados.

2. **Algoritmo simétrico (HS256):**
   - Misma clave para firmar Y verificar
   - Si se comparte con API Gateway, ese gateway podría emitir tokens falsos
   - Débil desde perspectiva de confianza

3. **No hay public key:**
   ```
   src/main/resources/certs/private.pem  ✅ EXISTE
   src/main/resources/certs/public.pem   ❌ NO EXISTE
   ```

4. **No hay lógica de lectura de PEM:**
   `JwtUtil.java` no tiene código para leer/parsear archivos `.pem`

### Cambios Requeridos — Paso a Paso

#### Paso 1: Generar `public.pem`

```bash
# Extraer public key desde la private key existente
openssl rsa -in src/main/resources/certs/private.pem \
  -pubout -out src/main/resources/certs/public.pem
```

Resultado: Nuevo archivo `public.pem` en `certs/`.

#### Paso 2: Actualizar `application.properties`

```properties
# ELIMINAR:
jwt.secret=MI-CLAVE-SUPER-SEGURA

# AGREGAR:
jwt.private-key-path=classpath:certs/private.pem
jwt.algorithm=RS256
jwt.expiration=86400000
```

#### Paso 3: Refactorizar `JwtUtil.java`

```java
@Component
public class JwtUtil {

  private final PrivateKey privateKey;
  private final long expirationTime;

  public JwtUtil(@Value("${jwt.private-key-path}") Resource privateKeyResource,
                 @Value("${jwt.expiration}") long expirationTime) throws Exception {
    this.expirationTime = expirationTime;
    
    // Leer y parsear el archivo PEM
    String privateKeyContent = new String(
        FileCopyUtils.copyToByteArray(privateKeyResource.getInputStream())
    );
    privateKeyContent = privateKeyContent
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    
    byte[] decodedKey = Base64.getDecoder().decode(privateKeyContent);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    this.privateKey = (PrivateKey) keyFactory.generatePrivate(
        new PKCS8EncodedKeySpec(decodedKey)
    );
  }

  public String generateToken(String username) {
    return JWT.create()
            .withSubject(username)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
            .sign(Algorithm.RSA256(null, privateKey));  // ✅ RS256
  }

  // validateToken() será eliminado en Deuda D1
  // Pero temporalmente (antes de D1), usar public key para validar:
  public String validateToken(String token) throws Exception {
    // Leer public key
    PublicKey publicKey = /* lógica para parsear public.pem */;
    JWTVerifier verifier = JWT.require(Algorithm.RSA256(publicKey, null))
            .build();
    DecodedJWT jwt = verifier.verify(token);
    return jwt.getSubject();
  }
}
```

#### Paso 4: Dependencias (si falta)

En `pom.xml`, se necesita `bouncycastle` para parsear PEM correctamente:

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk15on</artifactId>
  <version>1.70</version>
</dependency>
```

#### Paso 5: Actualizar Tests

```java
@Test
public void testGenerateTokenWithRS256() {
  String token = jwtUtil.generateToken("testuser");
  
  // Decodificar sin validación para inspeccionar
  DecodedJWT decoded = JWT.decode(token);
  assertEquals("RS256", decoded.getAlgorithm());  // ✅
}
```

#### Paso 6: Distribuir `public.pem` al API Gateway

Después de generar, compartir `public.pem` con el equipo de API Gateway para que puedan validar tokens sin tener acceso a la private key.

**Opciones de distribución:**
- Comitear `public.pem` en el repo (es pública, segura para compartir)
- Crear endpoint `GET /auth/public-key` que retorne la pública
- Distribuir vía pipeline de CI/CD como artefacto

---

### Impacto de la Migración RS256

| Aspecto | Antes (HS256) | Después (RS256) |
|---------|---------------|-----------------|
| Algoritmo | HMAC256 (simétrico) | RSA256 (asimétrico) |
| Clave de firma | Secret string en properties | Private key en archivo PEM |
| Clave de validación | Mismo secret | Public key en archivo PEM |
| Distribución segura | NO (secret no debe compartirse) | SÍ (public key es pública) |
| Quien puede emitir tokens | Quien tenga el secret | Solo UserService (tiene private key) |
| Quien puede validar tokens | Quien tenga el secret | Cualquiera (con public key) |

---

### ⚠️ Nota de Seguridad Crítica

La clave `private.pem` actual en `src/main/resources/certs/` está **COMPROMETIDA** porque está en el repositorio público. Para producción:

1. **Generar un nuevo par de claves**
2. **NO comitear private.pem** al repositorio
3. **Inyectar private.pem vía variable de entorno o Vault:**
   ```properties
   jwt.private-key=${JWT_PRIVATE_KEY}  # Valor injected en tiempo de ejecución
   ```
4. **Comitear SOLO public.pem** (es seguro compartir)

---

## 1.3 ❌ NO IMPLEMENTADO: OAuth2 (Google / Facebook) {#deuda-3}

**Estado verificado:** Cero código relacionado con OAuth2.

```
pom.xml: NO contiene spring-boot-starter-oauth2-client ❌
Clases OAuth: NINGUNA
Configuración OAuth: NINGUNA
User entity: NO tiene campos oauthProvider, oauthId, oauthEmail ❌
```

### Especificación Completa Requerida

Ver documento `07-HOJA_RUTA.md` sección "Fase 4 — OAuth2" para plan de implementación detallado.

---

## 1.4 ❌ NO EXISTE: Colección de Tests Postman {#deuda-4}

**Estado verificado:**
```
Directorio src/postman/: NO EXISTE
Archivos .json de Postman: NINGUNO
```

### Especificación

Ver documento `APENDICES.md` sección "Apéndice D — Colección Postman" para estructura JSON completa.

---

## 1.5 ⚠️ VERIFICADO: `softDeleteUser()` — Existe con limitaciones {#deuda-5}

**Estado verificado:** El método EXISTE y funciona como soft delete, pero está incompleto.

### Código Actual

```java
// UserService.java L.91-103
public Optional<UserDto> softDeleteUser(String token) {
  String tokenVerify = token.substring(7);
  String userName = jwtUtil.validateToken(tokenVerify);  // ❌ D1: Eliminar
  Optional<UserModel> user = userRepository.findByUsername(userName);
  
  if (user.isPresent()) {
    user.get().setDeleted(true);  // ✅ Soft delete
    userRepository.save(user.get());
    return userRepository.findByUsername(userName)
            .map(User -> new UserDto(User.getId(), User.getUsername(), User.getEmail()));
  } else {
    throw new RuntimeException("Usuario no encontrado o token inválido");
  }
}
```

### Lo que HACE correctamente

- ✅ **Soft delete real:** Solo marca `deleted = true`, no borra datos
- ✅ **Datos preservados:** Todos los datos del usuario permanecen en BD
- ✅ **Filtrado en queries:** `getUsersNames()` usa `findByDeletedFalse()` — excluye eliminados

### Problemas Identificados

#### Problema 1: No hay `deleted_at` timestamp

`UserModel` no tiene campo de fecha de eliminación:

```java
// UserModel.java
private Boolean deleted = false;  // ✅ EXISTE
// private LocalDateTime deletedAt = null;  // ❌ NO EXISTE
```

**Impacto:** Imposible saber CUÁNDO se eliminó un usuario.

**Fix requerido:** Agregar `deletedAt` a `UserModel`:

```java
@Column(name = "deleted_at", nullable = true)
private LocalDateTime deletedAt;

// En softDeleteUser():
if (user.isPresent()) {
  user.get().setDeleted(true);
  user.get().setDeletedAt(LocalDateTime.now());  // ✅ NUEVO
  userRepository.save(user.get());
}
```

#### Problema 2: No hay auditoría

No se registra quién eliminó el usuario ni cuándo.

**Fix requerido:** Agregar logging:

```java
public Optional<UserDto> softDeleteUser(String username) {  // Cambio de firma
  Optional<UserModel> user = userRepository.findByUsername(username);
  
  if (user.isPresent()) {
    user.get().setDeleted(true);
    user.get().setDeletedAt(LocalDateTime.now());
    userRepository.save(user.get());
    
    LOGGER.info("User soft-deleted: {} at {}", username, LocalDateTime.now());  // ✅ NUEVO
    
    return userRepository.findByUsername(username)
            .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail()));
  } else {
    throw new UserNotFoundException(username);
  }
}
```

#### Problema 3: No valida si ya está eliminado

Si el usuario ya tiene `deleted=true`, se intenta guardar de nuevo silenciosamente.

**Fix requerido:**

```java
if (user.isPresent()) {
  if (user.get().getDeleted()) {
    throw new UserAlreadyDeletedException(username);  // ✅ NUEVO
  }
  user.get().setDeleted(true);
  user.get().setDeletedAt(LocalDateTime.now());
  userRepository.save(user.get());
  
  return userRepository.findByUsername(username)
          .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail()));
}
```

#### Problema 4: Response confusa

Retorna los datos del usuario DESPUÉS de ser eliminado (L.98-99):

```java
return userRepository.findByUsername(userName)
        .map(User -> new UserDto(User.getId(), User.getUsername(), User.getEmail()));
```

Esto es confuso porque el usuario ya está marcado como eliminado. La respuesta debería indicarlo claramente:

```java
// Option 1: Retornar status "deleted"
return ResponseEntity.ok(Map.of(
  "message", "Usuario eliminado exitosamente",
  "userId", user.get().getId(),
  "deletedAt", LocalDateTime.now()
));

// Option 2: Retornar DTO con bandera "deleted"
UserDto dto = new UserDto(user.get().getId(), user.get().getUsername(), user.get().getEmail());
dto.setDeleted(true);  // Si la propiedad existe
return Optional.of(dto);
```

#### Problema 5: Validación JWT local debe eliminarse

L.92-93 contienen validación que debe eliminarse en Deuda D1.

---

### Cambios Consolidados para `softDeleteUser()`

```java
public Optional<UserDto> softDeleteUser(String username) {  // ✅ Cambio de firma
  Optional<UserModel> user = userRepository.findByUsername(username);
  
  if (!user.isPresent()) {
    throw new UserNotFoundException(username);  // ✅ Manejo explícito
  }
  
  if (user.get().getDeleted()) {
    throw new UserAlreadyDeletedException(username);  // ✅ Validación nueva
  }
  
  user.get().setDeleted(true);
  user.get().setDeletedAt(LocalDateTime.now());  // ✅ Timestamp nuevo
  userRepository.save(user.get());
  
  LOGGER.info("User soft-deleted: {} at {}", username, LocalDateTime.now());  // ✅ Auditoría nueva
  
  // Retornar confirmación clara
  return Optional.of(new UserDto(user.get().getId(), user.get().getUsername(), user.get().getEmail()));
}
```

---

## 1.6 ⚠️ EXISTE PARCIALMENTE: Reactivación de Usuario {#deuda-6}

**Estado verificado:** El método `ActiveUser()` EXISTE pero tiene diseño incorrecto para el caso de uso esperado.

### Código Actual

```java
// UserService.java L.105-113
public void ActiveUser(String username, String password) {
  Optional<UserModel> user = userRepository.findByUsername(username);
  if (user.isPresent() && passwordEncoder.matches(password, user.get().getPassword())) {
    user.get().setDeleted(false);
    userRepository.save(user.get());
  } else {
    throw new RuntimeException("Usuario no encontrado o token inválido");
  }
}
```

### Problemas del Diseño Actual

#### Problema 1: Requiere contraseña del usuario

El método requiere `username + password`, no es una operación de administrador.

```java
// Caso de uso actual (incorrecto):
activeUser("juan", "micontraseña")  // Usuario debe conocer su propia contraseña

// Caso de uso esperado (correcto):
reactivateUser(1L)  // Admin reactiva usuario por ID, sin necesidad de contraseña
```

#### Problema 2: Sin control de acceso (ADMIN)

No hay verificación de que quien llama tenga permisos de admin.

```java
// ❌ Actualmente:
userService.ActiveUser(username, password);  // Cualquiera puede hacerlo

// ✅ Esperado:
@PostMapping("/{id}/reactivate")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> reactivateUser(@PathVariable Long id) {
  userService.reactivateUser(id);
}
```

#### Problema 3: Sin endpoint REST

No está expuesto como endpoint HTTP.

#### Problema 4: Sin auditoría

No se registra quién reactivó el usuario.

#### Problema 5: Sin validación de estado

No valida si el usuario ya está activo.

#### Problema 6: Naming inconsistente

`ActiveUser` con A mayúscula viola convención Java. Debería ser `reactivateUser` (verbo + claro propósito).

---

### Reemplazo Requerido: `reactivateUser()`

```java
/**
 * Reactiva un usuario previamente eliminado (solo para ADMIN).
 * Requiere que el usuario tenga deleted=true.
 * 
 * @param userId ID del usuario a reactivar
 * @return Usuario reactivado
 * @throws UserNotFoundException Si el usuario no existe
 * @throws UserNotDeletedException Si el usuario no está eliminado
 * @throws AccessDeniedException Si quien llama no es ADMIN
 */
public UserDto reactivateUser(Long userId) {
  UserModel user = userRepository.findById(userId)
      .orElseThrow(() -> new UserNotFoundException(userId));
  
  if (!user.getDeleted()) {
    throw new UserNotDeletedException(userId);  // ✅ Validación nueva
  }
  
  user.setDeleted(false);
  user.setDeletedAt(null);  // ✅ Limpiar timestamp
  userRepository.save(user);
  
  LOGGER.info("User reactivated: {} by {}", userId, getCurrentAdmin());  // ✅ Auditoría
  
  return new UserDto(user.getId(), user.getUsername(), user.getEmail());
}
```

### Endpoint REST

```java
@PostMapping("/{id}/reactivate")
@PreAuthorize("hasRole('ADMIN')")  // ✅ Requiere ADMIN
public ResponseEntity<UserDto> reactivateUser(@PathVariable Long id) {
  try {
    UserDto reactivatedUser = userService.reactivateUser(id);
    LOGGER.info("User reactivated by admin: {}", id);
    return ResponseEntity.ok(reactivatedUser);
  } catch (UserNotFoundException e) {
    return ResponseEntity.notFound().build();
  } catch (UserNotDeletedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "User is already active"));
  }
}
```

---

## 1.7 ❌ NO EXISTE: `changePassword()` {#deuda-7}

**Estado verificado:** NO existe como método ni como endpoint.

```
UserService.java L.159-164: Solo hay un comentario TODO
UserController.java: Sin endpoint change-password
```

### Especificación Completa Requerida

Ver documento `07-HOJA_RUTA.md` sección "Fase 3.3 — Implementar changePassword()" para la especificación detallada de:
- Endpoint
- Request/Response
- Validaciones
- Errores HTTP
- Auditoría
- Seguridad

---

## 1.8 DEUDAS ADICIONALES ENCONTRADAS (Menor Prioridad)

Encontradas en los comentarios TODO de `UserService.java`:

| TODO encontrado | Ubicación | Prioridad |
|---|---|---|
| `myProfile()` — perfil del usuario autenticado (menos campos) | L.116-124 | BAJA |
| Mejor manejo de errores (no se retornan mensajes estructurados) | L.127-128 | MEDIA |
| Rate limiting de intentos fallidos de login | L.130-133 | BAJA |
| `ChangeRol()` — cambiar rol de usuario | L.135-140 | BAJA |
| `updateAvatar()` — actualizar avatar | L.147-151 | BAJA |
| `resetPassword()` — resetear contraseña olvidada | L.153-158 | MEDIA |
| `changeEmail()` — cambiar email (separado de updateUser) | L.165-170 | BAJA |
| `changeUsername()` — cambiar username | L.171-176 | BAJA |
| `UserController.java` L.62: Paginación en `/user/search` | L.62 | BAJA |

---

**Documento:** 02-DEUDAS_TECNICAS.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
