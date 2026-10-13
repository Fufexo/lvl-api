# ADR-0001: Monolito modular con Spring Modulith

**Estado:** Aceptada
**Fecha:** 2026-10-13

## Contexto

LVL necesita un backend para cuentas, transferencias, tarjetas, recompensas y, más adelante, notificaciones. Lo mantiene una sola persona y se despliega en planes gratuitos, donde cada servicio se duerme por separado tras unos minutos sin tráfico. Aun así, los dominios tienen que quedar bien separados para poder crecer o extraer uno si hiciera falta.

## Decisión

Una sola aplicación Spring Boot dividida en módulos por dominio (`auth`, `accounts`, `fx` y `shared`), con límites verificados por Spring Modulith en cada build:

- Cada módulo es un paquete. Lo público del paquete raíz es su API; el resto es package-private.
- Los módulos se comunican por esa API (`UserDirectory`) o por eventos (`UserRegistered`).
- `ModularityTest` falla si un módulo usa clases internas de otro o si aparece un ciclo.

## Opciones consideradas

### A. Monolito modular (elegida)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Baja: un deploy, una base de datos, transacciones locales |
| Costo | Un solo servicio gratuito |
| Escalabilidad | Vertical; un módulo puede extraerse luego porque sus límites ya están probados |
| Operación | Un log, una URL, un arranque en frío |

### B. Microservicios (auth, cuentas, notificaciones)

| Dimensión | Evaluación |
|---|---|
| Complejidad | Alta: consistencia entre servicios (sagas o outbox), descubrimiento, trazas distribuidas |
| Costo | Varios servicios dormidos: la primera petición despierta una cadena |
| Escalabilidad | Independiente por servicio, sin necesidad real a esta escala |
| Operación | N pipelines, N configuraciones, N puntos de falla |

### C. Monolito sin límites (paquetes por capa)

Simple al principio, pero todo puede depender de todo y nada impide que se convierta en una maraña.

## Análisis

Una transferencia y la apertura de cuentas al registrarse deben ser atómicas. En un monolito eso es una transacción de base de datos; con microservicios haría falta una saga para lo mismo. Los beneficios de los microservicios (equipos y escalado independientes) no aplican a este proyecto. Spring Modulith da los límites sin pagar el costo de la red.

## Consecuencias

- La apertura de cuentas escucha `UserRegistered` dentro de la misma transacción del registro: si falla, el registro se revierte.
- Las notificaciones (fase siguiente) pueden ir en un servicio aparte, alimentado por eventos publicados después del commit.
- Si un módulo necesitara escalar por separado, se extrae con sus límites ya probados.
