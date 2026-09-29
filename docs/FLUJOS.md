# Flujos

Diagramas de cómo se genera, se verifica y se envía un código.

## Contenido

- [Generar un código](#generar-un-código)
- [Verificar un código](#verificar-un-código)
- [Ciclo de vida de un código](#ciclo-de-vida-de-un-código)
- [Twilio por sesión (interfaz web)](#twilio-por-sesión-interfaz-web)
- [Qué proveedor envía cada flujo](#qué-proveedor-envía-cada-flujo)

---

## Generar un código

`POST /otps` y `POST /api/twilio/otps` usan el mismo caso de uso; solo cambia el `SmsSender` que recibe.

```mermaid
sequenceDiagram
    participant C as Cliente
    participant H as HttpAdapter
    participant U as GenerateOtpUseCase
    participant M as MongoDB
    participant S as SmsSender

    C->>H: POST /otps con cellphone, digits y durationSeconds
    H->>U: generate(command)
    U->>M: Invalida los códigos activos del celular
    U->>U: Genera el código con SecureRandom
    U->>U: Calcula el hash HMAC-SHA256
    U->>M: Guarda el hash, la ventana de validez y purgeAt
    U->>S: send(celular, mensaje con el código)
    U-->>H: GenerateOtpResult
    H-->>C: 201 con success, message y demoCode
```

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

La purga ocurre solo si el índice TTL de `purgeAt` existe. Hoy no se crea automáticamente (ver [BASE_DE_DATOS.md](./BASE_DE_DATOS.md#índices)).

---

## Twilio por sesión (interfaz web)

Flujo completo de la interfaz web: conectar una cuenta propia de Twilio, enviar, verificar y desconectar.

```mermaid
sequenceDiagram
    actor U as Usuario
    participant W as Interfaz web
    participant B as Backend
    participant T as Twilio
    participant M as MongoDB

    U->>W: Escribe sus credenciales de Twilio
    W->>B: POST /api/twilio/connect
    B->>T: Consulta el Verify Service con esas credenciales
    alt Credenciales válidas
        T-->>B: Servicio encontrado
        B->>B: Guarda las credenciales en la sesión por 15 minutos
        B-->>W: 200 connected
        W->>U: Abre otp-service.html
    else Credenciales inválidas
        T-->>B: Rechazo
        B-->>W: 401 TWILIO_CREDENTIALS_INVALID
    end

    U->>W: Escribe su celular y pulsa Enviar código
    W->>B: POST /api/twilio/otps
    B->>M: Guarda el hash del código y su expiración
    B->>T: Envía el SMS con las credenciales de la sesión
    T-->>U: SMS al celular
    B-->>W: 201

    U->>W: Escribe el código recibido
    W->>B: POST /api/twilio/otps/verify
    B->>M: Reclama el código de forma atómica
    B-->>W: 200 verificado

    U->>W: Pulsa Cambiar configuración
    W->>B: POST /api/twilio/disconnect
    B->>B: Borra las credenciales de la sesión
```

Twilio interviene solo dos veces: al validar las credenciales y al entregar el SMS. La verificación del código la hace el backend contra MongoDB.

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
