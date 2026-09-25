# Arquitectura Hexagonal (Puertos y Adaptadores)

## Descripción

Este proyecto implementa arquitectura hexagonal para lograr máxima separación de concernimientos y facilitar testing, mantenimiento y evolución del código.

## Estructura de Capas

```
src/main/java/com/otpservice/otp/
├── domain/                          # Núcleo del negocio (cero dependencias externas)
│   ├── model/                       # Entidades de dominio (agregados raíz)
│   │   └── OtpAggregate             # Agregado raíz del OTP
│   ├── valueobject/                 # Value Objects inmutables
│   │   ├── Cellphone                # Teléfono validado
│   │   ├── OtpCode                  # Código generado
│   │   ├── ValidityWindow           # Ventana de validez
│   │   ├── VerificationStatus       # Estado de intentos
│   │   └── TwilioCredentials        # Credenciales encapsuladas
│   ├── port/                        # Interfaces (contratos)
│   │   ├── input/                   # Casos de uso
│   │   │   ├── GenerateOtpUseCase   # Contrato: generar OTP
│   │   │   └── VerifyOtpUseCase     # Contrato: verificar OTP
│   │   └── output/                  # Adaptadores requeridos
│   │       ├── OtpPersistencePort   # Puerto: persistencia
│   │       └── SmsPort              # Puerto: envío de SMS
│   └── service/                     # Servicios de dominio
│       └── OtpDomainService         # Lógica de negocio pura
│
├── application/                     # Casos de uso (orquestación)
│   ├── usecase/                     # Implementaciones de casos de uso
│   │   ├── GenerateOtpUseCaseImpl    # Implementa GenerateOtpUseCase
│   │   ├── VerifyOtpUseCaseImpl      # Implementa VerifyOtpUseCase
│   │   └── OtpCodeGenerator         # Utilidad: generación de códigos
│   ├── port/                        # Interfaces de aplicación
│   └── dto/                         # Data Transfer Objects
│
├── adapter/                         # Adaptadores concretos
│   ├── in/                          # Adaptadores de entrada
│   │   └── http/                    # Controllers REST
│   │       ├── OtpHttpAdapter       # Adaptador HTTP para OTP local
│   │       ├── TwilioHttpAdapter    # Adaptador HTTP para Twilio
│   │       └── TwilioConnectAdapter # Adaptador HTTP para conexión
│   ├── out/                         # Adaptadores de salida
│   │   ├── persistence/             # Persistencia
│   │   │   ├── OtpPersistenceAdapter# Implementa OtpPersistencePort
│   │   │   └── OtpMapper            # Mapeo dominio ↔ persistencia
│   │   └── sms/                     # Servicios SMS
│   │       ├── ConsoleSmsAdapter    # Debug: imprime en consola
│   │       ├── TwilioSmsAdapter     # SMS: Twilio
│   │       └── InfobipSmsAdapter    # SMS: Infobip
│   └── config/                      # Configuración Spring
│       └── HexagonalArchitectureConfig
│
├── shared/                          # Código compartido
│   ├── exception/                   # Excepciones globales
│   └── utility/                     # Utilidades
│
├── controller/                      # Legacy: Remover gradualmente
├── service/                         # Legacy: Remover gradualmente
└── repository/                      # Legacy: Remover gradualmente
```

## Flujo de Dependencias

```
   HTTP Request
        ↓
  HTTP Adapter (Entrada)
        ↓
  Caso de Uso (Aplicación)
        ↓
  Servicio de Dominio
        ↓
  Modelo de Dominio
        ↓
  Puertos de Salida (interfaces)
        ↓
  Adaptadores de Salida (implementaciones)
        ↓
  Sistema Externo (BD, SMS, etc)
```

## Reglas de Arquitectura

### 1. Domain Layer (Sin dependencias externas)
- ✅ Lógica de negocio pura
- ✅ Value Objects
- ✅ Agregados
- ❌ No Spring
- ❌ No JPA/Hibernate
- ❌ No librerías externas (excepto validación básica)

### 2. Application Layer (Orquestación)
- ✅ Coordina llamadas a dominio y adaptadores
- ✅ Implementa Casos de Uso
- ✅ Depende de Domain Ports
- ❌ Lógica de negocio directa
- ❌ Detalles de persistencia

### 3. Adapter Layer (Detalles técnicos)
- ✅ Spring, JPA, Controllers
- ✅ Implementa interfaces de puertos
- ✅ Mapeos dominio ↔ externos
- ❌ Lógica de negocio
- ❌ Conocimiento de otros adaptadores

## Casos de Uso Principales

### GenerateOtpUseCase
```java
Input:  { cellphone, digits, durationSeconds }
Process:
  1. Generar código random con digits dígitos
  2. Hashear código con HMAC-SHA256
  3. Crear agregado OtpAggregate
  4. Persistir en BD
  5. Enviar SMS
Output: { message, code }
```

### VerifyOtpUseCase
```java
Input:  { cellphone, code }
Process:
  1. Buscar OTP por cellphone
  2. Validar no expirado
  3. Validar no bloqueado
  4. Validar hash del código
  5. Registrar intento (exitoso o fallido)
Output: { message, code } o error
```

## Puertos

### Input Ports (Casos de Uso)
- `GenerateOtpUseCase`: Generar código OTP
- `VerifyOtpUseCase`: Verificar código OTP

### Output Ports (Adaptadores Requeridos)
- `OtpPersistencePort`: Guardar/recuperar OTPs
- `SmsPort`: Enviar SMS
- `TwilioVerifyPort`: Validar credenciales Twilio

## Adaptadores

### Input Adapters (HTTP Controllers)
- `OtpHttpAdapter`: POST /api/otps (generar y verificar local)
- `TwilioHttpAdapter`: POST /api/twilio/otps (generar y verificar con Twilio)
- `TwilioConnectAdapter`: POST /api/twilio/connect (conectar sesión)

### Output Adapters (Implementaciones)
- `OtpPersistenceAdapter`: MongoDB persistence
- `ConsoleSmsAdapter`: Console logging (debug)
- `TwilioSmsAdapter`: Twilio SMS
- `InfobipSmsAdapter`: Infobip SMS

## Ventajas

1. **Testabilidad**: Puertos permiten mocks sin dependencias
2. **Flexibilidad**: Cambiar adaptadores sin tocar dominio
3. **Mantenimiento**: Dominio claramente separado
4. **Escalabilidad**: Fácil agregar nuevos adaptadores
5. **Independencia**: Dominio sin acoplamiento tecnológico

## Roadmap: De Arquitectura Actual a Hexagonal

- ✅ Paso 1: Crear estructura de carpetas
- ✅ Paso 2: Definir puertos (interfaces)
- ✅ Paso 3: Crear casos de uso
- ✅ Paso 4: Crear adaptadores
- ⏳ Paso 5: Migrando controllers viejos
- ⏳ Paso 6: Remover código legacy
- ⏳ Paso 7: Tests en nueva arquitectura

## Ejemplo: Nuevo Adaptador SMS

Para agregar un nuevo proveedor de SMS (ej: AWS SNS):

```java
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "aws-sns")
public class AwsSnsSmsAdapter implements SmsPort {
  private final AmazonSNS sns;
  
  @Override
  public void sendOtpCode(String phoneNumber, String code) {
    sns.publish(phoneNumber, "Tu OTP: " + code);
  }
}
```

**Solo se agregó 1 clase nueva**, sin tocar dominio ni casos de uso.

