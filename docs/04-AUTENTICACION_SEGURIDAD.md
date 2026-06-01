# Autenticación y Seguridad

## JWT — Estado Actual (HS256)

### Configuración

```properties
# application.properties
jwt.secret=MI-CLAVE-SUPER-SEGURA  # ❌ Inseguro: hardcodeado, visible
jwt.expiration=86400000            # 24 horas en milisegundos
```

### Algoritmo y Firma

**Clase:** `JwtUtil.java`  
**Biblioteca:** `com.auth0:java-jwt:4.4.0`  
**Algoritmo:** `HMAC256` (simétrico — misma clave para firmar y verificar)

```java
// L.36 — Firma actual
.sign(Algorithm.HMAC256(secretKey));  // ❌ HS256 débil
```

### Claims del Token Actual

El JWT generado contiene:
- `sub` (subject): username del usuario
- `iat` (issued at): timestamp Unix de emisión
- `exp` (expires at): timestamp Unix de expiración (= iat + 86400000ms)

**Ejemplo de token decodificado:**
```json
// Header
{
  "alg": "HS256",
  "typ": "JWT"
}

// Payload
{
  "sub": "juan123",
  "iat": 1748000000,
  "exp": 1748086400
}

// Signature: HMAC256(secret, base64UrlEncode(header) + "." + base64UrlEncode(payload))
```

### Problemas de Seguridad

| Problema | Riesgo | Solución |
|----------|--------|----------|
| Secret hardcodeado en properties | Alto | Usar variables de entorno (Deuda D2) |
| HS256 (simétrico) | Alto | Migrar a RS256 (Deuda D2) |
| Secret débil ("MI-CLAVE-SUPER-SEGURA") | Alto | Usar clave más fuerte |
| No hay `jti` (JWT ID) | Medio | Agregar para revocación futura |
| No hay `iss` (issuer) | Bajo | Agregar para auditoría |
| No hay `aud` (audience) | Bajo | Agregar para validación futura |
| Tokens no se pueden revocar | Medio | Implementar blacklist o TTL corto |
| No hay refresh tokens | Medio | Implementar refresh token mechanism |
| Expiración de 24h es larga | Bajo | Considerar reducir a 1h + refresh |

---

## JWT — Estado Futuro (RS256)

### Configuración

```properties
# application.properties
jwt.private-key-path=classpath:certs/private.pem
jwt.public-key-path=classpath:certs/public.pem  # Para validación futura
jwt.algorithm=RS256
jwt.expiration=3600000   # 1 hora (más corto con RS256)
```

### Archivos de Certificados

```
src/main/resources/certs/
  ├── private.pem    ✅ EXISTE (RSA 2048 bits)
  └── public.pem     ❌ NO EXISTE (generar de private.pem)
```

**Generación:**
```bash
# Generar public key desde private key
openssl rsa -in src/main/resources/certs/private.pem \
  -pubout -out src/main/resources/certs/public.pem
```

### Algoritmo y Firma

```java
// Futuro en JwtUtil.java
.sign(Algorithm.RSA256(null, privateKey));  // ✅ RS256 asimétrico
```

### Ventajas de RS256 sobre HS256

| Aspecto | HS256 | RS256 |
|--------|-------|-------|
| Tipo de clave | Simétrica (1 secret) | Asimétrica (private + public) |
| Firma | Con secret | Con private key |
| Validación | Con secret | Con public key |
| Distribución segura | NO (secret no compartible) | SÍ (public key es pública) |
| Quien puede firmar | Quien tenga secret | Solo quien tenga private key |
| Quien puede validar | Quien tenga secret | Cualquiera (con public key) |
| Confianza | Baja (secret comprometible) | Alta (private key separado) |

---

## Flujo de Autenticación Actual

