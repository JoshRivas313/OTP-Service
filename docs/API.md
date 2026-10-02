# API

Referencia de los endpoints. La documentación interactiva se genera con springdoc-openapi:

- Swagger UI: `http://localhost:8080/swagger-ui.html` (redirige a `/swagger-ui/index.html`)
- OpenAPI en JSON: `http://localhost:8080/v3/api-docs`

## Contenido

- [Convenciones](#convenciones)
- [OTP local](#otp-local)
- [OTP con Twilio por sesión](#otp-con-twilio-por-sesión)
- [OTP por correo](#otp-por-correo)
- [Estado del servicio](#estado-del-servicio)
- [Códigos de error](#códigos-de-error)
- [Errores de formato](#errores-de-formato)

---

## Convenciones

- Todas las peticiones y respuestas son JSON (`Content-Type: application/json`).
- Las respuestas correctas de OTP llevan `"success": true`. Los errores de negocio y de validación llevan `{"success": false, "code": "...", "message": "..."}`.
- **Celular:** solo números peruanos. Se acepta `9XXXXXXXX` o `+519XXXXXXXX` (9 dígitos que empiezan con 9) y se normaliza a `+519XXXXXXXX`.
- **Código:** entre 4 y 10 dígitos en OTP; entre 6 y 8 en HOTP y TOTP.
- **Protocolo (`type`):** los endpoints de envío y de verificación aceptan `OTP` (por defecto), `HOTP` o `TOTP`. Al verificar se manda el mismo `type` con el que se envió. Con HOTP y TOTP el servidor no guarda el código: lo recalcula.
- **Propósito (`purpose`):** `LOGIN` (por defecto), `REGISTER`, `PASSWORD_RECOVERY` o `PAYMENT_CONFIRMATION`. Forma parte de la identidad del código: se verifica con el mismo `purpose` con el que se pidió, y uno pedido para `LOGIN` responde `404 OTP_NOT_FOUND` si se intenta verificar como `PAYMENT_CONFIRMATION`. Cada destino tiene un código (OTP) o una credencial (HOTP/TOTP) independiente por propósito. Un valor desconocido responde `400 VALIDATION_ERROR`.
- **Respuesta de envío:** además de `success` y `message` trae `type`, `expiresInSeconds` (ausente en HOTP), `counter` (HOTP) o `timeStep` (TOTP). `demoCode` solo aparece con `OTP_DEMO_MODE=true`.
- **Correo:** dirección válida de hasta 254 caracteres. Se normaliza a minúsculas.
- **Límite de envíos:** los endpoints que generan códigos aceptan por defecto 5 envíos por destino y 20 por IP cada 10 minutos; el siguiente responde `429 RATE_LIMIT_EXCEEDED`.
- **Límite de verificaciones:** aparte del anterior, los endpoints `/verify` aceptan por defecto 10 verificaciones por destino y 30 por IP cada 10 minutos, correctas o no; la siguiente responde `429 RATE_LIMIT_EXCEEDED`. Frena la fuerza bruta que reparte intentos entre códigos nuevos o entre destinos.
- **Bloqueo de HOTP y TOTP:** 3 fallos bloquean la credencial de ese destino y propósito durante `OTP_LOCK_SECONDS` (600 s). Mientras dura, enviar y verificar responden `423 OTP_BLOCKED`; pedir otro código no lo levanta.
- **IP del cliente:** la fija Tomcat confiando solo en proxies conocidos; ver [IP del cliente](../README.md#ip-del-cliente).

---

## OTP local

Usan el proveedor de SMS global elegido con `SMS_PROVIDER` (`console` por defecto). No requieren sesión.

### POST /otps — Generar código OTP

Genera un código y lo envía. Generar un código nuevo invalida los anteriores del mismo celular.

| Campo | Obligatorio | Regla |
|---|---|---|
| `cellphone` | sí | Celular peruano |
| `type` | no | `OTP`, `HOTP` o `TOTP`. Por defecto `OTP` |
| `purpose` | no | `LOGIN`, `REGISTER`, `PASSWORD_RECOVERY` o `PAYMENT_CONFIRMATION`. Por defecto `LOGIN` |
| `digits` | no | Entero de 4 a 10 (6 a 8 en HOTP y TOTP). Por defecto 6 |
| `durationSeconds` | no | OTP: vigencia, de 1 a 86400. TOTP: ventana, de 15 a 300. HOTP: se ignora. Por defecto 30 |

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
  "type": "OTP",
  "expiresInSeconds": 60
}
```

Con `OTP_DEMO_MODE=true` el mensaje es `"Código enviado (modo demo, no llega SMS real)"` y `demoCode` trae el código.

---

### POST /otps/verify — Verificar código

Un código correcto solo se acepta una vez.

| Campo | Obligatorio | Regla |
|---|---|---|
| `cellphone` | sí | Celular peruano |
| `type` | no | El protocolo con el que se envió. Por defecto `OTP` |
| `purpose` | no | El propósito con el que se pidió. Por defecto `LOGIN` |
| `code` | sí | De 4 a 10 dígitos |

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

**Response `401`** (código incorrecto, indica el intento)

```json
{
  "success": false,
  "code": "OTP_INVALID",
  "message": "El código es incorrecto (intento 1 de 3)"
}
```

Otros errores posibles: `OTP_NOT_FOUND`, `OTP_INVALIDATED`, `OTP_ALREADY_USED`, `OTP_EXPIRED` y `OTP_BLOCKED`. El intento fallido que alcanza el máximo (3 por defecto) ya responde `423 OTP_BLOCKED`.

---

## OTP con Twilio por sesión

Estos endpoints envían el SMS con **la cuenta de Twilio que el propio usuario conectó**, no con las credenciales del servidor. Las credenciales viven en la sesión HTTP (cookie `JSESSIONID`) durante 15 minutos de inactividad y no se guardan en la base de datos.

### POST /api/twilio/connect — Conectar cuenta Twilio

Valida las credenciales contra Twilio y las guarda en la sesión. Además consulta a Twilio el tipo de cuenta y sus números verificados (`Account` y `OutgoingCallerId`) para decidir a qué números se podrá enviar (ver [POST /api/twilio/otps](#post-apitwiliootps--generar-código-otp)).

| Campo | Regla |
|---|---|
| `accountSid` | `AC` seguido de 32 caracteres alfanuméricos |
| `authToken` | 32 caracteres alfanuméricos |
| `verifyServiceSid` | `VA` seguido de 32 caracteres alfanuméricos |
| `phoneNumber` | Número de Twilio en formato E.164, por ejemplo `+15017122661` |

**Request**

```json
{
  "accountSid": "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "authToken": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "verifyServiceSid": "VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "phoneNumber": "+15017122661"
}
```

**Response `200`** (las credenciales y los números se devuelven enmascarados)

```json
{
  "connected": true,
  "maskedCredentials": "AC1234···abcd / verify VA5678···efgh",
  "restrictedToVerifiedNumbers": true,
  "verifiedNumbers": ["*********321"]
}
```

`restrictedToVerifiedNumbers` es `true` cuando la cuenta es de prueba o tiene números verificados: en ese caso solo se puede enviar a los de `verifiedNumbers`. Si Twilio no responde la consulta de números, queda en `false` y es Twilio quien decide al enviar.

**Response `401`** (formato inválido, o Twilio rechazó las credenciales o el Verify Service)

```json
{
  "success": false,
  "code": "TWILIO_CREDENTIALS_INVALID",
  "message": "Twilio rechazó esas credenciales o el Verify Service indicado"
}
```

Si falta algún campo, la respuesta es `400 VALIDATION_ERROR`.

---

### GET /api/twilio/status — Estado de la conexión

Indica si la sesión tiene credenciales conectadas y a qué números puede enviar.

**Response `200`**

```json
{
  "connected": false,
  "maskedCredentials": null,
  "restrictedToVerifiedNumbers": false,
  "verifiedNumbers": []
}
```

---

### POST /api/twilio/disconnect — Desconectar cuenta Twilio

Borra las credenciales de la sesión.

**Response `200`**

```json
{
  "connected": false,
  "maskedCredentials": null,
  "restrictedToVerifiedNumbers": false,
  "verifiedNumbers": []
}
```

---

### POST /api/twilio/otps — Generar código OTP

Igual que `POST /otps` (mismos campos), pero envía el SMS con las credenciales de la sesión y admite un campo más. Si `OTP_DEMO_MODE` está activo, la respuesta también incluye `demoCode`.

| Campo | Obligatorio | Regla |
|---|---|---|
| `message` | no | Texto del SMS. Máximo 300 caracteres y debe contener `{code}`, que se reemplaza por el código. `{seconds}` se reemplaza por los segundos de vigencia. Si se omite, se envía el mensaje predeterminado: "Tu código de verificación es 123456. Vence en 60 segundos." |

La interfaz web siempre envía `message`: el texto del propósito elegido (por ejemplo "Tu código para iniciar sesión es {code}. Vence en {seconds} segundos.") o el que escriba el usuario.

**Destino.** Si la cuenta conectada es de prueba, o tiene números verificados en Twilio, solo se puede enviar a esos números: cualquier otro responde `403 DESTINATION_NOT_VERIFIED` sin llamar a Twilio. Una cuenta de pago sin números verificados puede enviar a cualquier celular peruano.

`message` solo existe en este endpoint: `POST /otps` envía siempre el mensaje predeterminado, para que la API sin autenticación no sirva para enviar texto arbitrario con la cuenta del servidor.

**Request**

```json
{
  "cellphone": "912345678",
  "digits": 6,
  "durationSeconds": 60,
  "message": "Tu clave de acceso es {code}. Caduca en {seconds} segundos."
}
```

**Response `201`**

```json
{
  "success": true,
  "message": "Código enviado correctamente",
  "type": "OTP",
  "expiresInSeconds": 60
}
```

**Response `400`** (no hay una cuenta conectada en la sesión)

```json
{
  "success": false,
  "code": "TWILIO_NOT_CONNECTED",
  "message": "Primero conecta tu cuenta de Twilio"
}
```

---

### POST /api/twilio/otps/verify — Verificar código

Igual que `POST /otps/verify`, pero exige sesión conectada (`400 TWILIO_NOT_CONNECTED` si no la hay). La verificación no la hace Twilio: la hace el propio servicio contra su almacén de códigos (memoria o MongoDB). En este endpoint, un `code` con formato inválido responde `401 OTP_INVALID`.

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

---

### Ejemplo con curl

La sesión se mantiene con la cookie: `-c` la guarda y `-b` la envía.

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/twilio/connect \
  -H "Content-Type: application/json" \
  -d '{"accountSid":"ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","authToken":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","verifyServiceSid":"VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx","phoneNumber":"+15017122661"}'

curl -b cookies.txt -X POST http://localhost:8080/api/twilio/otps \
  -H "Content-Type: application/json" \
  -d '{"cellphone":"912345678","digits":6,"durationSeconds":60}'
```

---

## OTP por correo

Usan el proveedor de correo elegido con `EMAIL_PROVIDER` (`console` por defecto). No requieren sesión. El destino es un correo en lugar de un celular; el resto de reglas (dígitos, duración, intentos, expiración) son las mismas.

### POST /api/email/otps — Generar código OTP

| Campo | Obligatorio | Regla |
|---|---|---|
| `email` | sí | Correo electrónico válido |
| `type` | no | `OTP`, `HOTP` o `TOTP`. Por defecto `OTP` |
| `purpose` | no | `LOGIN`, `REGISTER`, `PASSWORD_RECOVERY` o `PAYMENT_CONFIRMATION`. Por defecto `LOGIN` |
| `digits` | no | De 4 a 10 (6 a 8 en HOTP y TOTP). Por defecto 6 |
| `durationSeconds` | no | OTP: vigencia, de 1 a 86400. TOTP: ventana, de 15 a 300. HOTP: se ignora. Por defecto 30 |
| `message` | no | Texto del correo. Máximo 300 caracteres y debe contener `{code}`. `{seconds}` se reemplaza por los segundos de vigencia |

**Request**

```json
{
  "email": "visitante@correo.com",
  "digits": 6,
  "durationSeconds": 60,
  "message": "Tu código para iniciar sesión es {code}. Vence en {seconds} segundos."
}
```

**Response `201`**

```json
{
  "success": true,
  "message": "Código enviado correctamente",
  "type": "OTP",
  "expiresInSeconds": 60
}
```

**Response `429`** (demasiados envíos al mismo correo o desde la misma IP)

```json
{
  "success": false,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Demasiados envíos seguidos. Prueba de nuevo en unos minutos"
}
```

**Response `502`** (el proveedor de correo rechazó el envío)

```json
{
  "success": false,
  "code": "EMAIL_DELIVERY_FAILED",
  "message": "No se pudo enviar el correo"
}
```

---

### POST /api/email/otps/verify — Verificar código

Igual que `POST /otps/verify`, pero con `email` en lugar de `cellphone`. Un `code` con formato inválido responde `401 OTP_INVALID`.

**Request**

```json
{
  "email": "visitante@correo.com",
  "code": "482913"
}
```

**Response `200`**

```json
{
  "success": true,
  "message": "Código verificado correctamente"
}
```

---

## Estado del servicio

### GET /health — Estado

Responde `200` mientras la aplicación esté levantada. Sirve como Health Check Path en plataformas como Render. Siempre está disponible, también con `OTP_LOCAL_API_ENABLED=false`.

**Response** `200`

```json
{
  "status": "UP",
  "storage": "memory"
}
```

`storage` es `memory` o `mongo`. En memoria, un reinicio borra los códigos pendientes y los secretos de HOTP y TOTP.

### GET /api/otp-policy — Reglas de verificación

Devuelve los valores que aplica la verificación. La interfaz los usa para explicar la tolerancia de TOTP y los límites con los mismos números que el servidor.

**Response** `200`

```json
{
  "totpToleranceSteps": 1,
  "hotpLookAhead": 10,
  "maxAttempts": 3,
  "lockSeconds": 600
}
```

- `totpToleranceSteps`: ventanas vecinas que se aceptan. Con 1, un TOTP vale entre 30 y 60 s según cuándo se generó.
- `hotpLookAhead`: cuántos HOTP emitidos sin usar se aceptan (los más recientes).

---

## Códigos de error

| Código | HTTP | Cuándo |
|---|---|---|
| `OTP_NOT_FOUND` | 404 | No hay ningún código para ese destino, protocolo y propósito |
| `OTP_INVALIDATED` | 409 | Se generó uno más reciente para el mismo destino y propósito. En HOTP: el código quedó detrás de 10 más nuevos sin usar (no gasta intento) |
| `OTP_ALREADY_USED` | 409 | El código ya se verificó antes |
| `OTP_EXPIRED` | 410 | Pasó la duración del código |
| `OTP_BLOCKED` | 423 | Se alcanzó el máximo de intentos fallidos. En HOTP y TOTP dura `OTP_LOCK_SECONDS` y también rechaza pedir otro código |
| `OTP_INVALID` | 401 | El código es incorrecto |
| `OTP_INVALID_REQUEST` | 400 | HOTP o TOTP con menos de 6 o más de 8 dígitos, o TOTP con una ventana fuera de 15 a 300 s |
| `SMS_DELIVERY_FAILED` | 502 | El proveedor de SMS rechazó o no pudo enviar el mensaje. Con Twilio, `message` explica la causa cuando se conoce (número no verificado en una cuenta de prueba, país sin permiso, remitente sin SMS) y el log del servidor registra el código de error de Twilio |
| `EMAIL_DELIVERY_FAILED` | 502 | El proveedor de correo rechazó o no pudo enviar el mensaje. El log del servidor registra el motivo que devolvió el proveedor |
| `RATE_LIMIT_EXCEEDED` | 429 | Se superó el límite de envíos o el de verificaciones para ese destino o esa IP |
| `TWILIO_CREDENTIALS_INVALID` | 401 | Credenciales de Twilio con formato inválido o rechazadas por Twilio |
| `TWILIO_NOT_CONNECTED` | 400 | El flujo de Twilio se usó sin conectar una cuenta en la sesión |
| `DESTINATION_NOT_VERIFIED` | 403 | La cuenta de Twilio conectada solo puede enviar a sus números verificados y el destino no es uno de ellos |
| `VALIDATION_ERROR` | 400 | Un campo obligatorio falta o está fuera de rango, o un valor no existe en su catálogo (por ejemplo un `purpose` desconocido) |

Mensajes de `VALIDATION_ERROR` (formato `campo: mensaje`):

| Campo | Mensaje |
|---|---|
| `cellphone` | `El celular es obligatorio` |
| `digits` | `Mínimo 4 dígitos` / `Máximo 10 dígitos` |
| `durationSeconds` | `La duración debe ser mayor a 0 segundos` / `La duración no puede superar un día` |
| `code` | `El código es obligatorio` |

**Response `400`** (ejemplo de `VALIDATION_ERROR`)

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "digits: Mínimo 4 dígitos"
}
```

---

## Errores de formato

Si un campo no se puede leer (celular o correo inválido, `code` con formato inválido, `type` o `purpose` desconocido) o el JSON está mal formado, la API responde `400 VALIDATION_ERROR` con el formato habitual y un mensaje genérico:

**Response `400`**

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Solicitud inválida: revisa los valores enviados"
}
```

Casos comprobados con `POST /otps`, `POST /otps/verify` y `POST /api/twilio/otps`:

| Entrada | Resultado |
|---|---|
| Celular de 8 dígitos | `400 VALIDATION_ERROR` |
| 9 dígitos que no empiezan con 9 | `400 VALIDATION_ERROR` |
| Celular de otro país (`+15551234567`) | `400 VALIDATION_ERROR` |
| `type` desconocido | `400 VALIDATION_ERROR` |
| `purpose` desconocido | `400 VALIDATION_ERROR` |
| `code` de 3 dígitos en `/otps/verify` | `400 VALIDATION_ERROR` |
| `message` sin `{code}` en `/api/twilio/otps` | `400 VALIDATION_ERROR` con `message: El mensaje debe incluir {code}` |
| JSON mal formado | `400 VALIDATION_ERROR` |
