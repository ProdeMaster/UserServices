# Modelo de Datos

## Entidad Principal: `UserModel`

### Mapeo a BD

```java
@Entity
@Table(name = "users")
public class UserModel {
  // Campos
}
```

Tabla: `users` | BD: PostgreSQL

### Campos Actuales

| Campo | Tipo Java | Columna BD | Tipo BD | Nullable | Unique | Default | Nota |
|-------|-----------|-----------|---------|----------|--------|---------|------|
| `id` | `Long` | `id` | `BIGSERIAL` | NO | SÍ (PK) | — | Auto-generado |
| `username` | `String` | `username` | `VARCHAR` | NO | SÍ | — | — |
| `password` | `String` | `password` | `VARCHAR` | NO | NO | — | BCrypt hash |
| `email` | `String` | `email` | `VARCHAR` | NO | SÍ | — | — |
| `role` | `String` | `role` | `VARCHAR` | NO | NO | "USER" | Sin ENUM |
| `deleted` | `Boolean` | `deleted` | `BOOLEAN` | SÍ | NO | false | Soft delete |

### Código Actual

```java
@Entity
@Table(name = "users")
public class UserModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  @DefaultValue("USER")
  private String role;

  private Boolean deleted = false;

  // Constructores, getters, setters
}
```

---

## Campos FALTANTES Identificados

| Campo | Tipo | Para Qué | Prioridad | Deuda |
|-------|------|----------|-----------|-------|
| `deletedAt` | `LocalDateTime` | Timestamp de soft delete | MEDIA | D5 |
| `createdAt` | `LocalDateTime` | Auditoría de creación | MEDIA | — |
| `updatedAt` | `LocalDateTime` | Auditoría de actualización | MEDIA | — |
| `lastLoginAt` | `LocalDateTime` | Rastreo de acceso | BAJA | — |
| `loginAttempts` | `Integer` | Rate limiting | BAJA | — |
| `oauthProvider` | `String` (enum) | Proveedor OAuth | MEDIA | D3 |
| `oauthId` | `String` | ID único en proveedor | MEDIA | D3 |
| `oauthEmail` | `String` | Email del proveedor | MEDIA | D3 |

### Propuesta para `deletedAt` (Deuda D5)

```java
@Column(name = "deleted_at", nullable = true)
private LocalDateTime deletedAt;

// En softDeleteUser():
user.get().setDeleted(true);
user.get().setDeletedAt(LocalDateTime.now());  // ✅ NUEVO
userRepository.save(user.get());

// En reactivateUser():
user.setDeleted(false);
user.setDeletedAt(null);  // Limpiar
userRepository.save(user);
```

### Propuesta para OAuth2 (Deuda D3)

```java
@Column(nullable = true)
private String oauthProvider;  // "GOOGLE", "FACEBOOK", "LOCAL"

@Column(nullable = true, unique = true)
private String oauthId;  // Identificador único en el proveedor

@Column(nullable = true)
private String oauthEmail;  // Email retornado por el proveedor

// Validación:
// Si oauthProvider != null → oauthId debe tener valor
// Si oauthProvider = null → es login LOCAL
```

### Propuesta para Auditoría

```java
@Column(nullable = false, updatable = false)
private LocalDateTime createdAt;

@Column(nullable = false)
private LocalDateTime updatedAt;

// Usar @CreationTimestamp y @UpdateTimestamp de Hibernate:
@CreationTimestamp
@Column(nullable = false, updatable = false)
private LocalDateTime createdAt;

@UpdateTimestamp
@Column(nullable = false)
private LocalDateTime updatedAt;
```

---

## DTOs de Request

### `RegisterRequestDto` (Propuesto)

```java
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
}
```

### `UpdateUserDto` (Actual)

```java
public class UpdateUserDto {
  private String username;
  private String email;
}
```

**Problemas:** Sin validaciones `@Valid`.

**Propuesta:** Separar en dos DTOs:

```java
public class UpdateEmailDto {
  @Email(message = "Email must be valid")
  @NotBlank
  private String newEmail;
}

public class UpdateUsernameDto {
  @NotBlank
  @Size(min = 3, max = 50)
  private String newUsername;
}
```

### `ChangePasswordRequestDto` (Propuesto para Deuda D7)

```java
public class ChangePasswordRequestDto {
  @NotBlank(message = "Current password is required")
  private String currentPassword;
  
  @NotBlank(message = "New password is required")
  @Size(min = 8, message = "Password must be at least 8 characters")
  private String newPassword;
  
  @NotNull(message = "Password confirmation is required")
  private String confirmPassword;
}
```

---

## DTOs de Response

### `UserDto` (Actual)

```java
public class UserDto {
  private Long id;
  private String username;
  private String email;
  
  // getters (no setters)
}
```

**Limitaciones:** Sin role, sin estado deleted, sin timestamps.

**Propuesta mejorada:**

```java
public class UserDto {
  private Long id;
  private String username;
  private String email;
  private String role;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  
  // Omitir: password, deleted, oauthId, loginAttempts, etc.
}
```

### `UserDetailedDto` (Propuesto)

Para cuando el usuario solicita su propio perfil (Deuda: `myProfile()`):

```java
public class UserDetailedDto {
  private Long id;
  private String username;
  private String email;
  private String role;
  private Boolean deleted;  // Visible solo para usuario propio
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime lastLoginAt;
  private LocalDateTime deletedAt;  // Si está eliminado
}
```

