# Hoja de Ruta de Implementación

## Timeline General

```
Semana 1:      Fases 1-2 (RS256 + Eliminación JWT local)           8-10h
Semana 2:      Fase 3 (changePassword, reactivateUser, correcciones) 5-7h
Semana 3-4:    Fase 4 (OAuth2)                                      16-20h
Semana 4:      Fase 5 (Testing, Postman, documentación)             3-4h
               ───────────────────────────────────────────────────────────
               TOTAL: ~32-41 horas de desarrollo
```

**Inicio recomendado:** Inmediatamente (D1 y D2 son críticas)

---

## Fase 1 — Seguridad JWT (Semana 1, estimado: 4-6h)

**Objetivo:** Migrar de HMAC256 a RS256 para firma de tokens.

**Bloqueador para:** Fase 2

### Paso 1.1: Generar `public.pem`

```bash
# Extraer public key desde private key existente
openssl rsa -in src/main/resources/certs/private.pem \
  -pubout -out src/main/resources/certs/public.pem

# Verificar
cat src/main/resources/certs/public.pem
```

**Resultado:** Nuevo archivo `src/main/resources/certs/public.pem`

**Tiempo:** 5 minutos

---

### Paso 1.2: Actualizar `application.properties`

```properties
# ELIMINAR:
jwt.secret=MI-CLAVE-SUPER-SEGURA

# AGREGAR:
jwt.private-key-path=classpath:certs/private.pem
jwt.algorithm=RS256
jwt.expiration=3600000
jwt.refresh-expiration=86400000
```

**Tiempo:** 5 minutos

---

### Paso 1.3: Refactorizar `JwtUtil.java`

Cambiar de HS256 a RS256:

```java
@Component
public class JwtUtil {

  private final PrivateKey privateKey;
  private final long expirationTime;

  public JwtUtil(@Value("${jwt.private-key-path}") Resource privateKeyResource,
                 @Value("${jwt.expiration}") long expirationTime) throws Exception {
    this.expirationTime = expirationTime;
    
    // Leer y parsear PEM
    String keyContent = StreamUtils.copyToString(
        privateKeyResource.getInputStream(), StandardCharsets.UTF_8);
    
    // Remover headers PEM
    keyContent = keyContent
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    
    // Decodificar base64
    byte[] decodedKey = Base64.getDecoder().decode(keyContent);
    
    // Generar private key desde bytes
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    this.privateKey = (PrivateKey) keyFactory.generatePrivate(
        new PKCS8EncodedKeySpec(decodedKey));
  }

  public String generateToken(String username) {
    return JWT.create()
            .withSubject(username)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
            .sign(Algorithm.RSA256(null, privateKey));  // ✅ RS256
  }

  // TEMPORALMENTE (será eliminado en Fase 2):
  public String validateToken(String token) throws Exception {
    // Leer public key
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    // ... parsear public.pem ...
    
    JWTVerifier verifier = JWT.require(Algorithm.RSA256(publicKey, null))
            .build();
    DecodedJWT jwt = verifier.verify(token);
    return jwt.getSubject();
  }
}
```

**Dependencias a agregar en `pom.xml`:**

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk15on</artifactId>
  <version>1.70</version>
</dependency>
```

**Tiempo:** 2-3 horas (parsing PEM puede ser tricky)

---

### Paso 1.4: Actualizar Tests

```java
@Test
public void testGenerateTokenWithRS256() {
  String token = jwtUtil.generateToken("testuser");
  
  DecodedJWT decoded = JWT.decode(token);
  assertEquals("RS256", decoded.getAlgorithm());  // ✅ Validar algoritmo
  assertEquals("testuser", decoded.getSubject());
}

@Test
public void testValidateTokenWithRS256() {
  String token = jwtUtil.generateToken("testuser");
  String username = jwtUtil.validateToken(token);
  assertEquals("testuser", username);
}

