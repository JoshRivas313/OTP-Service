# Base de datos

Este documento describe el modo MongoDB. Por defecto el servicio guarda los códigos en memoria y no usa ninguna base de datos (ver [Almacenamiento en memoria](#almacenamiento-en-memoria)). Con el perfil `mongo` (`SPRING_PROFILES_ACTIVE=mongo`) los guarda en la colección `otps` de la base indicada por `MONGODB_URI` (por defecto `otp_service`).

## Contenido

- [Modelo del documento](#modelo-del-documento)
- [Ciclo de vida de un código](#ciclo-de-vida-de-un-código)
- [Operaciones atómicas](#operaciones-atómicas)
- [Índices](#índices)
- [Credenciales HOTP y TOTP](#credenciales-hotp-y-totp)
- [Almacenamiento en memoria](#almacenamiento-en-memoria)
- [Datos que se guardan](#datos-que-se-guardan)

---

## Modelo del documento

Colección: `otps`. Clase: `OtpDocument` (`adapter/out/persistence/document`). El modelo de dominio equivalente es `Otp`; `OtpPersistenceMapper` convierte entre ambos.

| Campo | Tipo | Descripción |
|---|---|---|
| `_id` | ObjectId | Identificador del documento |
| `destination` | string | Celular normalizado con prefijo (`+51912345678`) o correo en minúsculas (`visitante@correo.com`) |
| `codeHash` | string | HMAC-SHA256 del código en hexadecimal (64 caracteres). Nunca se guarda el código |
| `digits` | int | Cantidad de dígitos del código |
| `validityWindow.generatedAt` | date | Momento de emisión |
| `validityWindow.expiresAt` | date | Momento en que expira |
| `verificationStatus.attempts` | int | Intentos fallidos acumulados |
| `verificationStatus.used` | boolean | `true` cuando el código ya se verificó |
| `verificationStatus.invalidated` | boolean | `true` cuando se emitió uno más reciente para el mismo destino |
| `purgeAt` | date | `expiresAt` más el tiempo de retención (86400 s por defecto). Lo usa el índice TTL |

Ejemplo ilustrativo:

```json
{
  "_id": { "$oid": "6ab4a50af570751a6a05454e" },
  "destination": "+51912345678",
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

Al generar un código nuevo para un destino, los anteriores de ese destino que seguían activos pasan a invalidados.

---

## Operaciones atómicas

La verificación no lee el documento, lo modifica en memoria y lo guarda: dos verificaciones simultáneas del mismo código podrían pasar las dos. En su lugar, cada cambio es una sola operación de MongoDB (`OtpRepositoryImpl`, con `MongoTemplate`):

| Operación | Consulta de MongoDB | Efecto |
|---|---|---|
| `invalidateActive` | `updateMulti` sobre el destino con `used=false` e `invalidated=false` | Marca `invalidated=true` |
| `claimIfMatches` | `findAndModify` por `_id`, `codeHash`, `used=false`, `invalidated=false`, `attempts < máximo` y `expiresAt > ahora` | Marca `used=true` y devuelve el documento. Si nada coincide, no cambia nada |
| `registerFailedAttempt` | `findAndModify` por `_id` con `used=false` e `invalidated=false` | Incrementa `attempts` en 1 y devuelve el documento actualizado |

Como la condición y el cambio ocurren en una sola operación, un mismo código solo puede reclamarse una vez.

---

## Índices

La clase `OtpDocument` declara dos índices:

| Nombre | Campos | Para qué |
|---|---|---|
| `otp_destination_generated_idx` | `{ destination: 1, "validityWindow.generatedAt": -1 }` | Buscar el código más reciente de un destino |
| `otp_purge_ttl_idx` | `{ purgeAt: 1 }` con `expireAfterSeconds: 0` | Que MongoDB borre solo los documentos cuando llega `purgeAt` |

Con el perfil `mongo`, la aplicación crea los dos índices al arrancar (`spring.data.mongodb.auto-index-creation: true`). Al crear el índice TTL sobre una colección que ya tiene datos, MongoDB borra de inmediato los documentos cuyo `purgeAt` ya pasó, y después ejecuta el borrado aproximadamente cada minuto.

Si prefieres crearlos a mano, con `mongosh`:

```js
use otp_service
db.otps.createIndex({ purgeAt: 1 }, { name: "otp_purge_ttl_idx", expireAfterSeconds: 0 })
db.otps.createIndex({ destination: 1, "validityWindow.generatedAt": -1 }, { name: "otp_destination_generated_idx" })
```

---

## Credenciales HOTP y TOTP

Colección `hmac_credentials`: un secreto por destino, tipo (HOTP o TOTP) y modo (`DELIVERED` si el servidor envía el código por SMS o correo, `APP` si lo genera la app del usuario).

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | String | Se conserva al volver a vincular el mismo correo y tipo |
| `destination` | String | Correo normalizado |
| `type` | String | `HOTP` o `TOTP` |
| `mode` | String | `DELIVERED` o `APP` |
| `secretCiphertext` | Binary | Secreto cifrado con AES-256-GCM (`OTP_SECRET_ENCRYPTION_KEY`) |
| `secretNonce` | Binary | 12 bytes, distinto en cada cifrado |
| `digits` | int | 6 u 8 |
| `periodSeconds` | int | TOTP: 30 o 60. HOTP: 0 |
| `counter` | long | HOTP: próximo contador que se acepta |
| `issuedCounter` | long | HOTP en modo `DELIVERED`: próximo contador que se emite. Los contadores entre `counter` e `issuedCounter` son códigos enviados y aún sin usar |
| `lastUsedTimeStep` | long | TOTP: última ventana aceptada (`-1` si ninguna) |
| `status` | String | `PENDING` hasta confirmar la app, luego `ACTIVE`. En modo `DELIVERED` nace `ACTIVE` |
| `failedAttempts` | int | Fallos seguidos; vuelve a 0 al acertar o al bloquear |
| `lockedUntil` | Date | Fin del bloqueo, o ausente |
| `createdAt`, `confirmedAt` | Date | |

**Índice:** `credential_destination_type_mode_idx`, único sobre `{ destination: 1, type: 1, mode: 1 }`.

**Operaciones atómicas:** aceptar un código es un `updateFirst` condicionado. En TOTP, `lastUsedTimeStep < ventana`; en HOTP, `counter == esperado`. Si dos peticiones llegan con el mismo código, solo una modifica el documento y la otra recibe `409 OTP_ALREADY_USED`. Los fallos suman con `$inc`.

**No se guarda ningún código** de la app: se recalcula en cada verificación.

---

## Almacenamiento en memoria

Sin el perfil `mongo`, `InMemoryOtpPersistenceAdapter` implementa el mismo puerto (`OtpPersistencePort`) con un mapa en memoria. No hay colección ni índices.

- **Atomicidad:** un único lock protege todo el estado, así que cada operación (`invalidateActive`, `claimIfMatches`, `registerFailedAttempt`) es indivisible, igual que en MongoDB. Con 10 verificaciones simultáneas del mismo código, solo una pasa.
- **Purga:** al guardar un código nuevo se eliminan los que ya superaron `purgeAt` (expiración más `otp.retention-seconds`), y las consultas ignoran los ya purgados.
- **Tope:** se guardan como máximo `OTP_MEMORY_MAX_ENTRIES` códigos (10000 por defecto). Al llegar, se descartan los más antiguos, incluso si aún estaban vigentes. Sirve para que el consumo de memoria no crezca sin límite.
- **Persistencia:** ninguna. Al reiniciar la aplicación se pierden todos los códigos, y tampoco se comparten entre instancias.
- **Apps autenticadoras:** `InMemoryCredentialAdapter` guarda los registros con el mismo esquema de lock único. Un reinicio los borra y el usuario tiene que volver a vincular su app; `GET /health` devuelve `storage: memory` para que la interfaz lo advierta.
---

## Datos que se guardan

- El código nunca se guarda, solo su hash con clave (HMAC-SHA256 con `OTP_HASH_SECRET`).
- El destino (celular o correo) se guarda en claro. Es un dato personal. Es un dato personal: la retención por defecto es de 24 horas después de la expiración, siempre que el índice TTL esté activo (perfil `mongo`). En memoria desaparece al reiniciar o cuando lo purga la aplicación.
- El secreto de cada app autenticadora se guarda cifrado con AES-256-GCM. Sin `OTP_SECRET_ENCRYPTION_KEY` no se puede descifrar; si esa clave se pierde o se cambia, todas las apps vinculadas dejan de servir.
- Las credenciales de Twilio no se guardan en la base de datos: viven en la sesión HTTP (ver [PROVEEDORES_SMS.md](./PROVEEDORES_SMS.md)).
- Un código de 6 dígitos tiene un millón de combinaciones. Si alguien obtiene la base de datos y la clave `OTP_HASH_SECRET`, puede recuperar los códigos por fuerza bruta. Por eso hay que cambiar la clave por defecto (`dev-only-secret-change-me`); la aplicación lo avisa en el arranque.
