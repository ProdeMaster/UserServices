# Guía de Implementación — UserService
**Proyecto:** ProdeMaster · Microservicio UserService  
**Generado:** 2026-05-28  
**Versión del documento:** 1.0.0

---

## 1. Resumen Ejecutivo
El microservicio [UserService](..) es un componente modular de la plataforma **ProdeMaster** desarrollado bajo el framework Spring Boot (v3.4.3) y Java 17. Actualmente, implementa un esquema de seguridad perimetral local mediante Spring Security y JSON Web Tokens (JWT) utilizando el algoritmo simétrico HMAC256 con una clave secreta hardcodeada en los archivos de configuración.

El análisis estático del código revela un acoplamiento estrecho y redundante en el flujo de validación de tokens en múltiples capas (filtro, controladores y servicios), así como deficiencias de diseño críticas en el ciclo de vida del usuario (borrado lógico incompleto y endpoints administrativos mal diseñados). El alcance de esta guía es definir una hoja de ruta ordenada para eliminar la validación redundante local, migrar el esquema criptográfico de firma de JWT a claves asimétricas RSA (RS256), integrar autenticación social mediante OAuth2, y corregir o implementar endpoints del negocio (borrado lógico robusto, reactivación interna y cambio de contraseña seguro) sin comprometer el funcionamiento de la aplicación actual.

---

## 2. Mapa de la Arquitectura Actual
El microservicio está estructurado bajo un diseño clásico multicapa (Controlador-Servicio-Repositorio) con inyección de dependencias y Spring Security acoplado al filtro JWT local:

```
+-----------------------------------------------------------------------------------+
|                                  CAPA DE ENTRADA                                  |
|  [AuthController](../src/main/java/com/ProdeMaster/UserServices/Controller/AuthController.java)  |  [UserController](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java) |
|  (/auth/login, /auth/register)                 (/user/**, /user/search)           |
+----------------------------------------+------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                                 CAPA DE SEGURIDAD                                 |
|  [SecurityConfig](../src/main/java/com/ProdeMaster/UserServices/Security/SecurityConfig.java)  <--  [JwtAuthenticationFilter](../src/main/java/com/ProdeMaster/UserServices/Security/JwtAuthenticationFilter.java)         |
|                                        |                                          |
|                                        v                                          |
|                                 [JwtUtil](../src/main/java/com/ProdeMaster/UserServices/Security/JwtUtil.java) (Firma: HMAC256)                 |
+----------------------------------------+------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                                 CAPA DE SERVICIO                                  |
|                                   [UserService](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java)                             |
+----------------------------------------+------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                               CAPA DE PERSISTENCIA                                |
|                                 [UserRepository](../src/main/java/com/ProdeMaster/UserServices/Repository/UserRepository.java)                          |
|                                        |                                          |
|                                        v                                          |
|                                   [UserModel](../src/main/java/com/ProdeMaster/UserServices/Model/UserModel.java) (Tabla: "users")                     |
+-----------------------------------------------------------------------------------+
```