### `RegisterResponseDto` (Propuesto)

```java
public class RegisterResponseDto {
  private Long id;
  private String username;
  private String email;
  private LocalDateTime createdAt;
}
```

### `LoginResponseDto` (Propuesto)

```java
public class LoginResponseDto {
  private String accessToken;
  private String tokenType;  // "Bearer"
  private Long expiresIn;    // Segundos
  private UserDto user;
}
```

---

## Relaciones (Ninguna Actualmente)

La entidad `UserModel` no tiene relaciones con otras entidades.

### Relaciones Futuras Posibles

| Relación | Entidad | Tipo | Prioridad |
|----------|---------|------|-----------|
| `User` 1 —→ N `UserProfile` | UserProfile | OneToMany | BAJA |
| `User` 1 —→ N `AuditLog` | AuditLog | OneToMany | MEDIA |
| `User` 1 —→ N `LoginAttempt` | LoginAttempt | OneToMany | BAJA |

---

## Repository: `UserRepository`

```java
@Repository
public interface UserRepository extends JpaRepository<UserModel, Long> {
  Optional<UserModel> findByUsername(String username);
  Optional<UserModel> findById(Long id);
  
  @Query("SELECT u FROM UserModel u WHERE " +
          "(:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
          "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
          "u.deleted = false")
  List<UserModel> searchUsers(@Param("username") String username, @Param("email") String email);
  
  List<UserModel> findByDeletedFalse();
}
```

### Métodos Actuales

| Método | Retorna | Cláusula |
|--------|---------|----------|
| `findByUsername(String)` | `Optional<UserModel>` | WHERE username = ? |
| `findById(Long)` | `Optional<UserModel>` | WHERE id = ? (generado) |
| `searchUsers(String, String)` | `List<UserModel>` | Custom JPQL |
| `findByDeletedFalse()` | `List<UserModel>` | WHERE deleted = false |

### Métodos Faltantes (Propuestos)

```java
// Buscar por email
Optional<UserModel> findByEmail(String email);

// Buscar por OAuth
Optional<UserModel> findByOauthProviderAndOauthId(String provider, String oauthId);

// Paginación
Page<UserModel> searchUsersPaginated(String username, String email, Pageable pageable);

// Soft-deleted
Optional<UserModel> findByUsernameAndDeletedTrue(String username);  // Para reactivar

// Auditoría
List<UserModel> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
```

---

## Diagrama ER (Entidad-Relación)

```
┌─────────────────────────────────────────┐
│             users                       │
├─────────────────────────────────────────┤
│ id (PK)                    BIGSERIAL    │
│ username (UNIQUE)          VARCHAR(255) │
│ password                   VARCHAR(255) │
│ email (UNIQUE)             VARCHAR(255) │
│ role                       VARCHAR(50)  │
│ deleted                    BOOLEAN      │
│                                         │
│ [FUTURO]                                │
│ deleted_at                 TIMESTAMP    │
│ created_at                 TIMESTAMP    │
│ updated_at                 TIMESTAMP    │
│ oauth_provider             VARCHAR(50)  │
│ oauth_id                   VARCHAR(255) │
│ oauth_email                VARCHAR(255) │
│ login_attempts             INTEGER      │
│ last_login_at              TIMESTAMP    │
└─────────────────────────────────────────┘
```

---

## Script SQL de Migración (Futura)

### Agregar campos para Deuda D5 (softDeleteUser mejorado)

```sql
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP NULL;

-- Actualizar registros ya eliminados:
UPDATE users SET deleted_at = NOW() WHERE deleted = true;
```

### Agregar campos para auditoría

```sql
ALTER TABLE users ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

-- Actualizar registros existentes (aproximado):
UPDATE users SET created_at = NOW(), updated_at = NOW();

-- Crear índice para búsquedas por fecha:
CREATE INDEX idx_users_created_at ON users(created_at);
```

### Agregar campos para OAuth2 (Deuda D3)

```sql
ALTER TABLE users ADD COLUMN oauth_provider VARCHAR(50) NULL;
ALTER TABLE users ADD COLUMN oauth_id VARCHAR(255) NULL UNIQUE;
ALTER TABLE users ADD COLUMN oauth_email VARCHAR(255) NULL;

-- Índice para búsqueda rápida por OAuth:
CREATE INDEX idx_users_oauth ON users(oauth_provider, oauth_id);
```

### Agregar campos para rate limiting

```sql
ALTER TABLE users ADD COLUMN login_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP NULL;
```

---

## Validaciones a Nivel de Base de Datos

```sql
-- Ya existen (implícitos en JPA):
ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE(username);
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE(email);

-- Recomendados (agregar):
ALTER TABLE users ADD CONSTRAINT ck_users_role 
  CHECK(role IN ('USER', 'ADMIN'));

ALTER TABLE users ADD CONSTRAINT ck_users_oauth 
  CHECK((oauth_provider IS NULL AND oauth_id IS NULL) OR 
        (oauth_provider IS NOT NULL AND oauth_id IS NOT NULL));

-- Índices para performance:
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_deleted ON users(deleted);
CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted = true;
```

---

**Documento:** 06-MODELO_DATOS.md | **Versión:** 1.0 | **Fecha:** 2026-05-22
