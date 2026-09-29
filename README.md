<h1 align="center">OTP Service</h1>

<p align="center">
  API para generar y verificar códigos de un solo uso (OTP) y enviarlos por SMS
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 4.1.1" src="https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white">
  <img alt="MongoDB opcional" src="https://img.shields.io/badge/MongoDB-opcional-47A248?logo=mongodb&logoColor=white">
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
- [Despliegue en Render](#despliegue-en-render)
- [Scripts Disponibles](#scripts-disponibles)
- [API Endpoints](#api-endpoints)
- [Flujos de Uso](#flujos-de-uso)
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

| Backend (Spring Boot) | Proveedor de SMS (Twilio o Infobip) |
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
- Verificación atómica (en memoria o en MongoDB): sin condiciones de carrera entre verificaciones simultáneas
- Validación del celular (formato peruano `+51 9XXXXXXXX`)
- Las credenciales de Twilio viven solo en la memoria del servidor, ligadas a la sesión HTTP (15 minutos), y no se escriben en ninguna base de datos ni en el log
- El modo demo no puede combinarse con un proveedor de SMS real (la aplicación no arranca)

### Almacenamiento intercambiable
- En memoria por defecto: no necesita base de datos, los códigos se purgan al vencer su retención y hay un tope de códigos guardados
- MongoDB opcional con el perfil `mongo`, para que los códigos sobrevivan a un reinicio o para varias instancias

### Proveedores de SMS intercambiables
- `console`: escribe el mensaje en el log, sin cuentas ni costo (por defecto)
- `twilio`: envío con la cuenta de Twilio del servidor
- `infobip`: envío con Infobip
- Flujo web con la cuenta de Twilio del propio usuario, por sesión

### Interfaz web incluida
- Conexión de cuenta Twilio y flujo en tres pasos: enviar, verificar y resultado
- El propósito del código (iniciar sesión, registro, recuperar acceso, confirmar un pago) define el texto del SMS, o se puede escribir un mensaje propio con `{code}` y `{seconds}`
- Cuenta regresiva del código y estados de error y de expiración
- HTML, CSS y JavaScript sin framework, servidos por Spring Boot

### API documentada
- Swagger UI y especificación OpenAPI generadas automáticamente
- Errores con código estable (`OTP_EXPIRED`, `OTP_BLOCKED`, ...) y estado HTTP coherente

### Arquitectura hexagonal
- Dominio sin dependencias de Spring, MongoDB ni proveedores de SMS
- El almacenamiento es un puerto con dos adaptadores: memoria y MongoDB
- Agregar un proveedor de SMS es agregar una clase

---

## Stack Tecnológico

### Backend
- **Framework:** Spring Boot 4.1.1
- **Lenguaje:** Java 21
- **Web:** Spring Web MVC
- **Validación:** Bean Validation (Jakarta)
- **Build:** Maven (wrapper incluido)

### Almacenamiento
- **Memoria** (por defecto): sin dependencias externas
- **MongoDB** (opcional) con Spring Data MongoDB

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
   Memoria · MongoDB     Consola · Twilio · Infobip

domain (Otp, Cellphone, OtpCode, ValidityWindow, excepciones)
lo usan todas las capas y no depende de ninguna.
```

La regla de dependencia es `adapter → application → domain`. El detalle de paquetes y decisiones está en [ARQUITECTURA.md](./docs/ARQUITECTURA.md).


---

## Módulos del Sistema

### Núcleo OTP
Generación y verificación de códigos.

**Características:**
- Código aleatorio de 4 a 10 dígitos
- Ventana de validez, contador de intentos y estado usado / invalidado
- Un código nuevo invalida los anteriores del celular

**Clases principales:** `GenerateOtpUseCaseImpl`, `VerifyOtpUseCaseImpl`, `Otp`, `OtpCode`, `Cellphone`, `ValidityWindow`, `VerificationStatus`

**Colección (modo MongoDB):** `otps`

---

### Persistencia
Almacenamiento de los códigos con operaciones atómicas, en memoria o en MongoDB según el perfil.

**Características:**
- `claimIfMatches` marca el código como usado solo si coincide, no expiró y quedan intentos
- `registerFailedAttempt` suma un intento fallido en una sola operación
- En memoria, un único lock protege el estado, purga los códigos vencidos y descarta los más antiguos al llegar al tope
- El modelo de dominio (`Otp`) está separado del documento de MongoDB (`OtpDocument`)

**Clases principales:** `InMemoryOtpPersistenceAdapter`, `OtpPersistenceAdapter`, `OtpRepository`, `OtpRepositoryImpl`, `OtpDocument`, `OtpPersistenceMapper`

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
- Al conectar se consultan los números verificados de la cuenta y el envío queda limitado a ellos
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
- El selector de propósito (iniciar sesión, registro, recuperar acceso, confirmar un pago) cambia el texto del SMS y los textos de la demo; también se puede escribir un mensaje propio. El backend valida el código igual en todos los casos

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
Diagramas complementarios a los [Flujos de Uso](#flujos-de-uso):
- Generar un código, paso a paso
- Verificar un código, con cada comprobación y su error
- Ciclo de vida de un código
- Qué proveedor envía cada flujo

### Base de Datos
[**BASE_DE_DATOS.md**](./docs/BASE_DE_DATOS.md)
Modelo de datos y almacenamiento:
- Modo memoria y modo MongoDB
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
- Opcional: Docker, para levantar la aplicación con MongoDB mediante Docker Compose
- No hace falta instalar Maven: el repositorio incluye `mvnw`
- Opcional: una cuenta de Twilio o Infobip para enviar SMS reales

### Opción A. Ejecutar en local

```bash
git clone https://github.com/JoshRivas313/OTP-Service.git
cd OTP-Service
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`.

Sin más configuración la aplicación guarda los códigos en memoria, no necesita ninguna base de datos y usa el proveedor `console`. Al reiniciarla se pierden los códigos pendientes.

Para guardarlos en MongoDB, levanta una instancia y activa el perfil `mongo`:

```bash
docker run -d -p 27017:27017 --name otp-mongo mongo:latest
SPRING_PROFILES_ACTIVE=mongo ./mvnw spring-boot:run
```

Con el perfil `mongo` la aplicación crea sola los índices, incluido el de purga por TTL.

### Opción B. Docker Compose

```bash
docker compose up --build
```

Levanta MongoDB y la aplicación con el perfil `mongo` y el proveedor `console`. Las credenciales de MongoDB y la clave de `docker-compose.yml` son solo para desarrollo local.

### Probar

- Interfaz web: `http://localhost:8080` (pide conectar una cuenta de Twilio, ver [Limitaciones Conocidas](#limitaciones-conocidas))
- Swagger UI: `http://localhost:8080/swagger-ui.html` (con la imagen Docker está deshabilitado salvo que definas `SWAGGER_ENABLED=true`; Docker Compose ya lo activa)
- Estado del servicio: `GET http://localhost:8080/health`

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
| `SPRING_PROFILES_ACTIVE` | vacío | `mongo` activa el almacenamiento en MongoDB. Sin él, los códigos se guardan en memoria |
| `MONGODB_URI` | `mongodb://localhost:27017/otp_service` | Conexión a MongoDB. Solo se lee con el perfil `mongo` |
| `OTP_HASH_SECRET` | `dev-only-secret-change-me` | Clave del HMAC-SHA256. Cámbiala fuera de desarrollo: la aplicación avisa si queda el valor por defecto |
| `OTP_DEMO_MODE` | `false` | Si es `true`, `POST /otps` devuelve el código en `demoCode`. No se puede combinar con `twilio` ni `infobip` |
| `SMS_PROVIDER` | `console` | `console`, `twilio` o `infobip` |
| `TWILIO_ACCOUNT_SID` | vacío | Obligatoria si `SMS_PROVIDER=twilio` |
| `TWILIO_AUTH_TOKEN` | vacío | Obligatoria si `SMS_PROVIDER=twilio` |
| `TWILIO_PHONE_NUMBER` | vacío | Número de Twilio remitente. Obligatoria si `SMS_PROVIDER=twilio` |
| `INFOBIP_BASE_URL` | vacío | Obligatoria si `SMS_PROVIDER=infobip` |
| `INFOBIP_API_KEY` | vacío | Obligatoria si `SMS_PROVIDER=infobip` |
| `INFOBIP_SENDER` | vacío | Obligatoria si `SMS_PROVIDER=infobip` |
| `PORT` | `8080` | Puerto HTTP. Las plataformas como Render lo definen solas |
| `SESSION_COOKIE_SECURE` | `false` | Si es `true`, la cookie de sesión solo viaja por HTTPS. Actívalo al publicar |
| `OTP_LOCAL_API_ENABLED` | `true` | Si es `false`, se deshabilitan `POST /otps` y `POST /otps/verify`; quedan solo los endpoints de Twilio por sesión |
| `SWAGGER_ENABLED` | `true` | Si es `false`, se deshabilitan Swagger UI y `/v3/api-docs`. La imagen Docker lo trae en `false`; `docker-compose.yml` lo activa |
| `OTP_MEMORY_MAX_ENTRIES` | `10000` | Tope de códigos guardados en memoria; al llegar se descartan los más antiguos |

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
| `otp.retention-seconds` | `86400` | Segundos que se conserva un código tras expirar (en memoria lo purga la propia aplicación; en MongoDB, el índice TTL) |

`digits` y `durationSeconds` también se pueden enviar en cada petición.

---

## Despliegue en Render

La aplicación se despliega como un **Web Service con Docker** usando el `Dockerfile` del repositorio. Con el almacenamiento en memoria por defecto no hace falta ninguna base de datos.

1. En Render, crea un **New Web Service** y conecta el repositorio.
2. Elige **Language: Docker**. Render detecta el `Dockerfile` y toma el puerto de la variable `PORT`.
3. Define estas variables de entorno:

| Variable | Valor |
|---|---|
| `OTP_HASH_SECRET` | Una clave larga y aleatoria, por ejemplo la salida de `openssl rand -hex 32` |
| `SESSION_COOKIE_SECURE` | `true` |
| `OTP_LOCAL_API_ENABLED` | `false` |

4. En **Health Check Path** pon `/health`.
5. Despliega. La interfaz web queda disponible en la URL que asigna Render.

La imagen Docker deshabilita Swagger UI por defecto (`SWAGGER_ENABLED=false`); para activarlo en un despliegue, define `SWAGGER_ENABLED=true`.

Con esta configuración el servicio público solo expone el flujo de Twilio por sesión: cada visitante conecta su propia cuenta de Twilio y los envíos salen de ella. **No definas `SMS_PROVIDER=twilio` con tus propias credenciales en el servidor público**, porque cualquiera podría generar SMS a cargo de tu cuenta.

En el plan gratuito de Render el servicio se suspende tras un rato sin tráfico y, al despertar, empieza con la memoria vacía: los códigos pendientes y las sesiones de Twilio se pierden.

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
docker compose up --build           # Levanta MongoDB y la aplicación (perfil mongo)
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

## Flujos de Uso

### Flujo 1: Generación Local (Motor Interno)

```mermaid
sequenceDiagram
    participant Cliente
    participant Backend
    participant SMS as SMS Provider
    participant Store as Almacén de códigos

    Cliente->>Backend: POST /otps<br/>{cellphone, digits, durationSeconds}
    Backend->>Store: Invalidar códigos anteriores del celular
    Backend->>Backend: Generar código con SecureRandom
    Backend->>Backend: Hashear con HMAC-SHA256
    Backend->>Store: Guardar {hash, intentos, expira}
    Backend->>SMS: Mandar SMS con código
    SMS-->>Cliente: SMS recibido
    Backend-->>Cliente: 201 Código enviado

    Cliente->>Backend: POST /otps/verify<br/>{cellphone, code}
    Backend->>Store: Buscar código por celular
    Backend->>Backend: Validar estado, intentos, expiración
    alt Código válido
        Backend->>Store: Marcar como usado
        Backend-->>Cliente: 200 Código verificado
    else Código incorrecto
        Backend->>Store: Incrementar intentos
        Backend-->>Cliente: 401 OTP_INVALID (423 OTP_BLOCKED si fue el último intento)
    else Expirado, usado, invalidado, bloqueado o inexistente
        Backend-->>Cliente: 410, 409, 423 o 404 con su código de error
    end
```

**Cuándo usar**: pruebas de la API, demo local y cuando no se tiene una cuenta de Twilio. Con el proveedor `console` el código aparece en el log de la aplicación en lugar de llegar por SMS.

### Flujo 2: Twilio por Sesión

```mermaid
sequenceDiagram
    participant Usuario
    participant index.html
    participant Backend
    participant Twilio
    participant otp-service.html
    participant Store as Almacén de códigos

    Usuario->>index.html: Abre http://localhost:8080
    Usuario->>index.html: Ingresa credenciales Twilio
    index.html->>Backend: POST /api/twilio/connect<br/>{accountSid, authToken, verifyServiceSid, phoneNumber}
    Backend->>Twilio: Validar credenciales (consulta el Verify Service)
    Twilio-->>Backend: OK / Error
    alt Credenciales válidas
        Backend->>Backend: Guardar en HttpSession (15 min)
        Backend-->>index.html: 200 connected
        index.html->>otp-service.html: Redirect
    else Inválidas
        Backend-->>index.html: 401 TWILIO_CREDENTIALS_INVALID
    end

    Usuario->>otp-service.html: Ingresa celular, dígitos y expiración
    otp-service.html->>Backend: POST /api/twilio/otps<br/>{cellphone, digits, durationSeconds}
    Backend->>Backend: Generar código + hashear
    Backend->>Store: Guardar hash, expiración e intentos
    Backend->>Twilio: Enviar SMS con las credenciales de la sesión
    Twilio-->>Usuario: SMS con el código
    Backend-->>otp-service.html: 201

    Usuario->>otp-service.html: Ingresa el código recibido
    otp-service.html->>Backend: POST /api/twilio/otps/verify<br/>{cellphone, code}
    Backend->>Store: Reclamar el código (operación atómica)
    alt Correcto
        Backend-->>otp-service.html: 200 verificado
        otp-service.html->>otp-service.html: Animación de éxito + resultado
    else Incorrecto, expirado o bloqueado
        Backend-->>otp-service.html: 401, 410 o 423 con su código de error
    end

    Usuario->>otp-service.html: Botón Cambiar configuración
    otp-service.html->>Backend: POST /api/twilio/disconnect
    Backend->>Backend: Borrar las credenciales de la sesión
    Backend-->>index.html: Redirect
```

**Cuándo usar**: probar el flujo completo con SMS reales desde la interfaz web, con la cuenta de Twilio propia. Twilio solo valida las credenciales y entrega el SMS; el código lo genera y lo verifica el backend.

---

## Limitaciones Conocidas

- **Solo celulares peruanos.** Se acepta `9XXXXXXXX` o `+519XXXXXXXX`.
- **La interfaz web exige conectar una cuenta de Twilio.** Sin ella se puede probar la API directamente con el proveedor `console`.
- **Con el almacenamiento en memoria, un reinicio borra los códigos pendientes y las sesiones de Twilio.** Además, la memoria no se comparte entre instancias: para varias réplicas hay que usar el perfil `mongo`.
- **En memoria hay un tope de códigos** (`OTP_MEMORY_MAX_ENTRIES`). Al llegar, se descartan los más antiguos, incluso si aún estaban vigentes.
- **La API no tiene autenticación ni límite de envíos.** No la publiques en internet con `SMS_PROVIDER=twilio` o `infobip`: cualquiera podría generar SMS a cargo de esa cuenta. Para una instancia pública usa `OTP_LOCAL_API_ENABLED=false`.
- **Los errores de formato devuelven el cuerpo estándar de Spring.** Un celular o un código mal formados responden `400` sin `code` ni `message`. 
- **Solo se envía a los números verificados de la cuenta de Twilio conectada.** Al conectar, el servicio consulta a Twilio qué números tiene verificados la cuenta (siempre en las cuentas de prueba) y solo permite enviar a esos; otro destino responde `403 DESTINATION_NOT_VERIFIED`. Una cuenta de pago sin números verificados puede enviar a cualquier celular peruano.

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