@Test
public void testExpiredTokenThrows() {
  // Crear token con expiración de 1ms
  assertThrows(RuntimeException.class, () -> {
    jwtUtil.validateToken(expiredToken);
  });
}
```

**Tiempo:** 1 hora

---

### Paso 1.5: Compartir `public.pem` con API Gateway

**Opciones:**
1. **Comitear en repo:** `public.pem` es pública, seguro compartir
2. **Endpoint HTTP:** `GET /auth/public-key` retorna PEM
3. **CI/CD:** Distribuir como artefacto

**Recomendación:** Opción 1 (comitear) — más simple, `public.pem` es no-sensible.

```bash
git add src/main/resources/certs/public.pem
git commit -m "chore(security): add public key for RS256 validation"
```

**Tiempo:** 15 minutos

---

### Validación de Fase 1

- [ ] `public.pem` generado y existente
- [ ] `application.properties` actualizado (sin `jwt.secret`)
- [ ] `JwtUtil.java` compila y lee `private.pem`
- [ ] Tests pasan (RS256 firma y valida)
- [ ] `public.pem` compartido con API Gateway
- [ ] Servidor inicia sin errores

---

## Fase 2 — Eliminación de Validación JWT Local (Semana 1-2, estimado: 2-3h)

**Prerequisito:** Confirmar que API Gateway está listo para validar JWTs.

**Bloqueador para:** D1, D5, D6, D7

### Paso 2.1: Sincronización con API Gateway

**Comunicación necesaria:**

- [ ] API Gateway **ya valida JWTs** (verificación con public key)
- [ ] API Gateway **inyecta usuario en header** — ¿cuál es el nombre? (ej. `X-Authenticated-User`)
- [ ] API Gateway tiene **public.pem** (recibió en Fase 1.5)
- [ ] Plan de **rollback** si hay problemas

**Resultado esperado:** Reunión de sincronización completada, acuerdo de header name.

**Tiempo:** 30 minutos (coordinar con equipo de API Gateway)

---

### Paso 2.2: Eliminar `JwtAuthenticationFilter.java`

**Archivo:** `src/main/java/.../Security/JwtAuthenticationFilter.java`

**Acción:** Eliminar clase completa (~55 líneas)

```bash
git rm src/main/java/com/ProdeMaster/UserServices/Security/JwtAuthenticationFilter.java
```

**Tiempo:** 5 minutos

---

### Paso 2.3: Modificar `SecurityConfig.java`

Eliminar registro del filtro:

```java
@Configuration
public class SecurityConfig {

  // ❌ ELIMINAR:
  // private final JwtAuthenticationFilter jwtAuthenticationFilter;
  // @Autowired
  // public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) { ... }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/login", "/auth/register").permitAll()
            .requestMatchers(HttpMethod.GET, "/user/**").authenticated()
            .requestMatchers(HttpMethod.PUT, "/user/**").authenticated()
            .requestMatchers(HttpMethod.DELETE, "/user/**").authenticated()
            .anyRequest().authenticated())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // ❌ ELIMINAR:
        // .addFilterBefore(jwtAuthenticationFilter, SessionManagementFilter.class);
        
        // ✅ AGREGAR (NEW FILTER):
        .addFilterBefore(new ApiGatewayAuthFilter(), SessionManagementFilter.class);
    
    return http.build();
  }
}
```

**Crear nuevo filtro para leer header del API Gateway:**

```java
@Component
public class ApiGatewayAuthFilter extends OncePerRequestFilter {

  private static final List<String> PUBLIC_PATHS = Arrays.asList(
      "/auth/login",
      "/auth/register");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return PUBLIC_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    
    String username = request.getHeader("X-Authenticated-User");  // ← Header del API Gateway
    
    if (username != null) {
      UserDetails userDetails = User.withUsername(username)
          .password("")
          .authorities("USER")
          .build();
      
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
    
    chain.doFilter(request, response);
  }
}
```

**Tiempo:** 30 minutos

---

### Paso 2.4: Refactorizar `UserController.java`

Eliminar validaciones JWT redundantes:

```java
@RestController
@RequestMapping("/user")
public class UserController {

  @Autowired
  private UserService userService;
  
  // ❌ ELIMINAR:
  // @Autowired
  // private JwtUtil jwtUtil;

