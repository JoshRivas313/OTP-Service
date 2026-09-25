# Arquitectura Hexagonal (Puertos y Adaptadores)

## Descripción

El backend está organizado en arquitectura hexagonal: el dominio (reglas de negocio y value objects) no depende de Spring, MongoDB ni de ningún framework. Todo lo técnico entra o sale a través de **puertos** (interfaces), implementados por **adaptadores** concretos.

## Estructura de Capas

```
src/main/java/com/otpservice/otp/
├── domain/                          # Núcleo del negocio (sin dependencias de framework)
│   ├── valueobject/                 # Value Objects inmutables y autovalidados
│   │   ├── Cellphone                # Teléfono E.164, deserializable desde JSON
│   │   ├── OtpCode                  # Código OTP (4-10 dígitos), generación con SecureRandom
│   │   ├── ValidityWindow           # Ventana de validez (generatedAt/expiresAt)
│   │   ├── VerificationStatus       # Intentos, usado, invalidado
│   │   └── TwilioCredentials        # Credenciales Twilio validadas por regex
│   └── port/
│       ├── input/                   # Casos de uso (lo que el mundo exterior puede pedir)
│       │   ├── GenerateOtpUseCase
│       │   ├── VerifyOtpUseCase
│       │   ├── TwilioGenerateOtpUseCase
│       │   └── TwilioVerifyOtpUseCase
│       └── output/                  # Lo que el dominio necesita del mundo exterior
│           ├── OtpPersistencePort   # Persistencia (invalidar, guardar, claim atómico)
│           └── SmsSender            # Envío de SMS (implementado por 3 proveedores)
│
├── application/usecase/             # Orquestación de los casos de uso
│   ├── GenerateOtpUseCaseImpl        # Genera código, hashea, persiste, envía SMS
│   ├── VerifyOtpUseCaseImpl          # Valida expiración/bloqueo/hash, intentos atómicos
│   ├── TwilioGenerateOtpUseCaseImpl  # Delega a GenerateOtpUseCase con SmsSender de sesión
│   └── TwilioVerifyOtpUseCaseImpl    # Delega a VerifyOtpUseCase
│
├── adapter/                         # Detalles técnicos (entrada y salida)
│   ├── in/http/                     # Controllers REST (adaptadores de entrada)
│   │   ├── OtpHttpAdapter            # POST /otps, /otps/verify
│   │   ├── TwilioOtpHttpAdapter      # POST /api/twilio/otps[/verify]
│   │   └── TwilioConnectHttpAdapter  # POST/GET /api/twilio/connect|status|disconnect
│   └── out/persistence/
│       └── OtpPersistenceAdapter     # Implementa OtpPersistencePort delegando a OtpRepository
│
├── document/OtpDocument             # Documento Mongo (TTL index, compound index) con
│                                     # lógica de estado (isExpired, isBlocked, isUsed)
├── repository/                      # Adaptador técnico Spring Data Mongo
│   ├── OtpRepository                 # MongoRepository + OtpRepositoryCustom
│   ├── OtpRepositoryCustom            # Operaciones atómicas (claimIfMatches, etc.)
│   └── impl/OtpRepositoryImpl         # Update atómico vía MongoTemplate
│
├── sms/                              # Adaptadores de salida SMS (implementan SmsSender)
│   ├── ConsoleSmsSender               # Debug: imprime en logs (default)
│   ├── TwilioSmsSender                # SMS real vía credenciales globales del proyecto
│   ├── InfobipSmsSender               # SMS real vía Infobip
│   └── twilioconnect/
│       ├── TwilioSessionService        # Guarda/lee TwilioCredentials en HttpSession
│       ├── TwilioSessionSmsSender      # Envía SMS con credenciales de sesión (no global)
│       └── TwilioVerifyService         # Valida credenciales contra la API de Twilio
│
├── dto/{request,response}            # DTOs de entrada/salida HTTP
├── security/CodeHasher               # HMAC-SHA256 de códigos OTP
├── exception/                        # ErrorCode, OtpException, GlobalExceptionHandler
└── config/                           # ClockConfig, OtpProperties, SmsProperties, filtros
```

## Flujo de Dependencias

