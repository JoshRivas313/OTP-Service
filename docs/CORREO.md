# Correo

El canal de correo envía el código a una dirección de email en lugar de un celular. No necesita ninguna cuenta para el visitante: escribe su correo y recibe el código. El resto del flujo (hash, expiración, intentos, verificación atómica) es el mismo que con SMS.

## Contenido

- [Proveedores disponibles](#proveedores-disponibles)
- [Por qué Brevo y no Gmail por SMTP](#por-qué-brevo-y-no-gmail-por-smtp)
- [Configurar Brevo](#configurar-brevo)
- [Validaciones al arrancar](#validaciones-al-arrancar)
- [Límites](#límites)
- [Agregar un proveedor](#agregar-un-proveedor)

---

## Proveedores disponibles

Se elige con `EMAIL_PROVIDER`.

| Valor | Qué hace | Variables |
|---|---|---|
| `console` (por defecto) | Escribe el correo en el log, con el destinatario enmascarado. No envía nada | ninguna |
| `brevo` | Envía con la API HTTPS de Brevo | `BREVO_API_KEY`, `EMAIL_SENDER_ADDRESS`, `EMAIL_SENDER_NAME` (opcional) |

El correo lleva el asunto "Tu código de verificación" y el mensaje en texto plano. El texto se puede personalizar con el campo `message` (ver [API.md](./API.md#post-apiemailotps--generar-código-otp)).

---

## Por qué Brevo y no Gmail por SMTP

- **Render bloquea SMTP en el plan gratuito.** Sus servicios web gratuitos no pueden enviar tráfico saliente por los puertos 25, 465 y 587, que son los de SMTP. Gmail por SMTP funcionaría en local y fallaría en el despliegue.
- **Brevo se usa por HTTPS** (`POST /v3/smtp/email`), que no está bloqueado, y su plan gratuito incluye 300 correos al día.
- **Con Gmail habría que exponer una cuenta personal como remitente** de todos los códigos, con riesgo de suspensión si alguien abusa del endpoint.

---

## Configurar Brevo

1. Crea una cuenta en Brevo.
2. Valida en Brevo el correo que usarás como remitente. Brevo exige un remitente validado para enviar.
3. Crea una clave de API en tu cuenta de Brevo. Los nombres de las secciones pueden cambiar; consulta la documentación de Brevo.
4. Define las variables en el entorno del servicio (en Render, en **Environment**):

| Variable | Valor |
|---|---|
| `EMAIL_PROVIDER` | `brevo` |
| `BREVO_API_KEY` | La clave de API. Es un secreto: no la pegues en el repositorio ni en el chat |
| `EMAIL_SENDER_ADDRESS` | El remitente validado en Brevo |
| `EMAIL_SENDER_NAME` | Opcional. Nombre que verá el destinatario |

Si un envío falla, la respuesta es `502 EMAIL_DELIVERY_FAILED` y el log del servidor registra el estado y el motivo que devolvió Brevo (por ejemplo, un remitente sin validar o una clave incorrecta).

---

## Validaciones al arrancar

- Con `EMAIL_PROVIDER=brevo`, la aplicación no arranca si faltan `BREVO_API_KEY` o `EMAIL_SENDER_ADDRESS`.
- `OTP_DEMO_MODE=true` no se puede combinar con `EMAIL_PROVIDER=brevo`: el modo demo devuelve el código en la respuesta y anularía el sentido de enviarlo.

---

## Límites

- **Cuota del proveedor.** El plan gratuito de Brevo permite 300 correos al día. Al superarla, los envíos fallan con `502`.
- **Límite de envíos de la aplicación.** Por defecto, 5 envíos por correo y 20 por IP cada 10 minutos; el siguiente responde `429 RATE_LIMIT_EXCEEDED`. Se ajusta con `OTP_RATE_LIMIT_PER_DESTINATION`, `OTP_RATE_LIMIT_PER_IP` y `OTP_RATE_LIMIT_WINDOW_SECONDS`. El contador vive en memoria: se reinicia con la aplicación y no se comparte entre instancias.
- **Entrega.** Un correo puede tardar unos segundos y, según el remitente, terminar en la carpeta de spam.
- **Sin comprobación del buzón.** Solo se valida el formato de la dirección, no que exista.

---

## Agregar un proveedor

1. Crear una clase que implemente `EmailSender` en `adapter/out/email`.
2. Anotarla con `@ConditionalOnProperty(name = "email.provider", havingValue = "...")`.
3. Agregar sus propiedades a `EmailProperties`.
4. Si falla la entrega, lanzar `EmailDeliveryFailedException` (se traduce a `502 EMAIL_DELIVERY_FAILED`).
