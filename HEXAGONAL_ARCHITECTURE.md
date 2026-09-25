# Arquitectura Hexagonal (Puertos y Adaptadores)

## Descripción

El backend está organizado en arquitectura hexagonal: el dominio (reglas de negocio, modelo, value objects y excepciones) no depende de Spring MVC, Spring Data ni de ningún SDK externo. Todo lo técnico entra o sale a través de **puertos** (interfaces en `domain/port`), implementados por **adaptadores** concretos en `adapter/`. Solo quedan 3 paquetes de primer nivel: `domain`, `application`, `adapter`.

## Estructura de Carpetas (real, verificada por compilación)

```
src/main/java/com/otpservice/otp/
├── domain/                              # Núcleo del negocio — cero imports de Spring
│   ├── model/
│   │   └── Otp                           # Modelo puro (isExpired, isBlocked, isUsed).
│   │                                      # Sin anotaciones de persistencia.
│   ├── valueobject/
│   │   ├── Cellphone                     # Teléfono E.164, deserializable desde JSON
│   │   ├── OtpCode                       # Código OTP (4-10 dígitos)
│   │   ├── ValidityWindow                # Ventana de validez
│   │   ├── VerificationStatus            # Intentos, usado, invalidado
│   │   └── TwilioCredentials             # Ver "Decisiones" — por qué no está en adapter/
│   ├── exception/                        # Excepciones de negocio, sin HttpStatus
│   │   ├── OtpDomainException             # Base abstracta: solo errorCode + mensaje
│   │   ├── OtpNotFoundException
│   │   ├── OtpInvalidatedException
│   │   ├── OtpAlreadyUsedException
│   │   ├── OtpExpiredException
│   │   ├── OtpBlockedException
│   │   ├── InvalidOtpException
│   │   ├── SmsDeliveryFailedException
│   │   ├── TwilioCredentialsInvalidException
│   │   └── TwilioNotConnectedException
│   └── port/
│       ├── input/                        # Casos de uso
│       │   ├── GenerateOtpUseCase
│       │   ├── VerifyOtpUseCase
│       │   ├── TwilioGenerateOtpUseCase
│       │   └── TwilioVerifyOtpUseCase
│       └── output/                       # Lo que el dominio necesita del exterior
│           ├── OtpPersistencePort         # Persistencia (atómica), trabaja con Otp
│           ├── SmsSender                  # Envío de SMS
│           └── CodeHasherPort             # Hash de códigos OTP
│
├── application/usecase/                  # Orquestación (los únicos @Service de caso de uso)
│   ├── GenerateOtpUseCaseImpl
│   ├── VerifyOtpUseCaseImpl
│   ├── TwilioGenerateOtpUseCaseImpl       # Delega a GenerateOtpUseCase con otro SmsSender
│   └── TwilioVerifyOtpUseCaseImpl         # Delega a VerifyOtpUseCase
│
├── adapter/
│   ├── in/http/                          # Adaptadores de entrada (los únicos controllers)
│   │   ├── OtpHttpAdapter                 # POST /otps, /otps/verify
│   │   ├── TwilioOtpHttpAdapter           # POST /api/twilio/otps[/verify]
│   │   ├── TwilioConnectHttpAdapter       # POST/GET /api/twilio/connect|status|disconnect
│   │   ├── GlobalExceptionHandler         # @RestControllerAdvice: OtpDomainException -> HTTP
│   │   └── dto/
│   │       ├── request/                   # 5 DTOs de request (con @Valid)
│   │       └── response/                  # 4 DTOs de response
│   │
│   ├── out/
│   │   ├── persistence/
│   │   │   ├── OtpPersistenceAdapter       # Implementa OtpPersistencePort, mapea Otp<->OtpDocument
│   │   │   ├── OtpRepository               # Spring Data MongoRepository<OtpDocument>
│   │   │   ├── OtpRepositoryCustom         # Operaciones atómicas (interfaz)
│   │   │   ├── impl/OtpRepositoryImpl      # Update atómico vía MongoTemplate
│   │   │   ├── document/OtpDocument        # Representación Mongo (@Document, @Indexed TTL)
│   │   │   └── mapper/OtpPersistenceMapper # Otp (dominio) <-> OtpDocument (persistencia)
│   │   ├── security/
│   │   │   └── CodeHasher                  # Implementa CodeHasherPort (HMAC-SHA256)
│   │   └── sms/
│   │       ├── ConsoleSmsSender            # Implementa SmsSender (debug, default)
│   │       ├── TwilioSmsSender             # Implementa SmsSender (credenciales globales)
│   │       ├── InfobipSmsSender            # Implementa SmsSender (Infobip)
│   │       └── twilio/
│   │           ├── TwilioSessionService     # Guarda/lee TwilioCredentials en HttpSession
│   │           ├── TwilioSessionSmsSender   # Envía SMS con credenciales de sesión
│   │           └── TwilioVerifyService      # Valida credenciales contra la API de Twilio
│   │
│   └── config/                           # Beans y filtros técnicos de Spring
│       ├── ClockConfig, OtpProperties, SmsProperties
│       ├── DemoModeGuard, OpenApiConfig
│       └── TwilioOnboardingFilter
```

