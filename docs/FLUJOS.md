# Flujos

Diagramas complementarios a los dos flujos de uso del README: [Flujo 1: Generación Local](../README.md#flujo-1-generación-local-motor-interno) y [Flujo 2: Twilio por Sesión](../README.md#flujo-2-twilio-por-sesión).

## Contenido

- [Generar un código, paso a paso](#generar-un-código-paso-a-paso)
- [Verificar un código](#verificar-un-código)
- [Ciclo de vida de un código](#ciclo-de-vida-de-un-código)
- [Qué proveedor envía cada flujo](#qué-proveedor-envía-cada-flujo)
- [Verificar un código HOTP o TOTP](#verificar-un-código-hotp-o-totp)

---

## Generar un código, paso a paso

`POST /otps` y `POST /api/twilio/otps` usan el mismo caso de uso (`GenerateOtpUseCase`); solo cambia el `SmsSender` que recibe.

1. Invalida los códigos anteriores del mismo celular que no estén usados ni invalidados.
2. Genera el código (`OtpCode.generate`, `SecureRandom`).
3. Calcula el hash con HMAC-SHA256 y la clave `OTP_HASH_SECRET`.
4. Guarda el `Otp` con su ventana de validez y `purgeAt = expiresAt + retención`.
5. Envía el mensaje con el `SmsSender` correspondiente.
6. Devuelve `demoCode` solo si `OTP_DEMO_MODE` está activo.

---

## Verificar un código

`POST /otps/verify` y `POST /api/twilio/otps/verify`.

```mermaid
flowchart TD
    A["POST /otps/verify"] --> B{"¿Existe un código para el celular?"}
    B -->|No| E1["404 OTP_NOT_FOUND"]
    B -->|Sí| C{"¿Fue invalidado por uno más nuevo?"}
    C -->|Sí| E2["409 OTP_INVALIDATED"]
    C -->|No| D{"¿Ya se usó?"}
    D -->|Sí| E3["409 OTP_ALREADY_USED"]
    D -->|No| F{"¿Expiró?"}
    F -->|Sí| E4["410 OTP_EXPIRED"]
    F -->|No| G{"¿Alcanzó el máximo de intentos?"}
    G -->|Sí| E5["423 OTP_BLOCKED"]
    G -->|No| H{"¿El hash coincide? claimIfMatches, una sola operación atómica"}
    H -->|Sí| OK["200 Verificado, el código queda marcado como usado"]
    H -->|No| I["registerFailedAttempt suma un intento"]
    I --> J{"¿Llegó al máximo?"}
    J -->|Sí| E6["423 OTP_BLOCKED"]
    J -->|No| E7["401 OTP_INVALID con el número de intento"]
```

Las comprobaciones se hacen sobre el código más reciente del celular. El intento fallido que alcanza el máximo (3 por defecto) ya responde `423`.

---

## Ciclo de vida de un código

No hay un campo de estado: el estado se deduce de los datos (ver [BASE_DE_DATOS.md](./BASE_DE_DATOS.md#ciclo-de-vida-de-un-código)).

```mermaid
stateDiagram-v2
    [*] --> Activo: se genera el código
    Activo --> Usado: verificación correcta
    Activo --> Invalidado: se genera un código nuevo para el celular
    Activo --> Bloqueado: los intentos fallidos llegan al máximo
    Activo --> Expirado: pasa la hora de expiración
    Usado --> Purgado: llega purgeAt
    Invalidado --> Purgado: llega purgeAt
    Bloqueado --> Purgado: llega purgeAt
    Expirado --> Purgado: llega purgeAt
    Purgado --> [*]
```

La purga la hace la propia aplicación al guardar códigos nuevos (modo memoria) o el índice TTL de `purgeAt` (perfil `mongo`). Ver [BASE_DE_DATOS.md](./BASE_DE_DATOS.md#índices).

---

## Qué proveedor envía cada flujo

```mermaid
flowchart LR
    A["POST /otps"] --> B["SmsSender global, elegido con SMS_PROVIDER"]
    B --> C["console: escribe el mensaje en el log"]
    B --> D["twilio: cuenta de Twilio del servidor"]
    B --> E["infobip: cuenta de Infobip del servidor"]
    F["POST /api/twilio/otps"] --> G["SmsSender de la sesión, con las credenciales del usuario"]
    G --> H["Twilio: cuenta de Twilio del usuario"]
```

Más detalle en [PROVEEDORES_SMS.md](./PROVEEDORES_SMS.md).

---

## Verificar un código HOTP o TOTP

Con HOTP y TOTP no hay hash guardado: el servidor recalcula el código con el secreto del destino y lo compara.

```mermaid
flowchart TD
    A["POST .../verify con type HOTP o TOTP"] --> R{"¿Límite de verificaciones por destino o IP?"}
    R -- superado --> X0["429 RATE_LIMIT_EXCEEDED"]
    R -- ok --> B{"¿Hay secreto para destino, tipo y propósito?"}
    B -- no --> X1["404 OTP_NOT_FOUND"]
    B -- sí --> D{"¿Bloqueado?"}
    D -- sí --> X3["423 OTP_BLOCKED"]
    D -- no --> E["Descifrar el secreto (AES-256-GCM)"]
    E --> F{"Tipo"}
    F -- TOTP --> G["Recalcular para las ventanas T-1, T y T+1"]
    F -- HOTP --> H["Recalcular los últimos 10 contadores enviados y sin usar"]
    G --> I{"¿Coincide?"}
    H --> I
    I -- "sí, con una ventana o contador ya usado" --> X4["409 OTP_ALREADY_USED"]
    I -- "TOTP de una ventana ya cerrada" --> X6["410 OTP_EXPIRED"]
    I -- "HOTP emitido pero detrás de 10 más nuevos" --> X7["409 OTP_INVALIDATED"]
    I -- no --> J["Sumar un fallo"]
    J --> K{"¿3 fallos seguidos?"}
    K -- sí --> X3
    K -- no --> X5["401 OTP_INVALID (intento n de 3)"]
    I -- sí --> L["Claim atómico: guardar la ventana o el contador siguiente"]
    L -- "otra petición llegó antes" --> X4
    L -- ok --> OK["200 Código verificado"]
```

El bloqueo dura `OTP_LOCK_SECONDS` (600 s por defecto) y solo afecta a ese destino, tipo y propósito. Pedir un código nuevo **no** lo levanta: mientras dura, enviar también responde `423 OTP_BLOCKED`.
