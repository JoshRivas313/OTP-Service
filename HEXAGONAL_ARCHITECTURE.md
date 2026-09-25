# Arquitectura Hexagonal (Puertos y Adaptadores)

## Descripción

El backend está organizado en arquitectura hexagonal: el dominio (reglas de negocio, modelo y value objects) no depende de Spring MVC, Spring Data ni de ningún SDK externo. Todo lo técnico entra o sale a través de **puertos** (interfaces en `domain/port`), implementados por **adaptadores** concretos en `adapter/`. Solo quedan 4 paquetes de primer nivel: `domain`, `application`, `adapter`, `shared`.

## Estructura de Carpetas (real, verificada por compilación)

```
src/main/java/com/otpservice/otp/
├── domain/                              # Núcleo del negocio
│   ├── model/
│   │   └── OtpDocument                   # Modelo de estado del OTP (isExpired,
│   │                                      # isBlocked, isUsed). Ver "Decisiones" abajo.
│   ├── valueobject/
│   │   ├── Cellphone                     # Teléfono E.164, deserializable desde JSON
│   │   ├── OtpCode                       # Código OTP (4-10 dígitos)
│   │   ├── ValidityWindow                # Ventana de validez
│   │   ├── VerificationStatus            # Intentos, usado, invalidado
│   │   └── TwilioCredentials             # Credenciales Twilio validadas por regex
│   └── port/
│       ├── input/                        # Casos de uso
│       │   ├── GenerateOtpUseCase
│       │   ├── VerifyOtpUseCase
│       │   ├── TwilioGenerateOtpUseCase
│       │   └── TwilioVerifyOtpUseCase
│       └── output/                       # Lo que el dominio necesita del exterior
│           ├── OtpPersistencePort         # Persistencia (atómica)
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
│   │   ├── GlobalExceptionHandler         # @RestControllerAdvice: OtpException -> HTTP
│   │   └── dto/
│   │       ├── request/                   # 5 DTOs de request (con @Valid)
│   │       └── response/                  # 4 DTOs de response
│   │
│   ├── out/
│   │   ├── persistence/
│   │   │   ├── OtpPersistenceAdapter       # Implementa OtpPersistencePort
│   │   │   ├── OtpRepository               # Spring Data MongoRepository
│   │   │   ├── OtpRepositoryCustom         # Operaciones atómicas (interfaz)
│   │   │   └── impl/OtpRepositoryImpl      # Update atómico vía MongoTemplate
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
│
└── shared/exception/                     # Vocabulario de error compartido
    ├── ErrorCode                          # Enum: código estable + HttpStatus + mensaje
    └── OtpException                       # Excepción de negocio (lanzada por use cases)
```

## Flujo de Dependencias

```
HTTP Request
    ↓
adapter/in/http/*HttpAdapter            ← traduce HTTP a comandos
    ↓
application/usecase/*UseCaseImpl        ← orquesta reglas de negocio
    ↓
domain/valueobject + domain/model       ← validan e invariantes puras
    ↓
domain/port/output (interfaces)         ← OtpPersistencePort, SmsSender, CodeHasherPort
    ↓
adapter/out/*                            ← OtpPersistenceAdapter, ConsoleSmsSender, CodeHasher...
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
4. Persiste OtpDocument con TTL de purga (OtpPersistencePort)
5. Envía SMS (SmsSender por defecto, o uno inyectado — ver Twilio)
Output: OtpGenerateResponse (success, message, demoCode si demoMode=true)
```

### VerifyOtpUseCase
```
Input:  { cellphone, code }
1. Busca el OTP más reciente del celular
2. Verifica: no invalidado → no usado → no expirado → no bloqueado
3. Compara hash; si coincide, claim atómico (claimIfMatches) marca como usado
4. Si no coincide, registra intento fallido atómicamente y evalúa bloqueo
Output: OtpVerifyResponse | OtpException (401/422/423 según ErrorCode)
```

### TwilioGenerateOtpUseCase / TwilioVerifyOtpUseCase
No duplican lógica: delegan a `GenerateOtpUseCase`/`VerifyOtpUseCase`, sustituyendo el `SmsSender` por uno que usa las credenciales Twilio de la sesión HTTP (`TwilioSessionSmsSender`), en vez del proveedor global configurado por `sms.provider`.

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

Todos verificados end-to-end con MongoDB real corriendo en Docker (generar, verificar, código incorrecto, credenciales Twilio inválidas, sesión no conectada).

## Decisiones Pragmáticas (y por qué)

- **`OtpDocument` vive en `domain/model`, no en `adapter/out/persistence`.** Tiene anotaciones de Spring Data Mongo (`@Document`, `@Indexed` con TTL), pero los casos de uso (`application/usecase`) necesitan construirlo y leer su estado (`isExpired`, `isBlocked`, `isUsed`) directamente. Ponerlo en `adapter/` habría hecho que `application` importara desde `adapter`, invirtiendo la dirección de dependencia permitida. La alternativa "pura" (un agregado de dominio sin anotaciones + una capa de mapeo hacia un documento Mongo separado) se descartó: no aporta valor real a este tamaño de proyecto y complica sin necesidad.
- **No hay una capa de mapeo entre `OtpDocument` y Mongo.** `OtpRepository`/`OtpRepositoryImpl` (en `adapter/out/persistence`) operan directamente sobre `OtpDocument` vía Spring Data.
- **`OtpPersistencePort` refleja operaciones atómicas de Mongo**, no un CRUD genérico: `claimIfMatches` y `registerFailedAttempt` son updates atómicos (`findAndModify`) para que verificaciones concurrentes del mismo código no generen condiciones de carrera. Un agregado "rico" reconstruido en memoria habría roto esa garantía.
- **`SmsSender` y `CodeHasherPort` son los únicos puertos de salida "técnicos".** `SmsSender` ya era una interfaz pura antes de la migración (se movió, no se duplicó). `CodeHasherPort` se creó nuevo para que `CodeHasher` (HMAC-SHA256, en `adapter/out/security`) sea intercambiable sin tocar los casos de uso.
- **`OtpProperties`/`SmsProperties` (en `adapter/config`) se inyectan directamente en `application/usecase`.** Son `record`s de solo configuración (`@ConfigurationProperties`, sin lógica ni dependencias pesadas). Crear un puerto para "leer configuración" habría sido sobre-ingeniería para este proyecto.
- **`ErrorCode`/`OtpException` viven en `shared/exception`**, no en `domain/`, porque `ErrorCode` incluye un `HttpStatus` de Spring Web — es vocabulario de error compartido entre capas, no una regla de negocio pura.

## Estado

✅ Migración completa. Sin controllers ni servicios legacy, sin carpetas técnicas sueltas en la raíz del paquete. Compilación limpia (`BUILD SUCCESS`), arranque de Spring sin beans faltantes/duplicados, y los 7 endpoints verificados con MongoDB real.
