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
│   ├── model/OtpStatus                     Estado de un código; el orden de declaración es la prioridad
│   ├── model/HmacCredential                Secreto de HOTP/TOTP con su contador o ventana
│   ├── valueobject/                        Destination, Cellphone, EmailAddress, OtpCode,
│   │                                       ValidityWindow, VerificationStatus, TwilioCredentials,
│   │                                       OtpProtocol, HmacType, HmacSecret, EncryptedSecret
│   ├── service/                            HmacOtpAlgorithm (RFC 4226/6238), TotpVerifier,
│   │                                       HotpVerifier, CodeMatch
│   └── exception/                          Reglas de negocio que pueden fallar
│       ├── ErrorCode                        Todos los códigos de error de la API, en un enum
│       ├── OtpDomainException               Base: ErrorCode + mensaje, sin HTTP
│       ├── OtpExpiredException
│       ├── OtpBlockedException
│       ├── OtpAlreadyUsedException
│       ├── OtpInvalidatedException
│       ├── InvalidOtpException
│       └── InvalidCodeRequestException
│
├── application/                           Casos de uso
│   ├── port/
│   │   ├── in/    GenerateOtpUseCase, VerifyOtpUseCase
│   │   └── out/   OtpPersistencePort, MessageSender, SmsSender, EmailSender,
│   │              CodeHasherPort, CredentialPersistencePort, SecretCipherPort
│   ├── protocol/  CodeProtocol, CodeProtocols, RandomCodeProtocol, HmacCodeProtocol,
│   │              HotpProtocol, TotpProtocol, IssuedCode, VerifiedCode
│   ├── usecase/   GenerateOtpUseCaseImpl, VerifyOtpUseCaseImpl, OtpMessage
│   ├── config/    HmacSettings, OtpSettings
│   ├── dto/       GenerateOtpResult, VerifyOtpResult
│   └── exception/ OtpNotFoundException
│
└── adapter/                               Todo lo técnico
    ├── in/http/
    │   ├── OtpHttpAdapter                   POST /otps, /otps/verify
    │   ├── TwilioOtpHttpAdapter             POST /api/twilio/otps, /api/twilio/otps/verify
    │   ├── EmailOtpHttpAdapter              POST /api/email/otps, /api/email/otps/verify
    │   ├── TwilioConnectHttpAdapter         /api/twilio/connect, /status, /disconnect
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
    │   ├── email/                           ConsoleEmailSender, BrevoEmailSender
    │   └── sms/                             ConsoleSmsSender, TwilioSmsSender, InfobipSmsSender
    │       └── twilio/                      TwilioGateway, TwilioSessionService,
    │                                        TwilioSessionSmsSender, TwilioVerifyService
    ├── exception/                           SmsDeliveryFailedException, EmailDeliveryFailedException,
    │                                        RateLimitExceededException,
    │                                        TwilioCredentialsInvalidException,
    │                                        TwilioNotConnectedException, DestinationNotVerifiedException
    └── config/                              ClockConfig, OtpProperties, OtpConfig, SmsProperties,
                                             SmsProvider, EmailProperties, EmailProvider,
                                             HmacProperties, HmacConfig, SendRateLimiter,
                                             VerifyRateLimiter, SlidingWindowLimiter,
                                             DemoModeGuard, ProductionSecretsGuard, OpenApiConfig
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

Cada regla de negocio que puede fallar tiene su propia excepción, y todas extienden `OtpDomainException`, que solo lleva un `ErrorCode` y un mensaje, sin estado HTTP. `ErrorCode` es un enum: el nombre de cada valor es el código estable que ve el cliente (`OTP_EXPIRED`, `RATE_LIMIT_EXCEEDED`…). Se reparten según dónde nacen:

| Capa | Excepciones | Motivo |
|---|---|---|
| `domain/exception` | `OtpExpiredException`, `OtpBlockedException`, `OtpAlreadyUsedException`, `OtpInvalidatedException`, `InvalidOtpException` | Responden preguntas que hace el propio modelo `Otp` |
| `application/exception` | `OtpNotFoundException` | Resultado vacío de un puerto, decidido por el caso de uso |
| `adapter/exception` | `SmsDeliveryFailedException`, `TwilioCredentialsInvalidException`, `TwilioNotConnectedException`, `DestinationNotVerifiedException` | Solo se lanzan desde adaptadores |

