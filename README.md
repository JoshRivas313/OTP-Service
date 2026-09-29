<h1 align="center">OTP Service</h1>

<p align="center">
  API para generar y verificar códigos de un solo uso (OTP) y enviarlos por SMS
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 4.1.1" src="https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white">
  <img alt="MongoDB" src="https://img.shields.io/badge/MongoDB-persistencia-47A248?logo=mongodb&logoColor=white">
  <img alt="Licencia MIT" src="https://img.shields.io/badge/Licencia-MIT-blue">
</p>

Backend en Spring Boot que genera códigos de un solo uso, los envía por SMS al celular del usuario y los verifica de forma segura y atómica. El proveedor de SMS es intercambiable (consola, Twilio o Infobip) y el proyecto incluye una interfaz web y documentación interactiva con Swagger.

## Tabla de Contenido

- [Descripción General](#descripción-general)
- [Características Principales](#características-principales)
- [Stack Tecnológico](#stack-tecnológico)
- [Arquitectura del Sistema](#arquitectura-del-sistema)
- [Módulos del Sistema](#módulos-del-sistema)
- [Documentación Detallada](#documentación-detallada)
- [Instalación y Configuración](#instalación-y-configuración)
- [Variables de Entorno](#variables-de-entorno)
- [Scripts Disponibles](#scripts-disponibles)
- [API Endpoints](#api-endpoints)
- [Limitaciones Conocidas](#limitaciones-conocidas)
- [Contribución](#contribución)

---

## Descripción General

OTP Service es una API REST desarrollada con **Spring Boot** para verificar la posesión de un celular mediante un código enviado por SMS:

- El backend genera el código, guarda solo su hash, controla la expiración y los intentos, y decide si un código es válido.
- Un proveedor de SMS, intercambiable por configuración, solo se encarga de entregar el mensaje.
- Un mismo código solo puede verificarse una vez, aunque lleguen dos peticiones a la vez.
- La interfaz web permite probar el flujo completo conectando una cuenta propia de Twilio.

### Qué hace el backend y qué hace el proveedor de SMS

| Backend (Spring Boot y MongoDB) | Proveedor de SMS (Twilio o Infobip) |
|---|---|
| Genera el código con `SecureRandom` | Entrega el SMS al celular |
| Guarda su hash HMAC-SHA256 y su expiración | Cobra el envío a la cuenta conectada |
| Cuenta intentos, bloquea y marca el código como usado | |
| Verifica el código | |

El proveedor nunca sabe si un código es correcto. Más detalle en [PROVEEDORES_SMS.md](./docs/PROVEEDORES_SMS.md).

---

## Características Principales

### Generación y verificación de códigos
- Códigos de 4 a 10 dígitos (6 por defecto), generados con `SecureRandom`
- Duración configurable por petición, de 1 a 86.400 segundos (30 por defecto)
- Máximo de intentos por código (3 por defecto): el intento fallido que llega al máximo bloquea el código
- Generar un código nuevo invalida los anteriores del mismo celular
- Cada código se acepta una sola vez

### Seguridad
- Nunca se guarda el código, solo su hash HMAC-SHA256 con clave (`OTP_HASH_SECRET`)
- Verificación atómica en MongoDB: sin condiciones de carrera entre verificaciones simultáneas
- Validación del celular (formato peruano `+51 9XXXXXXXX`)
- Las credenciales de Twilio viven solo en la sesión HTTP (15 minutos) y no se guardan en la base de datos
- El modo demo no puede combinarse con un proveedor de SMS real (la aplicación no arranca)

### Proveedores de SMS intercambiables
- `console`: escribe el mensaje en el log, sin cuentas ni costo (por defecto)
- `twilio`: envío con la cuenta de Twilio del servidor
- `infobip`: envío con Infobip
- Flujo web con la cuenta de Twilio del propio usuario, por sesión

### Interfaz web incluida
- Conexión de cuenta Twilio y flujo en tres pasos: enviar, verificar y resultado
- Cuenta regresiva del código y estados de error y de expiración
- HTML, CSS y JavaScript sin framework, servidos por Spring Boot

### API documentada
- Swagger UI y especificación OpenAPI generadas automáticamente
- Errores con código estable (`OTP_EXPIRED`, `OTP_BLOCKED`, ...) y estado HTTP coherente

### Arquitectura hexagonal
- Dominio sin dependencias de Spring, MongoDB ni proveedores de SMS
- Agregar un proveedor de SMS es agregar una clase

---

## Stack Tecnológico

### Backend
- **Framework:** Spring Boot 4.1.1
- **Lenguaje:** Java 21
- **Web:** Spring Web MVC
- **Validación:** Bean Validation (Jakarta)
- **Build:** Maven (wrapper incluido)

### Base de Datos
- **MongoDB** con Spring Data MongoDB (probado con la imagen `mongo:latest`)

### Servicios Externos (opcionales)
- **Twilio:** SDK 13.0.1, API de mensajes
- **Infobip:** API REST, mediante `RestClient` de Spring

### Herramientas
- **Documentación de API:** springdoc-openapi 3.0.2 (Swagger UI)
- **Contenedores:** Docker y Docker Compose (imagen multi-etapa con Eclipse Temurin 21)
- **Frontend:** HTML5, CSS3 y JavaScript sin framework; fuentes de Google Fonts
- **Lombok:** solo en tiempo de compilación

---

## Arquitectura del Sistema

```
        Cliente (navegador · curl · Swagger UI)
                        │ HTTP/REST
                        ▼
┌───────────────────────────────────────────────────────┐
│ adapter/in/http                                       │
│  Controllers · DTOs HTTP · GlobalExceptionHandler     │
│  TwilioOnboardingFilter (protege otp-service.html)    │
└───────────────────────┬───────────────────────────────┘
                        │ Command / Result
                        ▼
┌───────────────────────────────────────────────────────┐
│ application                                           │
│  GenerateOtpUseCase · VerifyOtpUseCase                │
│  Puertos: OtpPersistencePort · SmsSender ·            │
│           CodeHasherPort                              │
└───────────────────────┬───────────────────────────────┘
                        │ implementan los puertos
                        ▼
┌───────────────────────────────────────────────────────┐
│ adapter/out                                           │
│  persistence · security (HMAC) · sms                  │
└───────────┬──────────────────────────┬────────────────┘
            ▼                          ▼
        MongoDB          Consola · Twilio · Infobip

domain (Otp, Cellphone, OtpCode, ValidityWindow, excepciones)
lo usan todas las capas y no depende de ninguna.
```

La regla de dependencia es `adapter → application → domain`. El detalle de paquetes y decisiones está en [ARQUITECTURA.md](./docs/ARQUITECTURA.md).

### Flujo principal

```mermaid
sequenceDiagram
    actor U as Usuario
    participant A as API Spring Boot
    participant M as MongoDB
    participant S as Proveedor de SMS

    U->>A: POST /otps con el celular
    A->>M: Guarda el hash del código y su expiración
    A->>S: Envía el mensaje con el código
    S-->>U: SMS al celular
    U->>A: POST /otps/verify con el código recibido
    A->>M: Reclama el código de forma atómica
    alt El código coincide
        A-->>U: 200 verificado
    else No coincide, expiró o está bloqueado
        A-->>U: 401, 410 o 423 con su código de error
    end
```

Los demás flujos (verificación paso a paso, ciclo de vida del código y Twilio por sesión) están en [FLUJOS.md](./docs/FLUJOS.md).

---

## Módulos del Sistema

### Núcleo OTP
Generación y verificación de códigos.

**Características:**
- Código aleatorio de 4 a 10 dígitos
- Ventana de validez, contador de intentos y estado usado / invalidado
- Un código nuevo invalida los anteriores del celular

**Clases principales:** `GenerateOtpUseCaseImpl`, `VerifyOtpUseCaseImpl`, `Otp`, `OtpCode`, `Cellphone`, `ValidityWindow`, `VerificationStatus`

**Colección:** `otps`

---

### Persistencia (MongoDB)
Almacenamiento de los códigos con operaciones atómicas.

**Características:**
- `claimIfMatches` marca el código como usado solo si coincide, no expiró y quedan intentos
- `registerFailedAttempt` suma un intento fallido en una sola operación
- El modelo de dominio (`Otp`) está separado del documento de MongoDB (`OtpDocument`)

**Clases principales:** `OtpPersistenceAdapter`, `OtpRepository`, `OtpRepositoryImpl`, `OtpDocument`, `OtpPersistenceMapper`

---

### Seguridad
Protección del código guardado.

**Características:**
- Hash HMAC-SHA256 con clave configurable
- Aviso al arrancar si se usa la clave de desarrollo
- `DemoModeGuard` impide combinar el modo demo con un proveedor real

**Clases principales:** `CodeHasher`, `CodeHasherPort`, `DemoModeGuard`

---

### Envío de SMS
Entrega del mensaje con el proveedor elegido.

**Características:**
- Proveedor global elegido con `SMS_PROVIDER`
- Modo consola por defecto, sin cuentas
- Error `SMS_DELIVERY_FAILED` (502) si el proveedor falla

**Clases principales:** `SmsSender`, `ConsoleSmsSender`, `TwilioSmsSender`, `InfobipSmsSender`

---

### Twilio por sesión
Flujo de la interfaz web con la cuenta de Twilio del usuario.

**Características:**
- Conectar valida las credenciales contra Twilio
- Las credenciales se guardan 15 minutos en la sesión y se muestran enmascaradas
- El envío usa las credenciales de la sesión, no las del servidor

**Clases principales:** `TwilioConnectHttpAdapter`, `TwilioOtpHttpAdapter`, `TwilioSessionService`, `TwilioSessionSmsSender`, `TwilioVerifyService`, `TwilioOnboardingFilter`

---

### Manejo de errores
Excepciones de negocio traducidas a respuestas HTTP.

**Características:**
- Una excepción por regla (`OtpExpiredException`, `OtpBlockedException`, ...)
- Un único punto que decide el código HTTP: `GlobalExceptionHandler`
- Respuesta uniforme `{success, code, message}`

**Clases principales:** `OtpDomainException`, `GlobalExceptionHandler`

---

### Interfaz web
Cliente de demostración en `src/main/resources/static/`.

**Características:**
- `index.html`: conexión de la cuenta de Twilio
- `otp-service.html`: envío, verificación y resultado en tres pasos
- El selector de propósito (iniciar sesión, registro, recuperar acceso, confirmar un pago) solo cambia los textos de la demo: el backend valida el código igual en todos los casos

---

## Documentación Detallada

### Arquitectura
[**ARQUITECTURA.md**](./docs/ARQUITECTURA.md)
Estructura hexagonal del proyecto:
- Paquetes y regla de dependencia
- Manejo de errores
- Decisiones de diseño, limitaciones y deuda técnica

### Flujos
[**FLUJOS.md**](./docs/FLUJOS.md)
Diagramas de los procesos:
- Generar un código
- Verificar un código, paso a paso
- Ciclo de vida de un código
- Twilio por sesión y qué proveedor envía cada flujo

### Base de Datos
[**BASE_DE_DATOS.md**](./docs/BASE_DE_DATOS.md)
Modelo de datos en MongoDB:
- Campos de la colección `otps`
- Ciclo de vida de un código
- Operaciones atómicas
- Índices y purga por TTL
- Datos que se guardan

### API
[**API.md**](./docs/API.md)
Referencia de los endpoints:
- Campos, reglas, request y response de cada endpoint
- Flujo de Twilio por sesión
- Códigos de error y errores de formato

### Proveedores de SMS
[**PROVEEDORES_SMS.md**](./docs/PROVEEDORES_SMS.md)
Envío de mensajes:
- Qué hace el backend y qué hace el proveedor
- Consola, Twilio e Infobip
- Validaciones al arrancar y restricciones
- Cómo agregar un proveedor

---

## Instalación y Configuración

### Prerrequisitos

- Java 21
- Docker (para MongoDB o para levantar todo con Docker Compose), o un MongoDB accesible
- No hace falta instalar Maven: el repositorio incluye `mvnw`
- Opcional: una cuenta de Twilio o Infobip para enviar SMS reales

### Opción A. Ejecutar en local

```bash
git clone https://github.com/JoshRivas313/OTP-Service.git
cd OTP-Service
docker run -d -p 27017:27017 --name otp-mongo mongo:latest
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`.

Sin más configuración la aplicación usa MongoDB en `localhost:27017` y el proveedor `console`.

### Opción B. Docker Compose

```bash
docker compose up --build
```

Levanta MongoDB y la aplicación con el proveedor `console`. Las credenciales de MongoDB y la clave de `docker-compose.yml` son solo para desarrollo local.

### Probar

- Interfaz web: `http://localhost:8080` (pide conectar una cuenta de Twilio, ver [Limitaciones Conocidas](#limitaciones-conocidas))
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Sin ninguna cuenta se puede probar la API directamente. Con el proveedor `console`, el código aparece en el log de la aplicación:

```bash
curl -X POST http://localhost:8080/otps \
  -H "Content-Type: application/json" \
  -d '{"cellphone":"912345678","digits":6,"durationSeconds":60}'
```

En el log:

```
[DEV][SMS] para=*********678 mensaje="Tu código de verificación es 482913. Vence en 60 segundos."
```

Con Docker Compose: `docker compose logs -f app`. Luego, con ese código:

```bash
curl -X POST http://localhost:8080/otps/verify \
  -H "Content-Type: application/json" \
  -d '{"cellphone":"912345678","code":"482913"}'
```

---

## Variables de Entorno

La aplicación lee estas variables del entorno del sistema. **El archivo `.env` no se carga automáticamente**: `.env.example` solo lista las variables. Hay que definirlas en el shell, en la configuración de ejecución del IDE o en `docker-compose.yml`.

| Variable | Por defecto | Descripción |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/otp_service` | Conexión a MongoDB |
| `OTP_HASH_SECRET` | `dev-only-secret-change-me` | Clave del HMAC-SHA256. Cámbiala fuera de desarrollo: la aplicación avisa si queda el valor por defecto |
| `OTP_DEMO_MODE` | `false` | Si es `true`, `POST /otps` devuelve el código en `demoCode`. No se puede combinar con `twilio` ni `infobip` |
| `SMS_PROVIDER` | `console` | `console`, `twilio` o `infobip` |
| `TWILIO_ACCOUNT_SID` | vacío | Obligatoria si `SMS_PROVIDER=twilio` |
| `TWILIO_AUTH_TOKEN` | vacío | Obligatoria si `SMS_PROVIDER=twilio` |
| `TWILIO_PHONE_NUMBER` | vacío | Número de Twilio remitente. Obligatoria si `SMS_PROVIDER=twilio` |
| `INFOBIP_BASE_URL` | vacío | Obligatoria si `SMS_PROVIDER=infobip` |
| `INFOBIP_API_KEY` | vacío | Obligatoria si `SMS_PROVIDER=infobip` |
| `INFOBIP_SENDER` | vacío | Obligatoria si `SMS_PROVIDER=infobip` |
| `SERVER_PORT` | `8080` | Puerto HTTP (configuración estándar de Spring Boot) |

```bash
# Linux, macOS o Git Bash
export OTP_HASH_SECRET="una-clave-larga-y-aleatoria"
./mvnw spring-boot:run
```

```powershell
# PowerShell
$env:OTP_HASH_SECRET = "una-clave-larga-y-aleatoria"
.\mvnw.cmd spring-boot:run
```

Los parámetros del código se definen en `src/main/resources/application.yaml`:

| Propiedad | Valor | Descripción |
|---|---|---|
| `otp.digits` | `6` | Dígitos por defecto |
| `otp.duration-seconds` | `30` | Duración por defecto, en segundos |
| `otp.max-attempts` | `3` | Intentos fallidos antes de bloquear |
| `otp.retention-seconds` | `86400` | Segundos que se conserva un código tras expirar (lo usa el índice TTL) |

`digits` y `durationSeconds` también se pueden enviar en cada petición.

---

## Scripts Disponibles

```bash
# Desarrollo
./mvnw spring-boot:run              # Inicia la aplicación

# Compilación
./mvnw clean compile                # Compila
./mvnw clean package                # Genera el JAR en target/
java -jar target/otp-service-0.0.1-SNAPSHOT.jar

# Docker
docker compose up --build           # Levanta MongoDB y la aplicación
docker compose logs -f app          # Sigue el log (aquí aparecen los códigos en modo console)
docker compose down                 # Detiene los contenedores
docker compose down -v              # Detiene y borra los datos de MongoDB
```

---

## API Endpoints

Base URL: `http://localhost:8080`

Documentación interactiva: `http://localhost:8080/swagger-ui.html`

### OTP local

**POST /otps** — Generar código OTP

**Request**

```json
{
  "cellphone": "912345678",
  "digits": 6,
  "durationSeconds": 60
}
```

**Response `201`**

```json
{
  "success": true,
  "message": "Código enviado correctamente",
  "demoCode": null
}
```

**POST /otps/verify** — Verificar código

**Request**

```json
{
  "cellphone": "912345678",
  "code": "123456"
}
```

**Response `200`**

```json
{
  "success": true,
  "message": "Código verificado correctamente"
}
```

### OTP con Twilio por sesión

**POST /api/twilio/connect** — Conectar cuenta Twilio

**Request**

```json
{
  "accountSid": "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "authToken": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "verifyServiceSid": "VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "phoneNumber": "+15017122661"
}
```

**Response `200`**

```json
{
  "connected": true,
  "maskedCredentials": "AC1234···abcd / verify VA5678···efgh"
}
```

**GET /api/twilio/status** — Estado de la conexión

**Response `200`**

```json
{
  "connected": false,
  "maskedCredentials": null
}
```

**POST /api/twilio/disconnect** — Desconectar cuenta Twilio

**Response `200`**

```json
{
  "connected": false,
  "maskedCredentials": null
}
```

**POST /api/twilio/otps** — Generar código OTP con la cuenta de la sesión

**Request**

```json
{
  "cellphone": "912345678",
  "digits": 6,
  "durationSeconds": 60
}
```

**Response `201`**

```json
{
  "success": true,
  "message": "Código enviado correctamente",
  "demoCode": null
}
```

**POST /api/twilio/otps/verify** — Verificar código

**Request**

```json
{
  "cellphone": "912345678",
  "code": "123456"
}
```

**Response `200`**

```json
{
  "success": true,
  "message": "Código verificado correctamente"
}
```

### Error

Los errores de negocio y de validación comparten este formato:

**Response `401`** (ejemplo: código incorrecto)

```json
{
  "success": false,
  "code": "OTP_INVALID",
  "message": "El código es incorrecto (intento 1 de 3)"
}
```

**Documentación completa:** [API.md](./docs/API.md)

---

## Limitaciones Conocidas

- **Solo celulares peruanos.** Se acepta `9XXXXXXXX` o `+519XXXXXXXX`.
- **La interfaz web exige conectar una cuenta de Twilio.** Sin ella se puede probar la API directamente con el proveedor `console`.
- **La API no tiene autenticación ni límite de envíos.** No la publiques en internet con `SMS_PROVIDER=twilio` o `infobip`: cualquiera podría generar SMS a cargo de esa cuenta.
- **Los índices de MongoDB no se crean automáticamente**, incluido el de purga por TTL. Ver [BASE_DE_DATOS.md](./docs/BASE_DE_DATOS.md#índices).
- **Los errores de formato devuelven el cuerpo estándar de Spring.** Un celular o un código mal formados responden `400` sin `code` ni `message`. Además, el formulario acepta de 7 a 9 dígitos y el backend exige 9 que empiecen con 9.
- Las cuentas de prueba de Twilio, según sus reglas, solo envían a números verificados.

---

## Contribución

### Flujo de trabajo

1. Crear una rama desde `main`: `feature/...` o `fix/...`
2. Hacer commits descriptivos
3. Comprobar que compila: `./mvnw clean compile`
4. Abrir un Pull Request a `main` explicando el cambio

### Convenciones

- **Commits:** Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `chore:`)
- **Nombres:** `camelCase` para variables y métodos, `PascalCase` para clases
- **Capas:** `domain` no importa `application` ni `adapter`; `application` no importa `adapter`
- **Tiempo:** usar el `Clock` inyectado, nunca `Instant.now()`
- **Proveedor de SMS nuevo:** una clase que implemente `SmsSender` (ver [PROVEEDORES_SMS.md](./docs/PROVEEDORES_SMS.md#agregar-un-proveedor))

---

## Licencia

MIT. Ver [LICENSE](./LICENSE).

---

## Autor

[Jose Hurtado Rivas](https://www.linkedin.com/in/jose-hurtado-rivas-8150b0231/)

## Soporte

Dudas o problemas: [GitHub Issues](https://github.com/JoshRivas313/OTP-Service/issues)

---

**Versión:** 0.0.1-SNAPSHOT
**Última actualización:** 28 de septiembre de 2026