## Flujo de Dependencias

```
HTTP Request
    ↓
adapter/in/http/*HttpAdapter            ← traduce HTTP a comandos
    ↓
application/usecase/*UseCaseImpl        ← orquesta reglas de negocio
    ↓
domain/{model,valueobject,exception}    ← invariantes puras, sin Spring
    ↓
domain/port/output (interfaces)         ← OtpPersistencePort, SmsSender, CodeHasherPort
    ↓
adapter/out/*                            ← OtpPersistenceAdapter (+ mapper), ConsoleSmsSender...
    ↓
MongoDB / Twilio / Infobip
```

`domain/` y `application/` nunca importan una clase de `adapter/`. La única dirección permitida es `adapter → application → domain`.

## Casos de Uso

### GenerateOtpUseCase
```
Input:  { cellphone, digits?, durationSeconds? }
1. Invalida cualquier OTP activo previo del mismo celular (operación atómica)
2. Genera OtpCode con SecureRandom
3. Hashea con HMAC-SHA256 (CodeHasherPort)
4. Persiste Otp con TTL de purga (OtpPersistencePort -> mapeado a OtpDocument en el adapter)
5. Envía SMS (SmsSender por defecto, o uno inyectado — ver Twilio)
Output: OtpGenerateResponse (success, message, demoCode si demoMode=true)
```

### VerifyOtpUseCase
```
Input:  { cellphone, code }
1. Busca el OTP más reciente del celular (OtpNotFoundException si no existe)
2. Verifica: no invalidado → no usado → no expirado → no bloqueado
   (OtpInvalidatedException / OtpAlreadyUsedException / OtpExpiredException / OtpBlockedException)
3. Compara hash; si coincide, claim atómico (claimIfMatches) marca como usado
4. Si no coincide, registra intento fallido atómicamente; si se bloquea, OtpBlockedException;
   si no, InvalidOtpException con el conteo de intentos
Output: OtpVerifyResponse | una OtpDomainException (mapeada a HTTP por GlobalExceptionHandler)
```

### TwilioGenerateOtpUseCase / TwilioVerifyOtpUseCase
No duplican lógica: delegan a `GenerateOtpUseCase`/`VerifyOtpUseCase`, sustituyendo el `SmsSender` por uno que usa las credenciales Twilio de la sesión HTTP (`TwilioSessionSmsSender`), en vez del proveedor global configurado por `sms.provider`.

## Manejo de errores

Cada regla de negocio que puede fallar tiene su propia excepción en `domain/exception`, todas heredando de `OtpDomainException` (que solo expone `errorCode` + mensaje, sin `HttpStatus`). El único lugar que sabe traducir "regla de negocio violada" a "código HTTP" es `GlobalExceptionHandler`, con un `switch` de pattern matching (Java 21) sobre el tipo concreto:

```java
private HttpStatus statusFor(OtpDomainException exception) {
    return switch (exception) {
        case OtpNotFoundException e -> HttpStatus.NOT_FOUND;
        case OtpExpiredException e -> HttpStatus.GONE;
        case OtpBlockedException e -> HttpStatus.LOCKED;
        case InvalidOtpException e -> HttpStatus.UNAUTHORIZED;
        // ...
    };
}
```