`GlobalExceptionHandler` es el único lugar que conoce el estado HTTP de cada código, con un `switch` sobre `ErrorCode` sin `default`: si se agrega un código y no se mapea, no compila. La tabla completa está en [API.md](./API.md#códigos-de-error).

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
- **El propósito es parte de la identidad del código.** `Purpose` (`LOGIN`, `REGISTER`, `PASSWORD_RECOVERY`, `PAYMENT_CONFIRMATION`) viaja en `GenerateOtpCommand` y `VerifyOtpCommand`. El OTP aleatorio se busca por destino + propósito; las credenciales HOTP/TOTP, por destino + tipo + propósito, y el propósito entra en los datos asociados del cifrado del secreto.
- **El bloqueo de HOTP/TOTP no se reinicia al emitir.** `HmacCodeProtocol.issue` rechaza con `OtpBlockedException` mientras `lockedUntil` no venza, y la persistencia no toca `failedAttempts` ni `lockedUntil` al emitir. Los fallos vuelven a cero solo al acertar o al bloquearse; el bloqueo dura `otp.lock-seconds`, separado de la retención.
- **Dos limitadores, una sola implementación.** `SendRateLimiter` y `VerifyRateLimiter` envuelven un `SlidingWindowLimiter` (ventana deslizante por destino y por IP) con sus propios límites. La IP es `getRemoteAddr()`, que fija Tomcat con `RemoteIpValve` confiando solo en proxies conocidos (ver [IP del cliente](../README.md#ip-del-cliente)).
- **Cada protocolo es una clase (`CodeProtocol`).** `RandomCodeProtocol`, `HotpProtocol` y `TotpProtocol` implementan emitir y verificar; `HmacCodeProtocol` concentra lo que HOTP y TOTP comparten (secreto cifrado, bloqueo por intentos, orden de la verificación). Los casos de uso piden el protocolo a `CodeProtocols` y no preguntan cuál es: agregar uno nuevo es agregar una clase.
- **La configuración que necesita la aplicación entra como `OtpSettings`**, igual que `HmacSettings`, y la arma `OtpConfig` desde `OtpProperties`. Así `application` no importa nada de `adapter`.
- **Los proveedores son enums (`SmsProvider`, `EmailProvider`).** Un valor mal escrito en `SMS_PROVIDER` falla al arrancar con los valores válidos, y `isReal()` reemplaza las comparaciones de texto.
- **La interfaz web es HTML, CSS y JavaScript sin framework**, servida por Spring Boot desde `static/`.
- **El canal y el protocolo son independientes.** El canal (SMS o correo) solo decide cómo llega el código; el protocolo decide cómo se obtiene y cómo se comprueba. OTP guarda el hash del código en `OtpPersistencePort`. HOTP y TOTP guardan un `HmacCredential` (secreto cifrado y contador o ventana) por destino y tipo, y verifican recalculando.
- **El secreto de HOTP y TOTP se cifra, no se hashea.** Hay que poder recuperarlo para recalcular. AES-256-GCM usa como datos asociados `destino|tipo`, así un secreto copiado a otro registro no se puede descifrar.
- **La configuración de HOTP y TOTP entra como `HmacSettings`**, un record de `application` que construye `HmacConfig` a partir de `HmacProperties` (prefijo `hmac`).

---

## Limitaciones y deuda técnica

1. **`domain` usa anotaciones de Jackson** en `Cellphone` y `OtpCode` (`@JsonCreator`, `@JsonValue`) y Lombok en `Otp` (solo en compilación). No hay Spring, MongoDB, Twilio ni HTTP en `domain`, pero no compila como Java puro sin las anotaciones de Jackson.
2. **El almacenamiento en memoria no sobrevive a un reinicio ni se comparte entre instancias.** Ver [BASE_DE_DATOS.md](./BASE_DE_DATOS.md#almacenamiento-en-memoria).
3. **Los errores de formato dan un mensaje genérico.** Un JSON malformado o un valor que no se puede leer (celular o correo inválido, `purpose` o `type` desconocido) responde `400 VALIDATION_ERROR` con el formato `{success, code, message}`, pero el mensaje no dice qué campo falló.
4. **Sin autenticación** en los endpoints; hay límites de envíos y de verificaciones por destino y por IP, pero no la reemplazan. Ver [Limitaciones conocidas](../README.md#limitaciones-conocidas).