```
HTTP Request
    ↓
HttpAdapter (adapter/in/http)          ← traduce HTTP a comandos de dominio
    ↓
UseCase (application/usecase)          ← orquesta reglas de negocio
    ↓
Value Objects (domain/valueobject)     ← validan e invariantes puras
    ↓
Puertos de salida (domain/port/output) ← interfaces (OtpPersistencePort, SmsSender)
    ↓
Adaptadores de salida                  ← OtpPersistenceAdapter, ConsoleSmsSender, etc.
    ↓
MongoDB / Twilio / Infobip
```

El dominio nunca importa Spring Data, Tomcat ni el SDK de Twilio directamente; solo conoce sus propios puertos.

## Casos de Uso

### GenerateOtpUseCase
```
Input:  { cellphone, digits?, durationSeconds? }
1. Invalida cualquier OTP activo previo del mismo celular (operación atómica)
2. Genera OtpCode con SecureRandom
3. Hashea con HMAC-SHA256 (CodeHasher)
4. Persiste OtpDocument con TTL de purga
5. Envía SMS (sender por defecto o uno inyectado — ver Twilio)
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
No duplican lógica: delegan a `GenerateOtpUseCase`/`VerifyOtpUseCase`, sustituyendo el `SmsSender` por uno que usa las credenciales Twilio guardadas en la sesión HTTP (`TwilioSessionSmsSender`), en vez del proveedor global configurado por `sms.provider`.

## Por qué las operaciones de persistencia son atómicas

`VerifyOtpUseCase` no hace "leer documento → mutar en memoria → guardar": eso sería vulnerable a condiciones de carrera con verificaciones concurrentes del mismo código. En su lugar, `OtpPersistencePort.claimIfMatches(...)` y `registerFailedAttempt(...)` ejecutan updates atómicos directamente en MongoDB (vía `OtpRepositoryImpl` + `MongoTemplate`), igual que antes de la migración. El puerto refleja esas operaciones en vez de forzar un agregado de dominio "rico" que rompería esa garantía.

## Endpoints

| Método | Ruta | Adaptador | Descripción |
|---|---|---|---|
| POST | `/otps` | OtpHttpAdapter | Genera OTP local |
| POST | `/otps/verify` | OtpHttpAdapter | Verifica OTP local |
| POST | `/api/twilio/connect` | TwilioConnectHttpAdapter | Conecta credenciales Twilio a la sesión |
| GET | `/api/twilio/status` | TwilioConnectHttpAdapter | Estado de conexión |
| POST | `/api/twilio/disconnect` | TwilioConnectHttpAdapter | Limpia la sesión |
| POST | `/api/twilio/otps` | TwilioOtpHttpAdapter | Genera OTP vía Twilio (SMS real) |
| POST | `/api/twilio/otps/verify` | TwilioOtpHttpAdapter | Verifica OTP vía Twilio |

Todos verificados end-to-end tras la migración (curl + MongoDB real).

## Ventajas

1. **Testabilidad**: Use Cases se testean con mocks de `OtpPersistencePort`/`SmsSender`, sin Spring ni Mongo.
2. **Extensibilidad**: un nuevo proveedor SMS = una clase nueva implementando `SmsSender` con `@ConditionalOnProperty`.
3. **Claridad**: la regla de negocio (expiración, bloqueo, hash) vive en un solo lugar por caso de uso.
4. **Cero acoplamiento de dominio**: `domain/valueobject` y `domain/port` no importan `org.springframework.*` (excepto `@Service` en las implementaciones de `application`, que sí son detalles de infraestructura de inyección, no de dominio puro).

## Decisiones Pragmáticas

- **`OtpDocument` vive fuera de `domain/`**: tiene anotaciones de Spring Data Mongo (`@Document`, `@Indexed` con TTL). Se trata como el modelo de persistencia que los puertos de salida referencian directamente, evitando una capa de mapeo 1:1 sin valor añadido real para este tamaño de proyecto.
- **`SmsSender` es el puerto de salida SMS**, no una interfaz nueva duplicada: ya era una interfaz pura (`send(Cellphone, String)`) antes de la migración, así que se movió a `domain/port/output` en vez de reinventarse.
- **No hay un "OtpAggregate" separado de `OtpDocument`**: introducirlo forzaría reconstruir en memoria una entidad y perder las operaciones atómicas de Mongo (`findAndModify`) que garantizan consistencia bajo concurrencia.

## Estado

✅ Migración completa. Sin controllers ni servicios legacy — `adapter/in/http/*` son los únicos controllers, `application/usecase/*` son los únicos orquestadores. Compilación limpia (`BUILD SUCCESS`) y endpoints verificados con MongoDB real corriendo en Docker.
