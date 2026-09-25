# Plan de Migración: Arquitectura Hexagonal

## Objetivo
Migrar gradualmente de la arquitectura actual (Layered: Controller → Service → Repository) a Hexagonal (Puertos y Adaptadores).

## Estado Actual: Fase 1 ✅ Completada
- ✅ Nuevas carpetas de estructura hexagonal creadas
- ✅ Puertos (interfaces) definidos
- ✅ Value Objects movidos a Domain
- ✅ Casos de Uso creados (GenerateOtp, VerifyOtp)
- ✅ Adaptadores de Salida implementados (SMS, Persistencia)
- ✅ Adaptador HTTP creado (OtpHttpAdapter)
- ✅ Documentación arquitectónica escrita
- ✅ Adaptador de Integración legacy para transición suave

## Fase 2: Implementar Casos de Uso (En Progreso)
- [ ] Crear VerifyOtpUseCaseImpl completo
- [ ] Implementar TwilioGenerateOtpUseCase
- [ ] Implementar TwilioVerifyOtpUseCase
- [ ] Actualizar OtpPersistenceAdapter con métodos faltantes
- [ ] Crear TwilioSessionPort y adaptadores

## Fase 3: Migrar Controllers
- [ ] Cambiar OtpController → usar GenerateOtpUseCase directamente
- [ ] Cambiar TwilioOtpController → usar adaptadores nuevos
- [ ] Cambiar TwilioConnectController → puerto nuevo
- [ ] Remover inyección de legacy OtpService

## Fase 4: Testing
- [ ] Tests unitarios de Domain Services
- [ ] Tests de Use Cases sin dependencias externas
- [ ] Tests de Adaptadores con mocks
- [ ] Verificar que endpoints sigan funcionando

## Fase 5: Limpieza
- [ ] Remover controladores viejos
- [ ] Remover servicios legacy (OtpService, TwilioOtpService)
- [ ] Remover repositorios legacy
- [ ] Verificar imports e incluir solo nuevas carpetas

## Estructura Final
```
src/main/java/com/otpservice/otp/
├── domain/                  # Business core (zero external deps)
├── application/             # Use cases
├── adapter/                 # Spring, DB, SMS implementations
└── shared/                  # Exceptions, utilities
```

## Beneficios Esperados
1. **Testabilidad**: Mocks sin Spring, sin BD
2. **Flexibilidad**: Agregar nuevo SMS provider = 1 clase
3. **Claridad**: Dominio separado de frameworks
4. **Escalabilidad**: Más fácil agregar features nuevas
5. **Mantenimiento**: Cambios localizados por capa

## Timeline Estimado
- Fase 2: 2-3 horas
- Fase 3: 1-2 horas
- Fase 4: 1-2 horas
- Fase 5: 30 min

**Total: 5-8 horas de refactorización completando implementación.**

## Cómo Ejecutar la Migración

### Paso 1: Crear Use Case
```java
@Service
public class GenerateOtpUseCaseImpl implements GenerateOtpUseCase {
  @Override
  public GenerateOtpCommand generate(GenerateOtpCommand command) {
    // Orquestar dominio + adaptadores
    return command;
  }
}
```

### Paso 2: Crear Adaptador SMS
```java
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "custom")
public class CustomSmsAdapter implements SmsPort {
  @Override
  public void sendOtpCode(String phone, String code) {
    // Enviar SMS
  }
}
```

### Paso 3: Verificar
```bash
./mvnw clean compile
./mvnw spring-boot:run
curl -X POST http://localhost:8080/api/otps -H "Content-Type: application/json" \
  -d '{"cellphone":"987654321","digits":6,"durationSeconds":300}'
```

## Notas Importantes
- Legacy OtpService sigue funcionando durante migración
- Endpoints siguen siendo los mismos (/api/otps, /api/twilio/otps)
- Cambios son transparentes al usuario
- Gradualismo: un controlador a la vez
