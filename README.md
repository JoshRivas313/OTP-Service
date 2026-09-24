# OTP Service

Servicio de autenticación por código de un solo uso (OTP), hecho con Spring Boot y MongoDB. El código nunca se guarda en texto plano: se hashea con HMAC-SHA256 antes de persistirse, y cada intento fallido queda registrado para bloquear el código después de N intentos.

Incluye una UI mínima servida por el propio Spring Boot (`src/main/resources/static`), así que es un solo deployable, sin CORS ni un frontend aparte.

## Cómo correrlo

Necesita Java 21, Maven (o el wrapper `mvnw` que ya viene) y una instancia de MongoDB corriendo en `localhost:27017` (o la que le pases por `MONGODB_URI`).

```bash
cp .env.example .env
# completar OTP_HASH_SECRET al menos
./mvnw spring-boot:run
```

La app levanta en `http://localhost:8080` con la UI de prueba en la raíz.

## Diseño

- **Value Objects con comportamiento, no anémicos**: `Cellphone` normaliza a E.164 y sabe enmascararse, `OtpCode` genera el código con `SecureRandom` y valida su propio formato, `ValidityWindow` sabe si expiró (recibe el instante como parámetro, no llama a `Instant.now()` internamente — así es testeable), `VerificationStatus` cuenta intentos y sabe si está bloqueado.
- **Operaciones atómicas contra Mongo**: invalidar el código anterior, reclamar un código como usado, y registrar un intento fallido son todas `findAndModify` atómicos — no hay una lectura seguida de una escritura separada que pueda pisarse con otra request.
- **`ErrorCode` como catálogo único**: cada error de negocio tiene su estado HTTP y mensaje por defecto en un solo enum, en vez de strings sueltos repartidos por los servicios.
- **TTL index en Mongo** (`purgeAt`) para que los OTP viejos se autoeliminen, no se acumulan para siempre.

## Modos de ejecución

El proveedor de SMS se elige con `SMS_PROVIDER`:

| Valor | Qué hace |
|---|---|
| `console` (default) | No manda nada real, el código queda en la terminal del servidor |
| `twilio` | Manda el SMS con la cuenta de Twilio del dueño del proyecto (`TWILIO_*`) |
| `infobip` | Igual pero con Infobip (`INFOBIP_*`) |

### Modo demo (`OTP_DEMO_MODE=true`)

Pensado para un deploy público: el código generado se devuelve en la respuesta de `POST /otps` y se muestra en la UI, para que alguien pueda probar el flujo completo sin acceso al log del servidor. La app **no arranca** si esto se combina con `SMS_PROVIDER=twilio` o `infobip` — no tiene sentido mostrar un código que además se mandó de verdad, y es una forma barata de evitar quemar cuota de SMS sin querer.

### Conectar tu propia cuenta de Twilio (`TWILIO_CONNECT_ENABLED=true`)

Para que alguien pruebe con un SMS real sin gastar la cuota del dueño del proyecto ni usar sus credenciales. Con el flag prendido aparece una tarjeta extra en la UI donde cualquiera puede conectar su propia cuenta de Twilio Verify:

- La credencial vive **solo en la `HttpSession`** de ese visitante — nunca se escribe en Mongo ni en un log, y desaparece cuando la sesión expira o el visitante desconecta.
- Antes de guardar nada, el servidor llama de verdad a Twilio (fetch del Verify Service) para confirmar que la credencial es válida, no solo que tiene el formato correcto.
- Cada request arma su propio `TwilioRestClient` a partir de la credencial de la sesión — a diferencia del modo `twilio` de arriba, acá **no** se usa un `Twilio.init()` global, porque con varias sesiones conectadas a la vez eso las haría pisarse entre sí.
- Este modo no toca Mongo para nada: el estado del código (expiración, intentos) lo maneja Twilio Verify del otro lado.

Este flag queda **apagado por defecto** a propósito. La razón no es de confidencialidad de la credencial (eso ya está resuelto arriba) sino de superficie de exposición: dejar un endpoint público que acepta y usa credenciales de terceros, abierto sin supervisión, es un riesgo distinto al de simplemente guardarlas mal. Se prende solo para mostrar la demo en vivo.

#### Cómo conseguir credenciales de Twilio para probarlo

1. Crear una cuenta gratis en [twilio.com](https://www.twilio.com/try-twilio) (la cuenta trial ya trae crédito).
2. En el dashboard principal de la consola están el **Account SID** y el **Auth Token**.
3. Ir a **Verify → Services**, crear un servicio nuevo, y copiar su SID (empieza con `VA`).
4. Con una cuenta trial, Twilio solo manda SMS a números ya verificados en esa cuenta — el más simple para probar es el celular con el que te registraste, que ya queda verificado.

## Variables de entorno

Ver [`.env.example`](.env.example) para la lista completa con su valor por defecto.

## Tests

```bash
./mvnw test
```