  @PutMapping("")
  public ResponseEntity<?> updateUser(
      // ❌ ELIMINAR: @RequestHeader("Authorization") String token,
      @RequestBody UpdateUserDto updateData) {
    
    // ❌ ELIMINAR:
    // String newToken = userService.updateUser(token, updateData);
    // String tokenVerify = token.substring(7);
    // String username = jwtUtil.validateToken(tokenVerify);
    
    // ✅ NUEVO:
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    String newToken = userService.updateUser(username, updateData);  // Firma cambiada
    
    LOGGER.info("Profile update user: {}", username);
    return ResponseEntity.ok(Map.of("token", newToken));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getUser(
      // ❌ ELIMINAR: @RequestHeader("Authorization") String token,
      @PathVariable Long id) {
    
    // ❌ ELIMINAR:
    // String tokenVerify = token.substring(7);
    // String username = jwtUtil.validateToken(tokenVerify);
    // LOGGER.info("Profile user: {}", username);
    
    // ✅ NUEVO (logging es opcional aquí):
    return ResponseEntity.ok(userService.userProfile(id));
  }

  @DeleteMapping("")
  public ResponseEntity<?> deleteUser(
      // ❌ ELIMINAR: @RequestHeader("Authorization") String token,
      ) {
    
    // ✅ NUEVO:
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    Optional<UserDto> user = userService.softDeleteUser(username);  // Firma cambiada
    
    LOGGER.info("Delete user: {}", user.get().getUsername());
    return ResponseEntity.ok(user);
  }

  @GetMapping("/search")
  public ResponseEntity<?> getSearchUser(
      // ❌ ELIMINAR: @RequestHeader("Authorization") String token,
      @RequestParam String username,
      @RequestParam String email) {
    
    // ✅ NUEVO:
    String authenticatedUser = SecurityContextHolder.getContext().getAuthentication().getName();
    LOGGER.info("Search user by: {}", authenticatedUser);
    
    Stream<UserDto> users = userService.searchUsers(authenticatedUser, username, email);  // Firma cambiada
    return ResponseEntity.ok(users);
  }
}
```

**Tiempo:** 45 minutos

---

### Paso 2.5: Refactorizar `UserService.java`

Cambiar firmas de métodos (eliminar parámetro `token`):

```java
@Service
public class UserService {

  // ❌ ELIMINAR:
  // @Autowired
  // private JwtUtil jwtUtil;

  // Cambiar firmas:
  
  public Stream<UserDto> searchUsers(
      String authenticatedUser,  // ← Cambio: era token, ahora username
      String username,
      String email) {
    
    // ❌ ELIMINAR:
    // String tokenVerify = token.substring(7);
    // String userName = jwtUtil.validateToken(tokenVerify);
    
    // ✅ NUEVO (usar parámetro directo):
    Optional<UserModel> user = userRepository.findByUsername(authenticatedUser);
    if (user.isPresent()) {
      List<UserModel> users = userRepository.searchUsers(username, email);
      return users.stream()
          .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail()));
    } else {
      throw new UserNotFoundException(authenticatedUser);
    }
  }

  public String updateUser(
      String username,  // ← Cambio: era token
      UpdateUserDto userData) {
    
    // ❌ ELIMINAR:
    // String tokenVerify = token.substring(7);
    // String username = jwtUtil.validateToken(tokenVerify);
    
    Optional<UserModel> user = userRepository.findByUsername(username);
    if (user.isPresent()) {
      if (userData.getEmail() != null && !userData.getEmail().isBlank()) {
        user.get().setEmail(userData.getEmail());
      }
      if (userData.getUsername() != null && !userData.getUsername().isBlank()) {
        user.get().setUsername(userData.getUsername());
      }
      UserModel userUpdate = userRepository.save(user.get());
      String newToken = generateTokenFromUsername(userUpdate.getUsername());
      return newToken;
    } else {
      throw new UserNotFoundException(username);
    }
  }

  public Optional<UserDto> softDeleteUser(String username) {  // ← Cambio: era token
    
    // ❌ ELIMINAR:
    // String tokenVerify = token.substring(7);
    // String userName = jwtUtil.validateToken(tokenVerify);
    
    Optional<UserModel> user = userRepository.findByUsername(username);
    if (user.isPresent()) {
      user.get().setDeleted(true);
      // ✅ NUEVO (Deuda D5):
      user.get().setDeletedAt(LocalDateTime.now());
      
      userRepository.save(user.get());
      return userRepository.findByUsername(username)
          .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail()));
    } else {
      throw new UserNotFoundException(username);
    }
  }
}
```

**Tiempo:** 1 hora

---

### Paso 2.6: Actualizar Tests

Actualizar tests que dependían de validación JWT:

```java
@Test
public void testUpdateUserEliminaValidacionLocalJWT() {
  String username = "juan";
  UpdateUserDto dto = new UpdateUserDto("juan_nuevo", "juan@example.com");
  
  // ✅ NUEVO (no requiere token):
  String newToken = userService.updateUser(username, dto);
  assertNotNull(newToken);
}

