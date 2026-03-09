# 🌐 Use Service

Este proyecto implementa un servicio de usuarios que gestiona el registro, autenticación (login) y operaciones CRUD sobre los datos de usuario. Es parte de un sistema de microservicios que utiliza Eureka para el descubrimiento de servicios.

---

## 🚀 ¿Qué hace este servicio?

El User Service proporciona una API REST para:

* Registro de nuevos usuarios
* Autenticación de usuarios mediante JWT
* Lectura, actualización y eliminación de datos de usuario
* Validación de credenciales de acceso 

Este servicio se registra automáticamente en el servidor de Eureka para que otros servicios (como el ApiGateway) puedan enrutar solicitudes hacia él.

---

## 📆 Endpoints destacados

| Método | Ruta                        | Descripción                              |
|--------|-----------------------------|------------------------------------------|
| POST   | /user-service/auth/register | Registro de usuario                      |
| POST   | /user-service/auth/login    | Autenticación (devuelve JWT)             |
| GET    | /user-service/users/        | Devuelve el nombre de todos los usuarios |
| GET    | /user-service/users/{id}    | Obtener info del usuario                 |

## 🛠️ Tecnologías

- Java 17
- Spring Boot 3.4.x
- Spring Security (con JWT)
- Spring Data JPA
- PostgreSQL
- Spring Cloud Netflix Eureka Client
- Docker & Docker Compose

---

## 📁 Estructura del proyecto

```plaintext
user-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ProdeMaster/UserService/
│   │   │       ├── Controller/
│   │   │       │   ├── AuthController.java
│   │   │       │   └── UserController.java
│   │   │       ├── Model/
│   │   │       │   └── UserModel.java       
│   │   │       ├── Repository/
│   │   │       │   └── UserRepository.java
│   │   │       ├── Security/
│   │   │       │   ├── JwtAuthenticationFilter.java
│   │   │       │   ├── JwtUtils.java
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── Service/
│   │   │       │   └──UserService.java
│   │   │       └── UserServiceApplication.java
│   │   └── resources/
│   │       ├── static/
│   │       ├── templates/
│   │       └── application.properties
│   └── test/java/com/ProdeMaster/UserServices/
│       └── UserModelServicesApplicationTests.java
├── Dockerfile
├── pom.xml
└── readme.md
```

---

## ⚙️ Configuración

El archivo `application.properties` contiene la configuración principal del servicio de usuarios, incluyendo puertos, nombre del servicio y detalles de conexión a Eureka.

### application.properties
```plaintext
spring.application.name=user-service
server.port=8081

#Conectividad con zipkin para manejo de trazabilidad
spring.zipkin.base-url=http://zipkin:9411
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
management.tracing.sampling.probability=1.0

#Configuracion de Eureka
eureka.client.serviceUrl.defaultZone=http://eureka:8761/eureka/
eureka.instance.prefer-ip-address=true

#Configuracion de la base de datos PostgreSQL
spring.datasource.url=jdbc:postgresql://postgres:5432/prode_db
spring.datasource.username=postgres
spring.datasource.password=admin
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true


#Configuracion del JWT
jwt.secret=MI-CLAVE-SUPER-SEGURA
jwt.expiration=86400000
```
> ⚠️ **IMPORTANTE**: Tanto las credenciales de Postgres, como el secret key de jwt son valores de ejemplo proporcionados para facilitar el despliegue en local y las pruebas que se deseen realizar. Bajo ningún concepto se recomienda conservar estos valores si desea llevar el servicio a producción 

---

## 🧪 Cómo probarlo en local
### ✅ Requisitos
- JDK 17
- Maven
- Docker

#### Opción 1: sin Docker
```bash
mvn clean install
mvn spring-boot:run
```

> 💡 **NOTA:** Es necesario disponer de un servicio de Postgres, con las credenciales anteriormente mencionadas, para que el servicio se conecte.
>
> Es necesario modificar la propiedad `spring.datasource.url` configurándolo de la siguiente manera `jdbc:postgresql://localhost:5432/prode_db`


#### Opción 2: con Docker
```bash
docker image build -t user-service .
docker run -p 5432:5432 --name my-postgres -e POSTGRES_PASSWORD=admin -e POSTGRES_USER=postgres -e POSTGRES_DB=prode_db postgres:17
docker run user-service
```

---

## 📦 Docker

### Dockerfile

El servicio cuenta con un Dockerfile optimizado para producción basado en una imagen Java 17.

---

## 🧩 Integración con otros servicios

📂 Integración con otros servicios

Este servicio se registra en Eureka bajo el nombre user-service. Puede ser accedido a través del ApiGateway usando:

```bash
http://localhost:8080/user-service/api/users
```

Asegúrate de que `spring.application.name=user-service` coincida con la ruta configurada en ApiGateway.

---

## 📚 Documentación adicional
* [Spring Boot](https://docs.spring.io/spring-boot/index.html)
* [Spring Security](https://docs.spring.io/spring-security/reference/)
* [Spring cloud](https://docs.spring.io/spring-cloud/docs/current/reference/html/)
* [Ciclo de vida de las aplicaciones con Maven](https://keepcoding.io/blog/que-es-maven-lifecycle-y-sus-fases/)

## 🧑‍💻 Autor
> Nombre: Gastón Herrlein
>
> GitHub: @Gaston-Herrlein

## 📄 Licencia
Sin licencia