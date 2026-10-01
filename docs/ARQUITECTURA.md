# Arquitectura

El backend sigue una arquitectura hexagonal (puertos y adaptadores) organizada en tres capas: `domain`, `application` y `adapter`. La regla de dependencia es una sola:

```
adapter  ->  application  ->  domain
```

`domain` no importa nada de `application` ni de `adapter`, y `application` no importa nada de `adapter` (con una excepción conocida, ver [Limitaciones y deuda técnica](#limitaciones-y-deuda-técnica)).

## Contenido

- [Estructura de paquetes](#estructura-de-paquetes)
- [Flujo de una petición](#flujo-de-una-petición)
- [Manejo de errores](#manejo-de-errores)
- [Decisiones de diseño](#decisiones-de-diseño)
- [Limitaciones y deuda técnica](#limitaciones-y-deuda-técnica)

---

## Estructura de paquetes

```
src/main/java/com/otpservice/otp/
├── domain/                                Reglas de negocio, sin Spring
│   ├── model/Otp                           Modelo del OTP (isExpired, isBlocked, isUsed...)
│   ├── model/HmacCredential                Secreto de HOTP/TOTP con su contador o ventana (APP o DELIVERED)
│   ├── valueobject/                        Destination, Cellphone, EmailAddress, OtpCode,
│   │                                       ValidityWindow, VerificationStatus, TwilioCredentials,
│   │                                       OtpProtocol, HmacType, CredentialMode, CredentialStatus,
│   │                                       AuthenticatorSecret, EncryptedSecret,
│   │                                       OtpAuthUri
│   ├── service/                            HmacOtpAlgorithm (RFC 4226/6238), TotpVerifier,
│   │                                       HotpVerifier, CodeMatch, Base32
│   └── exception/                          Reglas de negocio que pueden fallar
│       ├── OtpDomainException               Base: código de error + mensaje, sin HTTP
│       ├── OtpExpiredException
│       ├── OtpBlockedException
│       ├── OtpAlreadyUsedException
│       ├── OtpInvalidatedException
│       ├── InvalidOtpException
│       └── InvalidCodeRequestException, AuthenticatorLockedException, EnrollmentNotConfirmedException
│
├── application/                           Casos de uso
│   ├── port/
│   │   ├── in/    GenerateOtpUseCase, VerifyOtpUseCase, EnrollAuthenticatorUseCase,
│   │   │          ConfirmEnrollmentUseCase, VerifyAuthenticatorCodeUseCase, RemoveEnrollmentUseCase
│   │   └── out/   OtpPersistencePort, MessageSender, SmsSender, EmailSender,
│   │              CodeHasherPort, CredentialPersistencePort, SecretCipherPort, QrCodePort
│   ├── usecase/   GenerateOtpUseCaseImpl, VerifyOtpUseCaseImpl, EnrollAuthenticatorUseCaseImpl,
│   │              ConfirmEnrollmentUseCaseImpl, VerifyAuthenticatorCodeUseCaseImpl,
│   │              RemoveEnrollmentUseCaseImpl, AuthenticatorCodeChecker, DeliveredHmacCodes
│   ├── config/    AuthenticatorSettings
│   ├── dto/       GenerateOtpResult, VerifyOtpResult, EnrollmentResult, AuthenticatorVerifyResult
│   └── exception/ OtpNotFoundException, EnrollmentNotFoundException
│
└── adapter/                               Todo lo técnico
    ├── in/http/
    │   ├── OtpHttpAdapter                   POST /otps, /otps/verify
    │   ├── TwilioOtpHttpAdapter             POST /api/twilio/otps, /api/twilio/otps/verify
    │   ├── EmailOtpHttpAdapter              POST /api/email/otps, /api/email/otps/verify
    │   ├── TwilioConnectHttpAdapter         /api/twilio/connect, /status, /disconnect
    │   ├── AuthenticatorHttpAdapter         /api/authenticator/enrollments, /confirm, /verify
    │   ├── EmailVerificationSession         Marca de sesión: correo verificado con OTP
    │   ├── GlobalExceptionHandler           Excepciones -> códigos HTTP
    │   └── dto/{request,response}
    ├── out/
    │   ├── persistence/                     OtpPersistenceAdapter, OtpRepository,
    │   │   ├── document/OtpDocument          OtpRepositoryCustom + impl (perfil mongo)
    │   │   ├── mapper/OtpPersistenceMapper
    │   │   ├── CredentialPersistenceAdapter + document/HmacCredentialDocument (perfil mongo)
    │   │   └── memory/InMemoryOtpPersistenceAdapter, InMemoryCredentialAdapter   Por defecto (perfil !mongo)
    │   ├── security/CodeHasher              Implementa CodeHasherPort (HMAC-SHA256)
    │   ├── security/AesGcmSecretCipher      Implementa SecretCipherPort (AES-256-GCM)
    │   ├── qr/ZxingQrCodeAdapter            Implementa QrCodePort (SVG)
    │   ├── email/                           ConsoleEmailSender, BrevoEmailSender
    │   └── sms/                             ConsoleSmsSender, TwilioSmsSender, InfobipSmsSender
    │       └── twilio/                      TwilioSessionService, TwilioSessionSmsSender,
    │                                        TwilioVerifyService
    ├── exception/                           SmsDeliveryFailedException, EmailDeliveryFailedException,
    │                                        RateLimitExceededException, EmailNotVerifiedException,
    │                                        TwilioCredentialsInvalidException,
    │                                        TwilioNotConnectedException, DestinationNotVerifiedException
    └── config/                              ClockConfig, OtpProperties, SmsProperties, EmailProperties,
                                             AuthenticatorProperties, AuthenticatorConfig,
                                             SendRateLimiter,
                                             DemoModeGuard, OpenApiConfig,
                                             TwilioOnboardingFilter
```

Los recursos estáticos (interfaz web) están en `src/main/resources/static/`.

---

## Flujo de una petición

```
Cliente HTTP
    │
    ▼
adapter/in/http/*HttpAdapter        Traduce Request -> Command y Result -> Response
    │
    ▼
application/port/in                 GenerateOtpUseCase / VerifyOtpUseCase
    │
    ▼
application/usecase                 Orquesta la regla de negocio
    │        usa domain (Otp, Cellphone, OtpCode, excepciones)
    ▼
application/port/out                OtpPersistencePort, MessageSender, SmsSender,
                                    EmailSender, CodeHasherPort
    │
    ▼
adapter/out/*                       Memoria o MongoDB, Console/Twilio/Infobip/Brevo, HMAC-SHA256
```

Los casos de uso nunca reciben ni devuelven objetos HTTP: reciben un `Command` y devuelven un `Result`, y el adaptador HTTP los convierte de y hacia el JSON de la API.

Los diagramas de los flujos de uso están en el [README](../README.md#flujos-de-uso), y los de verificación, ciclo de vida y proveedores en [FLUJOS.md](./FLUJOS.md).

---

## Manejo de errores

Cada regla de negocio que puede fallar tiene su propia excepción, y todas extienden `OtpDomainException`, que solo lleva un código estable y un mensaje, sin estado HTTP. Se reparten según dónde nacen:

| Capa | Excepciones | Motivo |
|---|---|---|
| `domain/exception` | `OtpExpiredException`, `OtpBlockedException`, `OtpAlreadyUsedException`, `OtpInvalidatedException`, `InvalidOtpException` | Responden preguntas que hace el propio modelo `Otp` |
| `application/exception` | `OtpNotFoundException` | Resultado vacío de un puerto, decidido por el caso de uso |
| `adapter/exception` | `SmsDeliveryFailedException`, `TwilioCredentialsInvalidException`, `TwilioNotConnectedException`, `DestinationNotVerifiedException` | Solo se lanzan desde adaptadores |

`GlobalExceptionHandler` es el único lugar que conoce el código HTTP de cada una, con un `switch` de pattern matching sobre el tipo. La tabla completa está en [API.md](./API.md#códigos-de-error).

---

## Decisiones de diseño

- **Los puertos viven en `application/port`.** Los casos de uso y sus `Command` son propios de esta aplicación; `domain` queda reducido a modelo, value objects y reglas.
- **El destino es un concepto, no un celular.** `Destination` (con `Cellphone` y `EmailAddress`) es lo único que conocen los casos de uso y la persistencia; el canal solo importa en el borde. Cada adaptador HTTP entrega a `GenerateOtpUseCase.generate(command, sender)` un `MessageSender` ya atado a su canal, sin conversiones de tipo.
- **Solo hay dos casos de uso: generar y verificar.** Enviar con Twilio usando las credenciales de la sesión no es una regla de negocio distinta: solo cambia qué `MessageSender` se usa, y esa elección depende de datos de la petición HTTP (la sesión). Por eso `TwilioOtpHttpAdapter` crea un `SmsSender` con las credenciales de la sesión y se lo pasa a `GenerateOtpUseCase.generate(command, sender)`.
- **`Otp` (dominio) y `OtpDocument` (MongoDB) son clases separadas**, con `OtpPersistenceMapper` entre ambas. El dominio no conoce anotaciones de Spring Data.
- **El almacenamiento es un puerto con dos adaptadores.** `InMemoryOtpPersistenceAdapter` es el predeterminado y `OtpPersistenceAdapter` (MongoDB) se activa con el perfil `mongo`, que además es el único que carga la configuración de Spring Data MongoDB. Los casos de uso no cambian.
- **La persistencia expone operaciones atómicas, no un CRUD.** `claimIfMatches`, `registerFailedAttempt` e `invalidateActive` son `findAndModify` / `updateMulti` en MongoDB, o secciones críticas bajo un lock en memoria. Un esquema de "leer, modificar en memoria, guardar" permitiría que dos verificaciones simultáneas del mismo código pasen las dos. Por eso `Otp` es un objeto de solo lectura sin métodos que cambien su estado.
- **El tiempo entra por `java.time.Clock`.** No hay `Instant.now()` en el código.
- **`domain` no conoce HTTP.** Los casos de uso devuelven `GenerateOtpResult` / `VerifyOtpResult`, no los DTO de la API.
- **Agregar un proveedor de SMS es agregar una clase.** Implementa `SmsSender`, lleva `@ConditionalOnProperty(name = "sms.provider", havingValue = "...")` y sus propiedades van en `SmsProperties`. Ver [PROVEEDORES_SMS.md](./PROVEEDORES_SMS.md).
- **La interfaz web es HTML, CSS y JavaScript sin framework**, servida por Spring Boot desde `static/`.
- **El canal y el protocolo son independientes.** El canal (SMS, correo o app) solo decide cómo llega el código; el protocolo decide cómo se obtiene y cómo se comprueba. OTP guarda el hash del código en `OtpPersistencePort`. HOTP y TOTP guardan un `HmacCredential` (secreto cifrado y contador o ventana) y verifican recalculando; `CredentialMode` distingue si el código lo calcula el servidor y lo envía (`DELIVERED`) o la app del usuario (`APP`).
- **El secreto de las apps se cifra, no se hashea.** Hay que poder recuperarlo para recalcular. AES-256-GCM usa como datos asociados `correo|tipo`, así un secreto copiado a otro registro no se puede descifrar.
- **Vincular una app exige haber verificado el correo con OTP en la misma sesión.** Sin cuentas de usuario, es lo que impide que alguien vincule su app al correo de otro.
- **Los casos de uso de la app autenticadora no importan `adapter.config`.** Reciben `AuthenticatorSettings`, un record de `application` que construye `AuthenticatorConfig`; no repiten la deuda del punto 1 de la sección siguiente.

---

## Limitaciones y deuda técnica

1. **`application` importa `adapter.config.OtpProperties`** (`GenerateOtpUseCaseImpl`, `VerifyOtpUseCaseImpl`). Es la única dependencia de `application` hacia `adapter`. Mover ese `record` de configuración a `application` la eliminaría.
2. **`domain` usa anotaciones de Jackson** en `Cellphone` y `OtpCode` (`@JsonCreator`, `@JsonValue`) y Lombok en `Otp` (solo en compilación). No hay Spring, MongoDB, Twilio ni HTTP en `domain`, pero no compila como Java puro sin las anotaciones de Jackson.
3. **El almacenamiento en memoria no sobrevive a un reinicio ni se comparte entre instancias.** Ver [BASE_DE_DATOS.md](./BASE_DE_DATOS.md#almacenamiento-en-memoria).
4. **Los errores de formato devuelven el cuerpo estándar de Spring.** Un celular o código con formato inválido y un JSON malformado responden `400` con `{"timestamp", "status", "error", "path"}`, no con el formato `{success, code, message}` del resto de la API.
5. **Sin autenticación ni límite de envíos** en los endpoints. Ver [Limitaciones conocidas](../README.md#limitaciones-conocidas).