@Test
public void testSoftDeleteUserEliminaValidacionLocalJWT() {
  String username = "juan";
  
  // ✅ NUEVO (no requiere token):
  Optional<UserDto> result = userService.softDeleteUser(username);
  assertTrue(result.isPresent());
}
```

**Tiempo:** 45 minutos

---

### Paso 2.7: Eliminar método `validateToken()` de `JwtUtil.java`

Una vez que todos los usos de `jwtUtil.validateToken()` se hayan removido:

```java
// ❌ ELIMINAR (líneas 45-55):
public String validateToken(String token) {
  try {
    JWTVerifier verifier = JWT.require(Algorithm.RSA256(publicKey, null))
            .build();
    DecodedJWT jwt = verifier.verify(token);
    return jwt.getSubject();
  } catch (JWTVerificationException e) {
    throw new RuntimeException("Token inválido o expirado", e);
  }
}
```

`JwtUtil.java` ahora solo tiene `generateToken()`.

**Tiempo:** 5 minutos

---

### Validación de Fase 2

- [ ] `JwtAuthenticationFilter.java` eliminada
- [ ] `SecurityConfig.java` sin referencia a `jwtAuthenticationFilter`
- [ ] `ApiGatewayAuthFilter.java` creado e inyectado
- [ ] `UserController.java` sin llamadas a `jwtUtil.validateToken()`
- [ ] `UserService.java` sin llamadas a `jwtUtil.validateToken()`
- [ ] Firmas de métodos refactorizadas (sin parámetro `token`)
- [ ] Tests pasan
- [ ] Servidor inicia sin errores
- [ ] Endpoints requieren header `X-Authenticated-User` del API Gateway

---

## Fase 3 — Métodos Faltantes (Semana 2, estimado: 5-7h)

### 3.1 Corregir `softDeleteUser()` (1-2h)

**Cambios:**

1. Agregar campo `deletedAt` a `UserModel`
2. Registrar timestamp al hacer soft delete
3. Manejar caso "usuario ya eliminado"
4. Mejorar response

**Código:**

```java
// UserModel.java
@Column(name = "deleted_at", nullable = true)
private LocalDateTime deletedAt;

// UserService.java
public Optional<UserDto> softDeleteUser(String username) {
  Optional<UserModel> user = userRepository.findByUsername(username);
  
  if (!user.isPresent()) {
    throw new UserNotFoundException(username);
  }
  
  if (user.get().getDeleted()) {
    throw new UserAlreadyDeletedException(username);  // ✅ Validación nueva
  }
  
  user.get().setDeleted(true);
  user.get().setDeletedAt(LocalDateTime.now());  // ✅ Timestamp nuevo
  userRepository.save(user.get());
  
  LOGGER.info("User soft-deleted: {} at {}", username, LocalDateTime.now());  // ✅ Auditoría
  
  return userRepository.findByUsername(username)
          .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail()));
}
```

### 3.2 Implementar `reactivateUser()` (2-3h)

Reemplazar el mal diseñado `ActiveUser()`:

```java
// UserService.java
public UserDto reactivateUser(Long userId) {
  UserModel user = userRepository.findById(userId)
      .orElseThrow(() -> new UserNotFoundException(userId));
  
  if (!user.getDeleted()) {
    throw new UserNotDeletedException(userId);
  }
  
  user.setDeleted(false);
  user.setDeletedAt(null);  // Limpiar timestamp
  userRepository.save(user);
  
  LOGGER.info("User reactivated: {} by {}", userId, getCurrentAdmin());
  
  return new UserDto(user.getId(), user.getUsername(), user.getEmail());
}

