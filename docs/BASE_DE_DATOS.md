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
| `purpose` | string | `LOGIN`, `REGISTER`, `PASSWORD_RECOVERY` o `PAYMENT_CONFIRMATION`. El código solo se verifica con este propósito |
| `codeHash` | string | HMAC-SHA256 del código en hexadecimal (64 caracteres). Nunca se guarda el código |
| `digits` | int | Cantidad de dígitos del código |
| `validityWindow.generatedAt` | date | Momento de emisión |
| `validityWindow.expiresAt` | date | Momento en que expira |
| `verificationStatus.attempts` | int | Intentos fallidos acumulados |
| `verificationStatus.used` | boolean | `true` cuando el código ya se verificó |
| `verificationStatus.invalidated` | boolean | `true` cuando se emitió uno más reciente para el mismo destino y propósito |
| `purgeAt` | date | `expiresAt` más el tiempo de retención (86400 s por defecto). Lo usa el índice TTL |

Ejemplo ilustrativo:

```json
{
  "_id": { "$oid": "6ab4a50af570751a6a05454e" },
  "destination": "+51912345678",
  "purpose": "LOGIN",
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
| `otp_destination_purpose_generated_idx` | `{ destination: 1, purpose: 1, "validityWindow.generatedAt": -1 }` | Buscar el código más reciente de un destino para un propósito |
| `otp_purge_ttl_idx` | `{ purgeAt: 1 }` con `expireAfterSeconds: 0` | Que MongoDB borre solo los documentos cuando llega `purgeAt` |

Con el perfil `mongo`, la aplicación crea los dos índices al arrancar (`spring.data.mongodb.auto-index-creation: true`). Al crear el índice TTL sobre una colección que ya tiene datos, MongoDB borra de inmediato los documentos cuyo `purgeAt` ya pasó, y después ejecuta el borrado aproximadamente cada minuto.

Si prefieres crearlos a mano, con `mongosh`:

```js
use otp_service
db.otps.createIndex({ purgeAt: 1 }, { name: "otp_purge_ttl_idx", expireAfterSeconds: 0 })
db.otps.createIndex({ destination: 1, purpose: 1, "validityWindow.generatedAt": -1 }, { name: "otp_destination_purpose_generated_idx" })
```

---

## Credenciales HOTP y TOTP

Colección `hmac_credentials`: un secreto por destino, tipo (HOTP o TOTP) y propósito. El servidor calcula el código con ese secreto y lo envía por SMS o correo.

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | String | |
| `destination` | String | Celular o correo normalizado |
| `type` | String | `HOTP` o `TOTP` |
| `purpose` | String | Propósito de la credencial. También va en los datos asociados (AAD) del cifrado: un secreto copiado a otro propósito no se descifra |
| `secretCiphertext` | Binary | Secreto cifrado con AES-256-GCM (`OTP_SECRET_ENCRYPTION_KEY`) |
| `secretNonce` | Binary | 12 bytes, distinto en cada cifrado |
| `digits` | int | 6 u 8 |
| `periodSeconds` | int | TOTP: entre 15 y 300. HOTP: 0 |
| `counter` | long | HOTP: próximo contador que se acepta |
| `issuedCounter` | long | HOTP: próximo contador que se emite. Los contadores entre `counter` e `issuedCounter` son códigos enviados y aún sin usar. En TOTP queda en 0: el código depende solo de `T = floor(unix / periodSeconds)` |
| `lastUsedTimeStep` | long | TOTP: última ventana aceptada (`-1` si ninguna) |
| `failedAttempts` | int | Fallos seguidos; vuelve a 0 al acertar o al bloquear. Emitir un código no lo reinicia |
| `lockedUntil` | Date | Fin del bloqueo (`OTP_LOCK_SECONDS` después del tercer fallo), o ausente. Emitir un código no lo borra: mientras dura, enviar también responde `423` |
| `createdAt` | Date | |
| `purgeAt` | Date | Fin de la retención: `OTP_CREDENTIAL_RETENTION_SECONDS` (30 días) después de la última emisión o verificación. MongoDB borra la credencial al llegar a este instante: guarda el correo o celular del destino, así que no se conserva para siempre |

**Índices:** `credential_destination_type_purpose_idx`, único sobre `{ destination: 1, type: 1, purpose: 1 }`, y `credential_purge_ttl_idx`, TTL sobre `purgeAt` (`expireAfterSeconds: 0`).

**Credenciales anteriores a la retención:** las que se crearon antes no tienen `purgeAt` y el índice TTL no las borra. Se renuevan solas (reciben la fecha) en cuanto su destino emite o verifica un código; las que nadie vuelva a usar se pueden limpiar a mano: `db.hmac_credentials.deleteMany({ purgeAt: { $exists: false }, createdAt: { $lt: new Date(Date.now() - 30*24*3600*1000) } })`.

**Migración desde la versión sin propósito:** una base creada antes tiene el índice único `credential_destination_type_idx` sobre `{ destination: 1, type: 1 }`, que impide crear una segunda credencial del mismo destino y tipo con otro propósito. Hay que borrarlo una vez; la aplicación crea el nuevo al arrancar. Los documentos viejos sin `purpose` quedan sin uso (ninguna consulta los encuentra) y los códigos OTP viejos vencen solos.

```js
use otp_service
db.hmac_credentials.dropIndex("credential_destination_type_idx")
db.otps.dropIndex("otp_destination_generated_idx")
```

**Operaciones atómicas:** aceptar un código es un `updateFirst` condicionado. En TOTP, `lastUsedTimeStep < ventana`; en HOTP, `counter == esperado`. Si dos peticiones llegan con el mismo código, solo una modifica el documento y la otra recibe `409 OTP_ALREADY_USED`. Los fallos suman con `$inc`.

**No se guarda ningún código** de HOTP ni de TOTP: se recalcula en cada verificación.

---

## Almacenamiento en memoria

Sin el perfil `mongo`, `InMemoryOtpPersistenceAdapter` implementa el mismo puerto (`OtpPersistencePort`) con un mapa en memoria. No hay colección ni índices.

- **Atomicidad:** un único lock protege todo el estado, así que cada operación (`invalidateActive`, `claimIfMatches`, `registerFailedAttempt`) es indivisible, igual que en MongoDB. Con 10 verificaciones simultáneas del mismo código, solo una pasa.
- **Purga:** al guardar un código nuevo se eliminan los que ya superaron `purgeAt` (expiración más `otp.retention-seconds`), y las consultas ignoran los ya purgados.
- **Tope:** se guardan como máximo `OTP_MEMORY_MAX_ENTRIES` códigos (10000 por defecto). Al llegar, se descartan los más antiguos, incluso si aún estaban vigentes. Sirve para que el consumo de memoria no crezca sin límite.
- **Persistencia:** ninguna. Al reiniciar la aplicación se pierden todos los códigos, y tampoco se comparten entre instancias.
- **Secretos de HOTP y TOTP:** `InMemoryCredentialAdapter` los guarda con el mismo esquema de lock único. Un reinicio los borra y el siguiente código de ese destino empieza con un secreto nuevo.
---

## Datos que se guardan

- El código nunca se guarda, solo su hash con clave (HMAC-SHA256 con `OTP_HASH_SECRET`).
- El destino (celular o correo) se guarda en claro. Es un dato personal. Es un dato personal: la retención por defecto es de 24 horas después de la expiración, siempre que el índice TTL esté activo (perfil `mongo`). En memoria desaparece al reiniciar o cuando lo purga la aplicación. Con el almacenamiento en memoria, al llegar a `OTP_MEMORY_MAX_ENTRIES` se descarta la credencial menos usada, no la más antigua.
- El secreto de HOTP y TOTP de cada destino se guarda cifrado con AES-256-GCM. Sin `OTP_SECRET_ENCRYPTION_KEY` no se puede descifrar; si esa clave se pierde o se cambia, los secretos guardados dejan de servir.
- Las credenciales de Twilio no se guardan en la base de datos: viven en la sesión HTTP (ver [PROVEEDORES_SMS.md](./PROVEEDORES_SMS.md)).
- Un código de 6 dígitos tiene un millón de combinaciones. Si alguien obtiene la base de datos y la clave `OTP_HASH_SECRET`, puede recuperar los códigos por fuerza bruta. Por eso hay que cambiar la clave por defecto (`dev-only-secret-change-me`); la aplicación lo avisa en el arranque.
