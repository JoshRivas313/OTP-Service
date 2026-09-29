# Arquitectura Hexagonal (Puertos y Adaptadores)

## Descripción

El backend está organizado en tres capas: `domain`, `application` y `adapter`. La regla de dependencia es una sola: **`adapter → application → domain`**, nunca al revés. Los puertos (interfaces) viven en `application/port` y los implementan los adaptadores.

## Estructura de carpetas (real, verificada por compilación)

```
src/main/java/com/otpservice/otp/
├── domain/                              # Reglas de negocio puras
│   ├── model/Otp                         # Modelo sin persistencia (isExpired, isBlocked, isUsed...)
│   ├── valueobject/                      # Cellphone, OtpCode, ValidityWindow,
│   │                                      # VerificationStatus, TwilioCredentials
│   └── exception/                        # Solo reglas de negocio del OTP
│       ├── OtpDomainException             # Base: errorCode + mensaje, sin HttpStatus
│       ├── OtpExpiredException
│       ├── OtpBlockedException
│       ├── OtpAlreadyUsedException
│       ├── OtpInvalidatedException
│       └── InvalidOtpException
│
├── application/                         # Orquestación de casos de uso
│   ├── port/
│   │   ├── in/    GenerateOtpUseCase, VerifyOtpUseCase
│   │   └── out/   OtpPersistencePort, SmsSender, CodeHasherPort
│   ├── usecase/   GenerateOtpUseCaseImpl, VerifyOtpUseCaseImpl
│   ├── dto/       GenerateOtpResult, VerifyOtpResult      # resultado del caso de uso,
│   │                                                       # independiente del JSON HTTP
│   └── exception/ OtpNotFoundException                    # decisión de orquestación
│
└── adapter/                             # Todo lo técnico
    ├── in/http/
    │   ├── OtpHttpAdapter                 # POST /otps, /otps/verify
    │   ├── TwilioOtpHttpAdapter           # POST /api/twilio/otps[/verify]
    │   ├── TwilioConnectHttpAdapter       # POST/GET /api/twilio/connect|status|disconnect
    │   ├── GlobalExceptionHandler         # OtpDomainException -> HttpStatus (único sitio)
    │   └── dto/{request,response}
    ├── out/
    │   ├── persistence/                   # OtpPersistenceAdapter, OtpRepository(+Custom/Impl atómico),
    │   │   ├── document/OtpDocument       #   OtpDocument (@Document, TTL) y OtpPersistenceMapper
    │   │   └── mapper/OtpPersistenceMapper
    │   ├── security/CodeHasher            # implementa CodeHasherPort (HMAC-SHA256)
    │   └── sms/                           # ConsoleSmsSender, TwilioSmsSender, InfobipSmsSender
    │       └── twilio/                    #   + TwilioSessionService/SmsSender/VerifyService
    ├── exception/                         # Fallos que solo nacen en adaptadores
    │   ├── SmsDeliveryFailedException
    │   ├── TwilioCredentialsInvalidException
    │   └── TwilioNotConnectedException
    └── config/                            # ClockConfig, OtpProperties, SmsProperties,
                                            # DemoModeGuard, OpenApiConfig, TwilioOnboardingFilter
```

## Flujo

```
HTTP -> adapter/in/http/*HttpAdapter -> application/port/in -> application/usecase
     -> domain (Otp, value objects, excepciones)
     -> application/port/out -> adapter/out/* -> MongoDB / Twilio / Infobip
```

Los `HttpAdapter` traducen `Request → Command` y `Result → Response`. Los casos de uso nunca ven ni devuelven un DTO HTTP.

## Decisiones

- **Puertos en `application/port`, no en `domain`.** Los casos de uso y sus `Command` son específicos de esta aplicación; el dominio queda reducido a modelo, value objects y reglas.
- **Un solo par de casos de uso (Generate/Verify), sin variantes Twilio.** Las antiguas `TwilioGenerateOtpUseCase`/`TwilioVerifyOtpUseCase` no contenían reglas de negocio: solo armaban un `SmsSender` con las credenciales de la `HttpSession` y delegaban. Esa elección depende de datos de la request, así que la hace `TwilioOtpHttpAdapter`, que pasa un `SmsSender` propio a `GenerateOtpUseCase.generate(command, sender)`. Ningún comportamiento cambió.
- **Excepciones clasificadas por dónde se lanzan.** Dominio: las que responden preguntas del modelo `Otp`. Aplicación: `OtpNotFoundException` (resultado vacío del puerto). Adaptador: las que solo se lanzan en adaptadores (SMS/Twilio). Todas extienden `OtpDomainException`, así `GlobalExceptionHandler` sigue mapeándolas con un único `switch` (Java 21). Mover un archivo de paquete no cambia la jerarquía.
- **`Otp` (dominio) separado de `OtpDocument` (Mongo)**, con `OtpPersistenceMapper`. Las mutaciones (`claimIfMatches`, `registerFailedAttempt`, `invalidateActive`) siguen siendo `findAndModify`/`updateMulti` atómicos en `OtpRepositoryImpl`; `Otp` es un snapshot inmutable a propósito y **no** tiene métodos mutadores, para no sugerir una mutación en memoria que no es la fuente de verdad.
- **`Clock` inyectado, sin `TimeProvider`.** No hay ningún `Instant.now()`/`LocalDateTime.now()` en `src/main`; `Clock.fixed(...)` ya da testabilidad.
- **Se mantiene `adapter/` (sin `infrastructure/`).** Renombrarlo no cambia ninguna regla de dependencia.
- **`TwilioCredentials` sigue en `domain/valueobject`.** Es un value object sin framework; moverlo a `adapter` obligaría a los puertos a importar desde `adapter`.

## Deuda técnica conocida

1. **`application` importa `adapter.config.OtpProperties`** (`GenerateOtpUseCaseImpl`, `VerifyOtpUseCaseImpl`). Es la única dependencia `application → adapter`. Solución barata pendiente de decidir: mover `OtpProperties` (un `record` de configuración) a `application`.
2. **`domain` depende de Jackson** en `Cellphone` y `OtpCode` (`@JsonCreator`/`@JsonValue`) y de Lombok en `Otp` (solo compilación). No hay Spring, Mongo, Twilio ni HTTP en `domain`, pero no compila como Java puro. Quitar Jackson exige registrar el binding en `adapter` (mixin/deserializador) sin alterar los errores de validación actuales.
3. **Sin tests.** No existe `src/test`; la verificación es manual (compilación + curl contra Mongo).
4. `OtpRepositoryCustom.findPreviousWithCode` está implementado y no se usa.

## Reglas de arquitectura sugeridas (ArchUnit, no implementadas)

- `domain` no depende de `application` ni de `adapter`.
- `application` no depende de `adapter`.
- Solo `adapter.in.http` contiene `@RestController`/`@RestControllerAdvice`.
- Solo `adapter.out.persistence` usa `org.springframework.data.mongodb`.
- Ninguna clase de `application`/`domain` referencia `..adapter.in.http.dto..`.