```
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT                                                          │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1. POST /auth/login { username, password }
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ API GATEWAY (futuro: validará aquí)                             │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 2. Envía a UserService
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserService (ACTUAL)                                            │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ AuthController.login()                                      │ │
│ │   → UserService.getAuthenticatedUser(username, password)    │ │
│ │      → BCryptPasswordEncoder.matches(password, hash) ✅    │ │
│ │      → JwtUtil.generateToken(username) — HS256 ❌          │ │
│ │      → Retorna JWT                                          │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 3. Response { token: "eyJ..." }
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT                                                          │
│ Almacena token en localStorage/sessionStorage                  │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 4. Requests posteriores incluyen Authorization header
       │    Authorization: Bearer eyJ...
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ API GATEWAY (futuro: validará aquí y pasará usuario)            │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 5. Pasa request a UserService con usuario inyectado
       │    (o sigue validando localmente — a eliminar)
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserService (ACTUAL)                                            │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 1. JwtAuthenticationFilter.doFilterInternal()              │ │
│ │    → Extrae token del header Authorization                 │ │
│ │    → JwtUtil.validateToken(token) — valida HS256 ❌       │ │
│ │    → Popula SecurityContext con username                   │ │
│ │                                                             │ │
│ │ 2. Endpoint (ej. UserController.getUser())                 │ │
│ │    → @RequestHeader("Authorization") String token (❌ D1)  │ │
│ │    → JwtUtil.validateToken(token) — REDUNDANTE (❌ D1)     │ │
│ │    → Lógica de negocio                                      │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 6. Response
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT                                                          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Flujo de Autenticación Futuro (Post Deudas D1 y D2)

```
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT                                                          │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 1. POST /auth/login { username, password }
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ API GATEWAY                                                     │
│ (Valida credentials contra UserService)                        │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 2. Envía a UserService
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserService                                                     │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ AuthController.login()                                      │ │
│ │   → UserService.getAuthenticatedUser(username, password)    │ │
│ │      → BCryptPasswordEncoder.matches(password, hash) ✅    │ │
│ │      → JwtUtil.generateToken(username) — RS256 ✅          │ │
│ │         (firma con private.pem)                             │ │
│ │      → Retorna JWT                                          │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 3. Response { token: "eyJ..." }
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ API GATEWAY                                                     │
│ Valida token con public.pem (RS256) ✅                          │
│ Extrae claims → username                                        │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 4. CLIENT almacena token
       │
       │ 5. Request posterior: Authorization: Bearer eyJ...
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ API GATEWAY                                                     │
│ 1. Valida token con public.pem (RS256) ✅                      │
│ 2. Extrae username del token                                    │
│ 3. Inyecta en header: X-Authenticated-User: juan123           │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 6. Request + header de usuario
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ UserService                                                     │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ 1. SecurityFilter (NEW) popula SecurityContext desde header │ │
│ │    (X-Authenticated-User: juan123)                          │ │
│ │                                                             │ │
│ │ 2. Endpoint (ej. UserController.getUser())                 │ │
│ │    → Obtiene username del SecurityContext ✅               │ │
│ │    → SIN validación de token local ✅                      │ │
│ │    → Lógica de negocio                                      │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
       │
       │ 7. Response
       ↓
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT                                                          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Filtro de Autenticación Actual