Antes de esto, había un único `OtpException` genérico + un enum `ErrorCode` que mezclaba el código, el mensaje default *y* el `HttpStatus` de Spring en el mismo sitio — lo que acoplaba ese enum (y por tanto cualquier código que lo lanzara) a `org.springframework.http.HttpStatus`. Separarlo en clases por tipo saca a Spring del dominio por completo, a cambio de un `GlobalExceptionHandler` un poco más largo.

## Endpoints

| Método | Ruta | Adaptador |
|---|---|---|
| POST | `/otps` | OtpHttpAdapter |
| POST | `/otps/verify` | OtpHttpAdapter |
| POST | `/api/twilio/connect` | TwilioConnectHttpAdapter |
| GET | `/api/twilio/status` | TwilioConnectHttpAdapter |
| POST | `/api/twilio/disconnect` | TwilioConnectHttpAdapter |
| POST | `/api/twilio/otps` | TwilioOtpHttpAdapter |
| POST | `/api/twilio/otps/verify` | TwilioOtpHttpAdapter |

Todos verificados end-to-end con MongoDB real corriendo en Docker: generar, verificar, código ya usado (409), OTP inexistente (404), código incorrecto (401), credenciales Twilio inválidas (401), sesión Twilio no conectada (400) — cada respuesta idéntica a como era antes de esta refactorización.

## Decisiones Pragmáticas (y por qué)

- **`Otp` (dominio) vs `OtpDocument` (persistencia) están separados, con `OtpPersistenceMapper` entre ambos.** `Otp` vive en `domain/model` sin ninguna anotación; `OtpDocument` vive en `adapter/out/persistence/document` con `@Document`/`@Indexed` de Spring Data Mongo. Si el día de mañana se cambia de Mongo a otra base, el dominio no se entera — solo cambia el adapter y el mapper.
- **`TwilioCredentials` se quedó en `domain/valueobject`, no se movió a `adapter/out/sms/twilio`.** Se intentó moverlo (por el mismo argumento que separar `Otp`/`OtpDocument`: "es un detalle del proveedor Twilio, no del dominio OTP"), pero el puerto de entrada `TwilioGenerateOtpUseCase` — que vive en `domain/port/input` — lo usa directamente en su firma. Moverlo a `adapter` habría hecho que ese puerto (dominio) importara desde `adapter`, invirtiendo la dependencia. A diferencia de `OtpDocument` (que sí tenía una alternativa de dominio limpia: `Otp`), `TwilioCredentials` ya es un Value Object puro (sin Spring, sin SDK de Twilio, autovalidado por regex) — no había nada que "separar", así que se dejó donde ya estaba.
- **`OtpPersistencePort` refleja operaciones atómicas de Mongo**, no un CRUD genérico: `claimIfMatches` y `registerFailedAttempt` son updates atómicos (`findAndModify`) para que verificaciones concurrentes del mismo código no generen condiciones de carrera.
- **`SmsSender` y `CodeHasherPort` son los puertos de salida "técnicos".** `SmsSender` ya era una interfaz pura antes de la migración. `CodeHasherPort` se creó para que `CodeHasher` (HMAC-SHA256, en `adapter/out/security`) sea intercambiable sin tocar los casos de uso.
- **`OtpProperties`/`SmsProperties` (en `adapter/config`) se inyectan directamente en `application/usecase`.** Son `record`s de solo configuración (`@ConfigurationProperties`, sin lógica ni dependencias pesadas). Crear un puerto para "leer configuración" habría sido sobre-ingeniería para este proyecto.
- **Excepciones de dominio específicas por tipo, no un enum `ErrorCode` centralizado.** Cambio consciente: se ganó pureza (dominio sin `HttpStatus` de Spring) a cambio de más clases y un `switch` en el handler en vez de un único `.getStatus()`. Ver sección "Manejo de errores".

## Estado

✅ Migración completa. Sin controllers ni servicios legacy, sin carpetas técnicas sueltas en la raíz del paquete, sin acoplamiento de Spring en `domain/`. Compilación limpia (`BUILD SUCCESS`), arranque de Spring sin beans faltantes/duplicados, y los 7 endpoints + 7 escenarios de error verificados con MongoDB real.
