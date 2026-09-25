# OTP Service

Servicio de autenticación por código de un solo uso (OTP), hecho con Spring Boot, MongoDB y Twilio. El código nunca se guarda en texto plano: se hashea con HMAC-SHA256 antes de persistirse, y cada intento fallido queda registrado para bloquear el código después de N intentos.

Incluye una UI mínima servida por el propio Spring Boot (`src/main/resources/static`), así que es un solo deployable, sin CORS ni un frontend aparte.

## Cómo probarlo

Necesita Java 21, Maven (o el wrapper `mvnw` que ya viene), una instancia de MongoDB corriendo en `localhost:27017` (o la que le pases por `MONGODB_URI`), y una cuenta de Twilio (la trial gratuita alcanza).

```bash
cp .env.example .env
# completar OTP_HASH_SECRET al menos
./mvnw spring-boot:run
```

1. Abrí `http://localhost:8080` — es la pantalla de conectar Twilio, no la demo directamente.
2. Ingresá tu **Account SID**, **Auth Token**, **Verify Service SID** y **Número de Twilio** (el que usa para mandar el SMS).
3. Al conectar, la app te lleva a `/otp-service.html`.
4. Escribí tu celular (el mismo con el que te registraste en Twilio, si tu cuenta es trial), elegí cantidad de dígitos y tiempo de expiración, y mandate un código.
5. Verificá el código que te llegó por SMS real.

Las credenciales viven **solo en tu sesión del backend** — nunca se persisten en Mongo, en un archivo ni en el navegador. Si reiniciás la app o cerrás la sesión, hay que volver a conectarlas.

## Diseño

- **Value Objects con comportamiento, no anémicos**: `Cellphone` normaliza a E.164 y sabe enmascararse, `OtpCode` genera el código con `SecureRandom` y valida su propio formato, `ValidityWindow` sabe si expiró (recibe el instante como parámetro, no llama a `Instant.now()` internamente — así es testeable), `VerificationStatus` cuenta intentos y sabe si está bloqueado.
- **Operaciones atómicas contra Mongo**: invalidar el código anterior, reclamar un código como usado, y registrar un intento fallido son todas `findAndModify` atómicos — no hay una lectura seguida de una escritura separada que pueda pisarse con otra request.
- **`ErrorCode` como catálogo único**: cada error de negocio tiene su estado HTTP y mensaje por defecto en un solo enum, en vez de strings sueltos repartidos por los servicios.
- **TTL index en Mongo** (`purgeAt`) para que los OTP viejos se autoeliminen, no se acumulan para siempre.

## Modos de ejecución

Estos modos son del motor local (`POST /otps`, `POST /otps/verify`) usando el `SmsSender` global del servidor. La UI en cambio siempre usa la cuenta de Twilio conectada en sesión (ver más abajo), pero estos endpoints siguen ahí y se pueden probar directo con `curl`/Postman. El proveedor de SMS global se elige con `SMS_PROVIDER`:

| Valor | Qué hace |
|---|---|
| `console` (default) | No manda nada real, el código queda en la terminal del servidor |
| `twilio` | Manda el SMS con la cuenta de Twilio del dueño del proyecto (`TWILIO_*`) |
| `infobip` | Igual pero con Infobip (`INFOBIP_*`) |

### Modo demo (`OTP_DEMO_MODE=true`)

Pensado para un deploy público: el código generado se devuelve en la respuesta de `POST /otps` y se muestra en la UI, para que alguien pueda probar el flujo completo sin acceso al log del servidor. La app **no arranca** si esto se combina con `SMS_PROVIDER=twilio` o `infobip` — no tiene sentido mostrar un código que además se mandó de verdad, y es una forma barata de evitar quemar cuota de SMS sin querer.

### Conectar tu propia cuenta de Twilio

`index.html` (la raíz del sitio) es la pantalla para conectar una cuenta de Twilio — no hay un flag ni una variable de entorno que la habilite o la esconda, es simplemente la primera pantalla del proyecto. `/otp-service.html` (donde se genera y verifica el código) está protegida: si no hay una sesión Twilio conectada, un `Filter` (`TwilioOnboardingFilter`) redirige de vuelta a `/` antes de mostrar nada.

- La credencial vive **solo en la `HttpSession`** de ese visitante — nunca se escribe en Mongo ni en un log, y desaparece cuando la sesión expira (15 minutos de inactividad) o el visitante toca "Cambiar configuración".
- Antes de guardar nada, el servidor llama de verdad a Twilio (fetch del Verify Service) para confirmar que la credencial es válida, no solo que tiene el formato correcto.
- El código se genera y guarda igual que en `/otps` (Mongo + HMAC): `digits` y la expiración sí son configurables por request desde la UI. La única diferencia con el motor local es por dónde sale el SMS.
- El SMS sale con la cuenta de Twilio de la sesión, no con la del servidor: se arma un `TwilioRestClient` a partir de la credencial guardada (nunca un `Twilio.init()` global, porque con varias sesiones conectadas a la vez eso las haría pisarse entre sí), y se manda como mensaje simple (`Message.creator`) usando el **Número de Twilio** que el visitante conectó.

#### Cómo conseguir credenciales de Twilio para probarlo

1. Crear una cuenta gratis en [twilio.com](https://www.twilio.com/try-twilio) (la cuenta trial ya trae crédito y un número de teléfono).
2. En el dashboard principal de la consola están el **Account SID** y el **Auth Token**.
3. Ir a **Verify → Services**, crear un servicio nuevo, y copiar su SID (empieza con `VA`).
4. Ir a **Phone Numbers → Manage → Active Numbers** y copiar el número asignado, en formato E.164 (ej. `+15017122661`).
5. Con una cuenta trial, Twilio solo manda SMS a números ya verificados en esa cuenta — el más simple para probar es el celular con el que te registraste, que ya queda verificado.

## Documentación de la API

Con la app corriendo, la documentación interactiva (Swagger UI) está en `http://localhost:8080/swagger-ui.html`, y el JSON de OpenAPI en `http://localhost:8080/v3/api-docs`.

## Variables de entorno

Ver [`.env.example`](.env.example) para la lista completa con su valor por defecto.

## Tests

```bash
./mvnw test
```
