# ADR-0002: Libro contable de partida doble con bloqueo pesimista

**Estado:** Aceptada
**Fecha:** 2026-10-13

## Contexto

El dinero no puede crearse ni perderse por un error, una carrera entre peticiones o un reintento. Hace falta saber siempre por qué un saldo es el que es, y dos compras simultáneas no pueden dejar una cuenta en negativo.

## Decisión

- **Partida doble.** Cada operación (`transfers`) genera asientos (`ledger_entries`) que, por moneda, suman cero. El dinero sale de una cuenta y entra en otra; el bono de bienvenida sale de una cuenta de tesorería y el cambio de moneda pasa por cuentas de compensación (`FX_PEN`, `FX_USD`).
- **Montos en unidades mínimas** (`bigint`, céntimos). Nunca `double`. En la API se usan decimales con validación de dos decimales.
- **Saldo en la fila de la cuenta**, actualizado en la misma transacción que los asientos y verificado en los tests contra la suma de asientos.
- **Bloqueo pesimista** (`SELECT … FOR UPDATE`) de todas las cuentas de la operación, en orden fijo de id, antes de leer saldos. Solo la clase `Ledger` cambia saldos.
- **Una restricción en la base de datos** (`balance_minor >= 0` para cuentas de usuario) como última defensa.
- **Idempotencia.** `Idempotency-Key` obligatoria en `POST /api/transfers`, con índice único por usuario. Un reintento con la misma clave y el mismo cuerpo devuelve la transferencia original; con otro cuerpo, 422.

## Opciones consideradas

| | Saldo calculado (`SUM` de asientos) | Saldo en fila + bloqueo pesimista (elegida) | Saldo en fila + bloqueo optimista (`@Version`) |
|---|---|---|---|
| Lecturas | Costosas a medida que crece el historial | Inmediatas | Inmediatas |
| Concurrencia | Igual necesita bloquear para validar fondos | Las operaciones sobre la misma cuenta esperan su turno | Los conflictos fallan y hay que reintentar |
| Complejidad | Baja | Media | Media, con lógica de reintentos |
| Experiencia | — | Sin errores espurios para el usuario | Errores bajo carga si no se reintenta bien |

## Análisis

En una billetera, las operaciones sobre una misma cuenta son poco frecuentes por usuario, así que esperar un bloqueo cuesta milisegundos. El bloqueo optimista desplazaría esa complejidad a reintentos en la aplicación. El orden fijo de bloqueo evita interbloqueos entre transferencias cruzadas (A→B y B→A al mismo tiempo).

## Consecuencias

- Las cuentas de sistema (tesorería, compensación FX) son puntos calientes: todas las operaciones de cambio las bloquean. Es aceptable a esta escala. A mayor volumen se repartirían en varias subcuentas o se dejaría de llevar su saldo en la fila.
- Corregir un error implica asientos compensatorios; los asientos nunca se editan.
- El test de concurrencia (20 transferencias simultáneas) y el invariante "por moneda suma cero" corren en cada build.