### Flujo de Seguridad y Validación de Token Actual:
1. El cliente envía sus credenciales a `/auth/login`. El servicio valida la información y genera un JWT con el algoritmo **HMAC256** firmado con el secreto simétrico de [application.properties](../src/main/resources/application.properties) (`jwt.secret=MI-CLAVE-SUPER-SEGURA`).
2. Para peticiones protegidas `/user/**`, el cliente adjunta el encabezado `Authorization: Bearer <token>`.
3. El filtro [JwtAuthenticationFilter](../src/main/java/com/ProdeMaster/UserServices/Security/JwtAuthenticationFilter.java) intercepta la petición, extrae el token y llama a [JwtUtil.validateToken()](../src/main/java/com/ProdeMaster/UserServices/Security/JwtUtil.java#L45-L55) para validar su autenticidad. Si es correcto, establece la autenticación en el `SecurityContextHolder`.
4. **Validación Redundante:** Múltiples endpoints en [UserController](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java) y [UserService](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java) reciben de forma manual el encabezado `Authorization` y vuelven a decodificar y validar el token usando el utilitario de JWT (ej. `updateUser`, `getUser`, `deleteUser` y `searchUsers`), generando un alto acoplamiento e ineficiencia.

---

## 3. Plan de Implementación por Feature

> Las features están ordenadas por dependencia lógica, no por prioridad de negocio.
> El equipo puede reordenar respetando el orden de prerrequisitos indicado.

### F-01 · Eliminar Validación JWT en UserService
**Prerequisitos:** Ninguno  
**Complejidad:** Baja  
**Archivos impactados:**
- [JwtAuthenticationFilter.java](../src/main/java/com/ProdeMaster/UserServices/Security/JwtAuthenticationFilter.java) — **[DELETE]** (Eliminar clase completa)
- [SecurityConfig.java](../src/main/java/com/ProdeMaster/UserServices/Security/SecurityConfig.java) — **[MODIFY]** (Eliminar registro de filtro local e inyectar filtro del API Gateway)
- [ApiGatewayAuthFilter.java](../src/main/java/com/ProdeMaster/UserServices/Security/ApiGatewayAuthFilter.java) — **[NEW]** (Crear filtro de entrada que confíe en la identidad provista por el Gateway)
- [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java) — **[MODIFY]** (Eliminar parámetros de token y validaciones manuales redundantes)
- [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java) — **[MODIFY]** (Refactorizar firmas eliminando el parámetro `token` y quitar lógica redundante de validación)
- [JwtUtil.java](../src/main/java/com/ProdeMaster/UserServices/Security/JwtUtil.java) — **[MODIFY]** (Remover el método `validateToken(String token)`)

**Pasos de implementación:**
1. Crear una nueva clase `ApiGatewayAuthFilter` que extienda de `OncePerRequestFilter`. Su lógica leerá el encabezado HTTP acordado (p. ej. `X-Authenticated-User`) que enviará el API Gateway tras validar el JWT original.
2. En `ApiGatewayAuthFilter.doFilterInternal()`, si el encabezado de identidad existe, construir un objeto `UserDetails` genérico con los roles/authorities correspondientes y guardarlo en el `SecurityContextHolder.getContext().setAuthentication(...)`.
3. Eliminar el archivo [JwtAuthenticationFilter.java](../src/main/java/com/ProdeMaster/UserServices/Security/JwtAuthenticationFilter.java).
4. Modificar [SecurityConfig.java](../src/main/java/com/ProdeMaster/UserServices/Security/SecurityConfig.java) para eliminar la inyección del antiguo filtro local y registrar `ApiGatewayAuthFilter` en la cadena mediante `.addFilterBefore(apiGatewayAuthFilter, SessionManagementFilter.class)`.
5. Modificar los endpoints de [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java):
   - Eliminar el parámetro `@RequestHeader("Authorization") String token` de los métodos.
   - Obtener el nombre de usuario autenticado usando: `SecurityContextHolder.getContext().getAuthentication().getName()`.
6. Actualizar las firmas de los métodos del servicio en [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java) (`updateUser`, `softDeleteUser`, `searchUsers`) para recibir la cadena `username` directamente en lugar del `token` de autorización, y eliminar las líneas que ejecutan `jwtUtil.validateToken()`.
7. Eliminar el método `validateToken` en [JwtUtil.java](../src/main/java/com/ProdeMaster/UserServices/Security/JwtUtil.java).

**Criterios de aceptación:**
- [ ] La validación criptográfica de tokens ya no se realiza a nivel del microservicio `UserService`.
- [ ] Peticiones directas sin el encabezado del Gateway son rechazadas (retornan 401/403).
- [ ] El microservicio compila limpiamente sin deudas ni llamadas a métodos huérfanos de validación.

**Riesgos y consideraciones:**
- La seguridad pasa a depender del API Gateway de forma absoluta. Es indispensable asegurar (por ejemplo, mediante configuración de red o MTLS) que los puertos y endpoints del microservicio no sean directamente accesibles desde el exterior de la infraestructura y sólo acepten tráfico proveniente del Gateway.

---

### F-02 · Migrar Firma JWT a RSA
**Prerequisitos:** F-01 (recomendado)  
**Complejidad:** Media  
**Archivos impactados:**
- [JwtUtil.java](../src/main/java/com/ProdeMaster/UserServices/Security/JwtUtil.java) — **[MODIFY]** (Actualizar constructor para leer y parsear el archivo `.pem` y modificar `generateToken` para firmar usando claves asimétricas RSA)
- [application.properties](../src/main/resources/application.properties) — **[MODIFY]** (Eliminar secreto simétrico e incorporar variables de ruta del certificado de clave privada)
- [pom.xml](../pom.xml) — **[MODIFY]** (Validar y asegurar que cuenta con las dependencias necesarias de cifrado o compatibilidad si se requiere BouncyCastle)

**Pasos de implementación:**
1. Generar el par de claves RSA si es necesario. Dado que `certs/private.pem` ya existe, se debe extraer su clave pública para distribuirla con el Gateway que validará las firmas. Comando útil:  
   `openssl rsa -in src/main/resources/certs/private.pem -pubout -out src/main/resources/certs/public.pem`
2. Modificar [application.properties](../src/main/resources/application.properties): eliminar `jwt.secret` y agregar la propiedad `jwt.private-key-path=classpath:certs/private.pem`.
3. En [JwtUtil.java](../src/main/java/com/ProdeMaster/UserServices/Security/JwtUtil.java), inyectar la clave privada mediante `@Value("${jwt.private-key-path}") Resource privateKeyResource`.
4. En el constructor de `JwtUtil`, leer el contenido de la clave del archivo de recurso, remover las cabeceras `-----BEGIN PRIVATE KEY-----` / `-----END PRIVATE KEY-----` y los saltos de línea, decodificar el contenido Base64 restante a bytes y generar el objeto `PrivateKey` mediante la clase `KeyFactory` y el tipo de codificación `PKCS8EncodedKeySpec`.
5. Modificar el método `generateToken(String username)` cambiando la llamada de firma del token: de `Algorithm.HMAC256(secretKey)` a `Algorithm.RSA256(null, privateKey)`.

**Criterios de aceptación:**
- [ ] Los tokens devueltos al autenticar contienen en el Header de firma la cadena `"alg": "RS256"`.
- [ ] Se remueve la propiedad `jwt.secret` sin romper el arranque de la aplicación.
- [ ] Los tests unitarios logran validar que el token emitido corresponde con la firma asimétrica cargada.

**Riesgos y consideraciones:**
- La clave privada en `src/main/resources/certs/` NO debe commitearse en texto plano; verificar que esté en `.gitignore` o cifrada. Para entornos de desarrollo o tests se puede mantener un par local, pero para entornos productivos reales el archivo de clave privada debe ser inyectado externamente (ej. Kubernetes Secrets, AWS Secrets Manager o Spring Cloud Config cifrado).

---

### F-03 · Autenticación OAuth2 (Facebook / Google)
**Prerequisitos:** F-02  
**Complejidad:** Alta  
**Archivos impactados:**
- [pom.xml](../pom.xml) — **[MODIFY]** (Incorporar dependencia del cliente de seguridad OAuth2)
- [application.properties](../src/main/resources/application.properties) — **[MODIFY]** (Definir propiedades de proveedores Google/Facebook, ID de clientes y claves)
- [SecurityConfig.java](../src/main/java/com/ProdeMaster/UserServices/Security/SecurityConfig.java) — **[MODIFY]** (Configurar filtro `.oauth2Login` y definir handler de éxito)
- [OAuth2SuccessHandler.java](../src/main/java/com/ProdeMaster/UserServices/Security/OAuth2SuccessHandler.java) — **[NEW]** (Procesar perfil obtenido externamente y generar el token JWT propio)
- [UserModel.java](../src/main/java/com/ProdeMaster/UserServices/Model/UserModel.java) — **[MODIFY]** (Permitir passwords nulos para cuentas sociales e incorporar campos `oauthProvider` y `oauthId`)

**Pasos de implementación:**
1. Agregar la dependencia `spring-boot-starter-oauth2-client` en el archivo `pom.xml`.
2. Registrar las propiedades requeridas para Google y Facebook client providers en `application.properties`:
   ```properties
   spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
   spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
   spring.security.oauth2.client.registration.google.scope=profile,email
   spring.security.oauth2.client.registration.facebook.client-id=${FACEBOOK_CLIENT_ID}
   spring.security.oauth2.client.registration.facebook.client-secret=${FACEBOOK_CLIENT_SECRET}
   spring.security.oauth2.client.registration.facebook.scope=public_profile,email
   ```
3. Modificar la clase `UserModel` para incluir las propiedades de texto `oauthProvider` y `oauthId`, permitiendo que el campo de contraseña sea opcional (nulo en base de datos) para usuarios que no utilicen el registro tradicional local.
4. Crear la clase `OAuth2SuccessHandler` (implementando `AuthenticationSuccessHandler`). Esta clase extraerá el correo electrónico y el nombre del perfil de la llamada externa de éxito, consultará si el usuario existe en `UserRepository` y, si no existe, creará un nuevo registro en la DB guardando el ID del proveedor OAuth2 correspondiente.
5. Tras validar/crear al usuario, `OAuth2SuccessHandler` generará un JWT RSA local y redirigirá al cliente frontend inyectando el token.
6. En `SecurityConfig.java`, agregar la configuración `.oauth2Login(oauth2 -> oauth2.successHandler(oauth2SuccessHandler))`.

**Criterios de aceptación:**
- [ ] Flujo de login expone redirecciones oficiales de Google y Facebook.
- [ ] La autenticación social exitosa registra el perfil en base de datos de usuarios locales (si es un email nuevo) mapeando sus datos básicos.
- [ ] El microservicio devuelve una firma JWT propia en el callback final.

**Riesgos y consideraciones:**
- Colisión de email: Si un usuario con registro local tradicional intenta iniciar sesión mediante Google con el mismo email, la lógica debe gestionar si actualiza el perfil vinculándolo con el proveedor OAuth2 o rechazar la acción con un error de conflicto.

---

### F-04 · Generación de Colección Postman (OpenCode)
**Prerequisitos:** Ninguno (puede hacerse en paralelo)  
**Complejidad:** Baja  
**Pasos de implementación:**
1. Ejecutar la herramienta externa OpenCode apuntando a la ruta raíz del microservicio `UserService` con el prompt:  
   _"Generate a Postman collection JSON file covering all REST endpoints of this Spring Boot microservice, including authentication headers, example request bodies, and environment variables for base URL and JWT token."_
2. Validar que la colección devuelta en formato `.json` contenga la estructura descrita en los controladores de la aplicación (endpoints de `/auth` y `/user`), incluyendo ejemplos de respuestas HTTP habituales y variables globales de entorno.
3. Guardar el archivo como `UserService.postman_collection.json`.

**Criterios de aceptación:**
- [ ] El archivo de salida en formato JSON es estructuralmente correcto e importable en Postman (versión v2.1+).
- [ ] Cubre el 100 % de los endpoints de `/auth` y `/user` de la aplicación.

---

### F-05 · Agregar Colección Postman al Repositorio
**Prerequisitos:** F-04  
**Complejidad:** Baja  
**Pasos de implementación:**
1. Crear el directorio `src/postman` si no existe en la ruta de trabajo.
2. Copiar el archivo generado `UserService.postman_collection.json` en dicha carpeta.
3. Asegurar que la ruta `src/postman/` no está catalogada en el archivo `.gitignore`.
4. Añadir el archivo mediante control de cambios git (`git add`) y realizar commit utilizando mensajes convencionales estandarizados: `docs: add Postman collection for UserService`.

**Criterios de aceptación:**
- [ ] El archivo de colección Postman se ubica exactamente en la ruta `./src/postman/UserService.postman_collection.json`.
- [ ] El archivo se encuentra correctamente versionado dentro del repositorio Git del proyecto.

---

### F-06 · Verificar y Corregir Método DeleteUser
**Prerequisitos:** F-01 (recomendado, para desacoplar el token del servicio)  
**Complejidad:** Media  
**Archivos impactados:**
- [UserModel.java](../src/main/java/com/ProdeMaster/UserServices/Model/UserModel.java) — **[MODIFY]** (Agregar atributo de marca temporal de borrado `deletedAt`)
- [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java) — **[MODIFY]** (Mejorar lógica de guardado de soft delete y retornar respuestas óptimas sin re-consultar a DB)
- [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java) — **[MODIFY]** (Evitar llamadas propensas a error de tipo `NoSuchElementException` y refactorizar códigos HTTP de retorno)

**Hallazgos Críticos Identificados:**
1. **Llamada insegura al Optional**: En `UserController.java` (L.58), se invoca `user.get().getUsername()` de forma directa sobre el valor de retorno. Si no se encontrara el usuario, arrojaría una excepción `NoSuchElementException` no controlada (retornando HTTP 500 al cliente).
2. **Deficiencia de auditoría física**: La bandera lógica `deleted = true` funciona correctamente, pero carece de un campo de marca de tiempo (`deleted_at`) que indique el momento exacto de la eliminación.
3. **Ineficiencia de persistencia y consultas**: El método `softDeleteUser` en `UserService` ejecuta una consulta redundante de recuperación `userRepository.findByUsername(userName)` tras haber guardado la entidad que ya tiene en memoria para poder retornar el DTO final.
4. **Manejo de Excepciones**: Si el usuario no existe, arroja una excepción genérica `RuntimeException` que el controlador no atrapa, provocando fallas HTTP 500 en lugar de códigos limpios de error.

**Pasos de implementación:**
1. Modificar [UserModel.java](../src/main/java/com/ProdeMaster/UserServices/Model/UserModel.java): añadir el campo `private LocalDateTime deletedAt` con sus getters y setters.
2. Refactorizar el método `softDeleteUser(String username)` en [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java):
   - Obtener al usuario mediante `userRepository.findByUsername(username)`. Si está ausente, lanzar una excepción personalizada `UserNotFoundException`.
   - Validar si el usuario ya está eliminado (`user.getDeleted() == true`). Si es el caso, lanzar una excepción `UserAlreadyDeletedException`.
   - Asignar `deleted = true` y `deletedAt = LocalDateTime.now()`.
   - Guardar los cambios en el repositorio.
   - Retornar un `UserDto` directamente con la información de la entidad modificada en memoria.
3. En [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java), refactorizar `deleteUser`:
   - Cambiar la firma y el flujo para que capture la identidad autenticada desde el contexto de seguridad.
   - Invocar la función del servicio de borrado seguro.
   - Envolver el flujo en excepciones personalizadas (capturadas por un manejador de excepciones global) para responder con códigos HTTP 404 (Not Found) o 409 (Conflict/Already Deleted) en lugar de HTTP 500.

**Criterios de aceptación:**
- [ ] La tabla de base de datos actualiza correctamente los campos `deleted = true` y guarda la fecha/hora en la columna `deleted_at`.
- [ ] Peticiones a usuarios inexistentes devuelven HTTP 404.
- [ ] Peticiones redundantes sobre usuarios ya borrados lógicamente devuelven HTTP 409 Conflict.
- [ ] No existen llamadas directas a `.get()` sobre `Optional` sin validación previa.

---

### F-07 · Método Interno de Reactivación de Usuario
**Prerequisitos:** F-06  
**Complejidad:** Media  
**Archivos impactados:**
- [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java) — **[MODIFY]** (Reescribir el actual `ActiveUser` para usar el ID del usuario en lugar de sus credenciales, limpiar la fecha de borrado y actualizar el estado lógico)
- [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java) — **[MODIFY]** (Exponer el endpoint REST correspondiente protegido para accesos administrativos)

**Notas de diseño:**
- **Inconsistencia de Uso:** El método actual `ActiveUser(String username, String password)` en `UserService` es incorrecto desde el punto de vista del negocio: fuerza al usuario a introducir sus credenciales para ser reactivado de forma manual, en lugar de ser una operación interna/administrativa.
- **Protección Endpoint:** La reactivación se debe exponer únicamente para roles de administrador (`ROLE_ADMIN`) o llamadas exclusivas desde la red interna de microservicios usando la anotación `@PreAuthorize("hasRole('ADMIN')")`.
- **Lógica de fechas:** El campo `deletedAt` de la base de datos debe ser reestablecido a `null` al restaurar el estado del registro a activo (`deleted = false`).

**Pasos de implementación:**
1. Modificar [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java):
   - Reemplazar el método obsoleto `ActiveUser(String username, String password)` por `public UserDto reactivateUser(Long id)`.
   - Recuperar al usuario por su ID mediante `userRepository.findById(id)`. Si no se encuentra, lanzar `UserNotFoundException`.
   - Si el usuario no tiene la bandera `deleted = true`, lanzar una excepción `UserNotDeletedException`.
   - Cambiar la bandera `deleted = false` y asignar `deletedAt = null`.
   - Guardar cambios en DB y retornar el `UserDto` del usuario recuperado.
2. En [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java), crear el endpoint `POST /user/{id}/reactivate`.
3. Asegurar el acceso usando la anotación `@PreAuthorize("hasRole('ADMIN')")`.

**Criterios de aceptación:**
- [ ] Solo usuarios autenticados con rol de administrador (`ADMIN`) tienen permitido llamar al endpoint de reactivación.
- [ ] La reactivación reestablece `deleted = false` y `deleted_at = null` de la entidad en base de datos.
- [ ] Intentar reactivar a un usuario que ya se encuentra activo retorna un código HTTP 409 Conflict.

---

### F-08 · Modificación de Contraseña
**Prerequisitos:** Ninguno  
**Complejidad:** Media  
**Archivos impactados:**
- [ChangePasswordRequestDto.java](../src/main/java/com/ProdeMaster/UserServices/Dto/ChangePasswordRequestDto.java) — **[NEW]** (Crear DTO seguro para capturar datos de contraseñas)
- [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java) — **[MODIFY]** (Implementar lógica de cambio cifrado y validaciones de coincidencia)
- [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java) — **[MODIFY]** (Exponer el endpoint público protegido para cambio de contraseña)

**Notas de diseño:**
- **Validación robusta:** El sistema debe verificar que la contraseña actual enviada coincida con la registrada en la DB (usando `BCryptPasswordEncoder.matches()`) antes de actualizar la información.
- **Seguridad en hashing:** Aplicar el mismo componente encoder del framework (`BCrypt`) para encriptar la nueva clave del usuario.
- **Control de accesos:** Restringir el método para que un usuario normal sólo pueda modificar su propia clave personal (verificando que el ID de la ruta coincida con el nombre de usuario autenticado en sesión) o permitirlo si el rol es de un administrador.

**Pasos de implementación:**
1. Crear el DTO `ChangePasswordRequestDto` con atributos para `currentPassword`, `newPassword` y `confirmNewPassword` con validaciones `@NotBlank` y `@Size`.
2. En [UserService.java](../src/main/java/com/ProdeMaster/UserServices/Service/UserService.java), implementar `public void changePassword(String username, ChangePasswordRequestDto request)`:
   - Buscar al usuario por username en la base de datos. Si no existe, lanzar `UserNotFoundException`.
   - Comparar `request.getCurrentPassword()` con la clave encriptada de la DB usando `passwordEncoder.matches()`. Si no coincide, lanzar `InvalidCurrentPasswordException`.
   - Validar que la nueva contraseña coincida con su confirmación (`request.getNewPassword().equals(request.getConfirmNewPassword())`). De lo contrario, lanzar `PasswordMismatchException`.
   - Validar que la contraseña nueva no sea idéntica a la antigua. Si es igual, lanzar `SamePasswordException`.
   - Codificar la clave mediante `passwordEncoder.encode(request.getNewPassword())`, actualizar la entidad y guardarla.
3. En [UserController.java](../src/main/java/com/ProdeMaster/UserServices/Controller/UserController.java), exponer el endpoint `POST /user/{id}/change-password`:
   - Validar que el usuario que realiza la petición sea el propietario del ID provisto en la ruta o sea un `ADMIN`.
   - Invocar el método del servicio y responder con HTTP 200 ante éxito.

**Criterios de aceptación:**
- [ ] El endpoint requiere que la contraseña actual coincida exactamente con la registrada.
- [ ] La nueva clave se almacena encriptada bajo hashing BCrypt de forma exitosa.
- [ ] No es posible cambiar contraseñas ajenas sin poseer rol de administrador.
- [ ] Si las contraseñas nueva y confirmación no coinciden, se devuelve un error de tipo HTTP 400 Bad Request.

---

## 4. Mejoras Arquitectónicas Recomendadas
Para asegurar el crecimiento del microservicio en sprints posteriores, se recomienda priorizar las mejoras restantes bajo el siguiente esquema:

1. **Implementar Manejador de Excepciones Global (`@ControllerAdvice`)** — *Sprint Inmediato / Fase 2.5*:  
   Es fundamental para capturar las excepciones de negocio (`UserNotFoundException`, `UserAlreadyDeletedException`, etc.) y evitar fugas de trazas internas convirtiéndolas a respuestas estructuradas HTTP limpias para los clientes.
2. **Paginación en consultas de búsqueda (`/user/search`)** — *Sprint Inmediato*:  
   Cambiar el retorno directo de `Stream` por interfaces `Page<UserDto>` y paginación JPA nativa para optimizar el rendimiento de la base de datos ante listados de datos voluminosos.
3. **Módulo de bloqueo de accesos y seguridad de fuerza bruta** — *Sprint 2*:  
   Implementar el contador de inicios de sesión fallidos para incrementar la protección del endpoint de autenticación contra accesos no autorizados.
4. **Implementar Endpoint `/user/me` y DTO extendido** — *Sprint 2*:  
   Añadir el soporte para consultar perfiles propios detallados sin comprometer la privacidad expuesta en perfiles públicos.

---

## 5. Orden de Implementación Sugerido (Roadmap)

```mermaid
gantt
    title UserService — Roadmap de Features
    dateFormat  YYYY-MM-DD
    section Seguridad
    F-01 Eliminar validación JWT     :f01, 2026-05-28, 2d
    F-02 RSA en JWT                  :f02, after f01, 3d
    F-03 OAuth2                      :f03, after f02, 5d
    section Usuarios
    F-06 Verificar DeleteUser        :f06, 2026-05-28, 1d
    F-07 Reactivar usuario           :f07, after f06, 2d
    F-08 Cambio de contraseña        :f08, 2026-05-28, 2d
    section Documentación
    F-04 Generar colección Postman   :f04, 2026-05-28, 1d
    F-05 Agregar colección al repo   :f05, after f04, 1d
```

---

## 6. Dependencias entre Features

| Feature | Depende de | Puede hacerse en paralelo con |
|---------|------------|-------------------------------|
| **F-01** (Eliminar validación JWT) | — | F-04, F-06, F-08 |
| **F-02** (Firma JWT a RSA) | **F-01** (Recomendado) | F-03 |
| **F-03** (Autenticación OAuth2) | **F-02** | — |
| **F-04** (Generar colección Postman) | — | F-01, F-06, F-08 |
| **F-05** (Agregar Postman al repo) | **F-04** | — |
| **F-06** (Verificar/corregir DeleteUser) | — | F-01, F-04, F-08 |
| **F-07** (Reactivación de usuario) | **F-06** | — |
| **F-08** (Modificación de contraseña) | — | F-01, F-04, F-06 |

---

## 7. Checklist de Validación Final

- [ ] Todas las features implementadas tienen su test unitario correspondiente.
- [ ] La colección Postman cubre el 100 % de los endpoints de la aplicación.
- [ ] La clave privada RSA está correctamente excluida del repositorio o cifrada en git mediante gitignore.
- [ ] El endpoint de reactivación lógica de usuario no es accesible públicamente (requiere rol de administrador).
- [ ] `DeleteUser` funciona correctamente como borrado lógico, registrando auditoría en `deleted_at`.
- [ ] El cambio de contraseña valida correctamente la clave previa del usuario.
- [ ] Las mejoras arquitectónicas remanentes cuentan con sus respectivos tickets en el backlog del equipo de desarrollo.
- [ ] Esta guía (`docs/GUIA_IMPLEMENTACION.md`) está commiteada de forma exitosa en el repositorio de código.

---

## 8. Glosario

| Término | Definición |
|---------|------------|
| **RSA** | Algoritmo criptográfico asimétrico de clave pública/privada (ej. RS256 en JWT). |
| **OAuth2** | Protocolo de autorización estándar para el acceso delegado mediante delegación de credenciales. |
| **Borrado lógico** | Estado de eliminación virtual del registro en base de datos sin destrucción física del dato. |
| **OpenCode** | Herramienta externa de generación automatizada de colecciones y componentes basada en Inteligencia Artificial. |
| **ProdeMaster** | Sistema matriz de múltiples microservicios que aloja a `UserService` como servicio gestor de usuarios. |
