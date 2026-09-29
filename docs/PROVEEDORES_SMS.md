# Proveedores de SMS

## Contenido

- [Qué hace el backend y qué hace el proveedor](#qué-hace-el-backend-y-qué-hace-el-proveedor)
- [Proveedores disponibles](#proveedores-disponibles)
- [Twilio por sesión (interfaz web)](#twilio-por-sesión-interfaz-web)
- [Validaciones al arrancar](#validaciones-al-arrancar)
- [Restricciones a tener en cuenta](#restricciones-a-tener-en-cuenta)
- [Agregar un proveedor](#agregar-un-proveedor)

---

## Qué hace el backend y qué hace el proveedor

El servicio genera y verifica los códigos por su cuenta. El proveedor de SMS solo entrega el mensaje.

| Lo hace el backend (Spring Boot) | Lo hace el proveedor (Twilio o Infobip) |
|---|---|
| Generar el código con `SecureRandom` | Entregar el SMS al celular |
| Guardar solo su hash (HMAC-SHA256) y su expiración | Cobrar el envío a la cuenta conectada |
| Invalidar códigos anteriores del celular | |
| Comprobar el código, contar intentos y bloquear | |
| Marcar el código como usado (una sola vez) | |
| Validar el formato del celular | |

El proveedor nunca conoce si el código es correcto: no se usa la API de verificación de Twilio (Twilio Verify) para comprobarlo. Twilio interviene en dos momentos: envía el SMS (API de mensajes, `Message.creator`) y, al conectar una cuenta desde la interfaz web, se le consulta el Verify Service para comprobar que las credenciales sirven.

---

## Proveedores disponibles

El proveedor global lo elige `SMS_PROVIDER` y lo usan los endpoints `/otps`. Cada uno implementa el puerto `SmsSender`.

| `SMS_PROVIDER` | Clase | Qué hace | Variables obligatorias |
|---|---|---|---|
| `console` (por defecto) | `ConsoleSmsSender` | No envía nada: escribe el mensaje en el log de la aplicación | Ninguna |
| `twilio` | `TwilioSmsSender` | Envía el SMS con la cuenta de Twilio del servidor | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NUMBER` |
| `infobip` | `InfobipSmsSender` | Envía el SMS con `POST {INFOBIP_BASE_URL}/sms/3/messages` y `Authorization: App <api key>` | `INFOBIP_BASE_URL`, `INFOBIP_API_KEY`, `INFOBIP_SENDER` |

**Modo consola.** Es el modo por defecto y el que permite probar sin ninguna cuenta. El código aparece en el log, con el celular enmascarado:

```
[DEV][SMS] para=*********678 mensaje="Tu código de verificación es 482913. Vence en 60 segundos."
```

Con Docker Compose: `docker compose logs -f app`.

**Modo demo.** Con `OTP_DEMO_MODE=true`, `POST /otps` además devuelve el código en el campo `demoCode`. Sirve para probar sin mirar el log, y no es un segundo factor real.

---

## Twilio por sesión (interfaz web)

La interfaz web no usa el proveedor global. Pide al usuario sus propias credenciales de Twilio y envía con ellas:

1. `POST /api/twilio/connect` valida el formato de las credenciales y le pregunta a Twilio si el Verify Service existe con esas credenciales (`TwilioVerifyService`). Después consulta el tipo de cuenta y sus números verificados (`TwilioAccountInfo`).
2. Si son válidas, `TwilioSessionService` las guarda en la `HttpSession` durante 15 minutos de inactividad. Se mantienen solo en la memoria del servidor: no se escriben en ninguna base de datos ni en el log, y se pierden si el servidor se reinicia.
3. `POST /api/twilio/otps` comprueba que el destino sea uno de los números verificados de la cuenta (si es de prueba o tiene números verificados; si no, `403 DESTINATION_NOT_VERIFIED`), toma las credenciales de la sesión, arma un `SmsSender` (`TwilioSessionSmsSender`) y se lo pasa al caso de uso de generar código.
4. `POST /api/twilio/disconnect`, o el vencimiento de la sesión, las descartan.

`TwilioOnboardingFilter` redirige a `/` cuando se pide `otp-service.html` sin credenciales en la sesión. Solo protege esa página: los endpoints se protegen por su cuenta (responden `400 TWILIO_NOT_CONNECTED`).

Cada SMS de este flujo lo paga la cuenta de Twilio conectada.

---

## Validaciones al arrancar

La aplicación no arranca si la configuración es incoherente:

- `SMS_PROVIDER=twilio` sin `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` o `TWILIO_PHONE_NUMBER`.
- `SMS_PROVIDER=infobip` sin `INFOBIP_BASE_URL`, `INFOBIP_API_KEY` o `INFOBIP_SENDER`.
- `OTP_DEMO_MODE=true` junto con `SMS_PROVIDER=twilio` o `infobip` (`DemoModeGuard`): el código saldría por dos canales, uno de ellos gratis para cualquiera que llame al endpoint.

`DemoModeGuard` solo mira `SMS_PROVIDER`. Con `OTP_DEMO_MODE=true` y una sesión de Twilio conectada desde la interfaz web, el SMS sí se envía y la respuesta también trae `demoCode`.

---

## Restricciones a tener en cuenta

- **Solo celulares peruanos** (`+51 9XXXXXXXX`), sea cual sea el proveedor.
- **Costo.** Con `twilio` o `infobip`, cada código enviado se cobra a la cuenta correspondiente. Las cuentas de prueba de Twilio, según sus reglas, solo envían a números verificados en su consola.
- **Endpoints sin protección.** `/otps` no tiene autenticación ni límite de envíos. Con un proveedor real configurado en el servidor, cualquiera que llegue al endpoint puede generar SMS a cargo de esa cuenta. No lo publiques en internet con `twilio` o `infobip` sin agregar autenticación y un límite de envíos.

---

## Agregar un proveedor

1. Crear una clase en `adapter/out/sms` que implemente `SmsSender` (`send(Cellphone destination, String message)`).
2. Anotarla con `@Component` y `@ConditionalOnProperty(name = "sms.provider", havingValue = "mi-proveedor")`.
3. Agregar sus propiedades en `SmsProperties` y en `application.yaml`.
4. Si falla la entrega, lanzar `SmsDeliveryFailedException` (se traduce a `502 SMS_DELIVERY_FAILED`).

No hay que tocar los casos de uso ni el dominio.
