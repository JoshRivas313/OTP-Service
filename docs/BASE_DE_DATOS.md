# Base de datos

El servicio guarda los códigos en MongoDB, en la colección `otps` de la base indicada por `MONGODB_URI` (por defecto `otp_service`).

## Contenido

- [Modelo del documento](#modelo-del-documento)
- [Ciclo de vida de un código](#ciclo-de-vida-de-un-código)
- [Operaciones atómicas](#operaciones-atómicas)
- [Índices](#índices)
- [Datos que se guardan](#datos-que-se-guardan)

---

## Modelo del documento

Colección: `otps`. Clase: `OtpDocument` (`adapter/out/persistence/document`). El modelo de dominio equivalente es `Otp`; `OtpPersistenceMapper` convierte entre ambos.

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador del documento |
| `cellphone` | string | Celular normalizado con prefijo, por ejemplo `+51912345678` |
| `codeHash` | string | HMAC-SHA256 del código en hexadecimal (64 caracteres). Nunca se guarda el código |
| `digits` | int | Cantidad de dígitos del código |
| `validityWindow.generatedAt` | date | Momento de emisión |
| `validityWindow.expiresAt` | date | Momento en que expira |
| `verificationStatus.attempts` | int | Intentos fallidos acumulados |
| `verificationStatus.used` | boolean | `true` cuando el código ya se verificó |
| `verificationStatus.invalidated` | boolean | `true` cuando se emitió uno más reciente para el mismo celular |
| `purgeAt` | date | `expiresAt` más el tiempo de retención (86400 s por defecto). Lo usa el índice TTL |

Ejemplo ilustrativo:

```json
{
  "_id": { "$oid": "6ab4a50af570751a6a05454e" },
  "cellphone": "+51912345678",
  "codeHash": "2bf48820fa7831b689bb65976e231370457fd3347d76c8e010d0aef2aa6018e8",
  "digits": 6,
  "validityWindow": {
    "generatedAt": { "$date": "2026-09-24T04:20:26.670Z" },
    "expiresAt":   { "$date": "2026-09-24T04:20:56.670Z" }
  },
  "verificationStatus": { "attempts": 0, "used": false, "invalidated": false },
  "purgeAt": { "$date": "2026-09-25T04:20:56.670Z" }
}
```

---

## Ciclo de vida de un código

No hay un campo de estado: el estado se deduce de los datos.

| Estado | Condición |
|---|---|
| Activo | No usado, no invalidado, no expirado y `attempts` menor al máximo |
| Usado | `verificationStatus.used = true` |
| Invalidado | `verificationStatus.invalidated = true` |
| Bloqueado | `attempts` mayor o igual a `otp.max-attempts` (3 por defecto) |
| Expirado | La hora actual es posterior a `validityWindow.expiresAt` |

Al generar un código nuevo para un celular, los anteriores de ese celular que seguían activos pasan a invalidados.

---

## Operaciones atómicas

La verificación no lee el documento, lo modifica en memoria y lo guarda: dos verificaciones simultáneas del mismo código podrían pasar las dos. En su lugar, cada cambio es una sola operación de MongoDB (`OtpRepositoryImpl`, con `MongoTemplate`):

| Operación | Consulta de MongoDB | Efecto |
|---|---|---|
| `invalidateActive` | `updateMulti` sobre el celular con `used=false` e `invalidated=false` | Marca `invalidated=true` |
| `claimIfMatches` | `findAndModify` por `_id`, `codeHash`, `used=false`, `invalidated=false`, `attempts < máximo` y `expiresAt > ahora` | Marca `used=true` y devuelve el documento. Si nada coincide, no cambia nada |
| `registerFailedAttempt` | `findAndModify` por `_id` con `used=false` e `invalidated=false` | Incrementa `attempts` en 1 y devuelve el documento actualizado |

Como la condición y el cambio ocurren en una sola operación, un mismo código solo puede reclamarse una vez.

---

## Índices

La clase `OtpDocument` declara dos índices:

| Nombre | Campos | Para qué |
|---|---|---|
| `otp_cellphone_generated_idx` | `{ cellphone: 1, "validityWindow.generatedAt": -1 }` | Buscar el código más reciente de un celular |
| `otp_purge_ttl_idx` | `{ purgeAt: 1 }` con `expireAfterSeconds: 0` | Que MongoDB borre solo los documentos cuando llega `purgeAt` |

**Estado actual: no se crean automáticamente.** Spring Data MongoDB no crea índices salvo que se le indique, y este proyecto no lo indica. En la base local de pruebas la colección `otps` solo tenía el índice `_id_`, con 29 documentos, algunos de varios días atrás que ya debieron purgarse. Mientras los índices no existan, los códigos no se borran solos.

Hay dos formas de activarlos.

**Opción 1. Activar la creación automática de índices** en `application.yaml`:

```yaml
spring:
  data:
    mongodb:
      auto-index-creation: true
```

Al crear el índice TTL sobre una colección que ya tiene datos, MongoDB borra de inmediato los documentos cuyo `purgeAt` ya pasó.

**Opción 2. Crearlos a mano** con `mongosh`:

```js
use otp_service
db.otps.createIndex({ purgeAt: 1 }, { name: "otp_purge_ttl_idx", expireAfterSeconds: 0 })
db.otps.createIndex({ cellphone: 1, "validityWindow.generatedAt": -1 }, { name: "otp_cellphone_generated_idx" })
```

MongoDB ejecuta el borrado por TTL aproximadamente cada minuto.

---

## Datos que se guardan

- El código nunca se guarda, solo su hash con clave (HMAC-SHA256 con `OTP_HASH_SECRET`).
- El celular se guarda en claro, en formato `+51...`. Es un dato personal: la retención por defecto es de 24 horas después de la expiración, siempre que el índice TTL esté activo.
- Las credenciales de Twilio no se guardan en la base de datos: viven en la sesión HTTP (ver [PROVEEDORES_SMS.md](./PROVEEDORES_SMS.md)).
- Un código de 6 dígitos tiene un millón de combinaciones. Si alguien obtiene la base de datos y la clave `OTP_HASH_SECRET`, puede recuperar los códigos por fuerza bruta. Por eso hay que cambiar la clave por defecto (`dev-only-secret-change-me`); la aplicación lo avisa en el arranque.