// UserController.java
@PostMapping("/{id}/reactivate")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> reactivateUser(@PathVariable Long id) {
  try {
    UserDto reactivatedUser = userService.reactivateUser(id);
    return ResponseEntity.ok(reactivatedUser);
  } catch (UserNotFoundException e) {
    return ResponseEntity.notFound().build();
  } catch (UserNotDeletedException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "User is already active"));
  }
}
```

### 3.3 Implementar `changePassword()` (3-4h)

Nuevo endpoint para cambiar contraseña:

```java
// UserService.java
public void changePassword(String username, ChangePasswordRequestDto request) {
  if (!request.getNewPassword().equals(request.getConfirmPassword())) {
    throw new PasswordMismatchException();
  }
  
  UserModel user = userRepository.findByUsername(username)
      .orElseThrow(() -> new UserNotFoundException(username));
  
  if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
    throw new InvalidCurrentPasswordException();
  }
  
  if (request.getCurrentPassword().equals(request.getNewPassword())) {
    throw new SamePasswordException();
  }
  
  user.setPassword(passwordEncoder.encode(request.getNewPassword()));
  userRepository.save(user);
  
  LOGGER.info("Password changed for user: {}", username);
}

// UserController.java
@PostMapping("/{id}/change-password")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<?> changePassword(
    @PathVariable Long id,
    @Valid @RequestBody ChangePasswordRequestDto request) {
  
  String username = SecurityContextHolder.getContext().getAuthentication().getName();
  
  // Validar ownership o admin
  if (!username.equals(id.toString()) && !hasAdminRole()) {
    return ResponseEntity.status(403).build();
  }
  
  try {
    userService.changePassword(username, request);
    return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
  } catch (InvalidCurrentPasswordException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Current password is incorrect"));
  } catch (SamePasswordException e) {
    return ResponseEntity.status(409)
        .body(Map.of("error", "New password must be different"));
  }
}
```

---

## Fase 4 — OAuth2 Google/Facebook (Semana 3-4, estimado: 16-20h)

Ver documento `07-HOJA_RUTA.md` § Fase 4 para plan completo (Deuda D3).

---

## Fase 5 — Testing y Documentación (Semana 4, estimado: 3-4h)

### 5.1 Generar Colección Postman (2h)

Crear `src/postman/UserService.postman_collection.json` con:
- Variables: `base_url`, `access_token`, `user_id`
- Pre-request scripts de autenticación
- Tests de validación para cada endpoint
- Ejemplos de requests y responses
- Casos de error

### 5.2 Tests de Integración (1-2h)

- [ ] Tests E2E con API Gateway (simulado)
- [ ] Tests de RS256 firma y validación
- [ ] Tests de nuevos endpoints (changePassword, reactivateUser)

### 5.3 Documentación Final (1h)

- [ ] Actualizar README.md
- [ ] Documentación de endpoints (OpenAPI/Swagger)
- [ ] Guía de integración para otros servicios
- [ ] Guía de deployment

---

## Checklist General

### Antes de Semana 1
- [ ] Comunicar a equipos consumidores sobre cambios planeados
- [ ] Preparar meeting de sincronización con API Gateway
- [ ] Reservar tiempo para testing

### Semana 1
- [ ] ✅ Fase 1 completada (RS256)
- [ ] ✅ Fase 2 iniciada (eliminación JWT local)
- [ ] ✅ public.pem compartido con API Gateway

### Semana 2
- [ ] ✅ Fase 2 completada
- [ ] ✅ Fase 3 iniciada
- [ ] ✅ `changePassword()` implementado
- [ ] ✅ `reactivateUser()` implementado

### Semana 3-4
- [ ] ✅ Fase 3 completada
- [ ] ✅ Fase 4 completada (OAuth2)
- [ ] ✅ Fase 5 completada (Testing, Postman, docs)

### Post-Implementación
- [ ] Capacitación a equipos consumidores
- [ ] Monitoreo de errores post-deployment
- [ ] Recolectar feedback y mejorar

---

**Documento:** 07-HOJA_RUTA.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