### `JwtAuthenticationFilter.java` — A Eliminar

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final List<String> PUBLIC_PATHS = Arrays.asList(
      "/auth/login",
      "/auth/register");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    String path = request.getRequestURI();
    return PUBLIC_PATHS.contains(path);  // ✅ Bypass para rutas públicas
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String token = request.getHeader("Authorization");

    if (token != null && token.startsWith("Bearer ")) {
      token = token.substring(7);
      String username = jwtUtil.validateToken(token);  // ❌ Valida JWT localmente
      if (username != null) {
        // Crea UserDetails y popula SecurityContext
        UserDetails userDetails = User.withUsername(username).password("").authorities("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
      }
    }
    chain.doFilter(request, response);
  }
}
```

**Problemas:**
- ❌ Valida JWT localmente (debe eliminarse — Deuda D1)
- ⚠️ No maneja excepciones de validación (pueden caer a 500)
- ⚠️ Siempre continúa (`chain.doFilter()`) aunque falle validación
- ⚠️ Crea UserDetails sin roles reales (hardcodeado "USER")

---

## OAuth2 — No Implementado

### Estado Actual
- ❌ Cero dependencias de OAuth2 en pom.xml
- ❌ Cero clases de OAuth2
- ❌ Cero configuración de OAuth2

### Estado Futuro (Deuda D3)

Ver `07-HOJA_RUTA.md` § Fase 4 para especificación completa.

**Resumen:**
- Soportar Google OAuth2
- Soportar Facebook OAuth2
- Flujo Authorization Code + PKCE
- Mapeo de usuario OAuth → usuario local
- Nuevos campos en User entity: oauthProvider, oauthId, oauthEmail

---

## Seguridad — Validaciones de Entrada

### Estado Actual

| Validación | Estado | Ubicación |
|---|---|---|
| Username null | ❌ No validado | `registerUser()` no usa `@Valid` |
| Username vacío | ❌ No validado | — |
| Username duplicado | ❌ Constraint en BD (no manejado) | — |
| Email inválido | ❌ No validado | — |
| Email duplicado | ❌ Constraint en BD (no manejado) | — |
| Password débil | ❌ No validado | Debería tener longitud mínima, complejidad |
| Password null | ❌ No validado | — |
| Password no hasheado | ✅ Hasheado con BCrypt | `registerUser()` L.41 |

### Recomendaciones

Implementar validaciones con `@Valid` y `@Validated`:

```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDto user) {
  // @Valid dispara validaciones en RegisterRequestDto
  return ResponseEntity.status(201).body(userService.registerUser(user));
}

// RegisterRequestDto.java
public class RegisterRequestDto {
  @NotBlank(message = "Username is required")
  @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
  private String username;
  
  @NotBlank(message = "Password is required")
  @Size(min = 8, message = "Password must be at least 8 characters")
  private String password;
  
  @NotNull(message = "Password confirmation is required")
  private String confirmPassword;
  
  @NotBlank(message = "Email is required")
  @Email(message = "Email must be valid")
  private String email;
  
  // getters/setters
}
```

---

## Encriptación de Contraseñas

### Algoritmo

**BCrypt** con `new BCryptPasswordEncoder()`

```java
// UserService.java L.26
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

// Registro
user.setPassword(passwordEncoder.encode(user.getPassword()));  // Genera hash + salt

// Login
passwordEncoder.matches(password, user.get().getPassword())    // Verifica contra hash
```

### Configuración

- ✅ Usa BCrypt por defecto (salted, iterado)
- ⚠️ No hay configuración personalizada de strength (usa default)

### Recomendación

Considerar aumentar la iteración de BCrypt para mayor seguridad:

```java
public PasswordEncoder passwordEncoder() {
  return new BCryptPasswordEncoder(12);  // 12 = strength (default 10)
}
```

---

## Rate Limiting

### Estado Actual

❌ No implementado. TODO comentado en `UserService.java:130-133`.

### Recomendación Futura

Implementar rate limiting en login:
- Máximo 5 intentos fallidos por IP/usuario
- Bloquear durante 15 minutos tras alcanzar el límite
- Usar `LoginAttempt` entity para rastrear intentos

---

## CORS — Cross-Origin Resource Sharing

### Estado Actual

⚠️ No hay configuración explícita de CORS. Spring Boot por defecto NO permite CORS.

### Recomendación

Agregar configuración de CORS si se necesita acceso desde navegadores:

```java
@Configuration
public class CorsConfig {
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/auth/**")
            .allowedOrigins("https://frontend.example.com")
            .allowedMethods("POST", "GET")
            .maxAge(3600);
      }
    };
  }
}
```

---

## Headers de Seguridad

### Estado Actual

❌ No hay configuración de headers de seguridad HTTP.

### Recomendación

Agregar en `SecurityConfig`:

```java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
    .xssProtection()
    .frameOptions(frameOptions -> frameOptions.deny()));
```

---

**Documento:** 04-AUTENTICACION_SEGURIDAD.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
