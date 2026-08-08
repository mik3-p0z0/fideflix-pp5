# Fideflix — Práctica Programada 5

Prueba de concepto cliente-servidor para la plataforma de contenido audiovisual **Fideflix**.
Curso: Programación Cliente-Servidor — Universidad Fidélitas, II Cuatrimestre 2026.

> **Estado:** en desarrollo. Este README crece con cada fase; la versión completa
> (modelo de datos, protocolo, decisiones de diseño y limitaciones) se consolida al cierre.

---

## Qué hace

Aplicación de escritorio en Java (Swing) con arquitectura cliente-servidor sobre sockets TCP.
Un servidor multihilo atiende peticiones concurrentes de N clientes y gestiona la
persistencia en una base de datos MySQL.

| Entrega | Alcance |
|---|---|
| PP4 | Sockets TCP, protocolo de texto, hilo por conexión, persistencia en archivo serializado |
| **PP5 (actual)** | Migración a **MySQL**: CRUD de Películas, Documentales y Series + comentarios |

---

## Arquitectura

```
[Cliente Swing] --socket TCP--> [HiloCliente] --> [DAO] --JDBC--> [MySQL: fideflix]
   N ventanas                     N hilos          PreparedStatement   transacciones
```

### Paquetes

| Paquete | Responsabilidad |
|---|---|
| `fideflix.logica` | Modelo de dominio: `Audiovisual` (abstracta), `Pelicula`, `Documental`, `Serie`, `Usuario` |
| `fideflix.red` | Protocolo de aplicación, servidor, hilos de atención y cliente de sockets |
| `fideflix.persistencia` | Acceso a datos (patrón DAO). **Todo el SQL vive aquí y solo aquí** |
| `fideflix.interfaz` | Ventanas Swing y punto de entrada |
| `fideflix.excepciones` | Excepciones propias del dominio |

---

## Requisitos

- JDK 26 (o el configurado en `nbproject/project.properties`)
- MySQL Server 8.x
- MySQL Workbench
- MySQL Connector/J (incluido en `lib/`)
- NetBeans IDE (proyecto Ant)

---

## Instalación

> Se completa en la Fase 2. Resumen del procedimiento previsto:
>
> 1. Ejecutar `sql/01_schema.sql` y `sql/02_datos_iniciales.sql` desde Workbench.
> 2. Crear el usuario de aplicación `fideflix_app` con permisos mínimos.
> 3. Copiar `db.properties.example` a `db.properties` y completar las credenciales.
>    **`db.properties` no se versiona**: está en `.gitignore`.
> 4. Abrir el proyecto en NetBeans y ejecutar `fideflix.interfaz.Main`.

---

## Ejecución

`Main.java` levanta la ventana del servidor (que inicia la escucha en el puerto 5000)
y el lanzador de clientes. Cada clic en **Nuevo cliente** abre una sesión independiente,
lo que permite demostrar la atención concurrente sin volver a ejecutar el programa.

---

## Seguridad

Este es un proyecto académico. Las decisiones y limitaciones de seguridad
se documentan de forma explícita al cierre de la práctica.

- Las contraseñas se almacenan como *hash*, nunca en claro.
- Todo acceso a la base usa `PreparedStatement` (prevención de inyección SQL — OWASP A03:2021).
- La aplicación se conecta con un usuario MySQL de privilegios mínimos, nunca como `root`.
- Las credenciales viven fuera del código y fuera del repositorio.

---

## Integrantes

- Michael Pozo Lopez — <!-- completar con el resto del grupo -->
