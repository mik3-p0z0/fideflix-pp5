# Fase 9 — Protocolo de validación

Marcá cada casilla ejecutándola, no leyéndola. **Lo que no se prueba no funciona: solo no ha fallado todavía.**

Antes de empezar, dejá la base en estado conocido: ejecutá `sql/01_schema.sql` y `sql/02_datos_iniciales.sql`.

---

## A. Pruebas automáticas

Clic derecho sobre cada clase → *Run File* (Shift+F6). En este orden:

Están en el paquete `fideflix.pruebas`.

- [ ] **`PruebaConexion`** → imprime motor, driver y las 6 obras.
- [ ] **`PruebaDAO`** → `30 correctas, 0 fallidas`.
- [ ] **`PruebaRed`** → todas en `[OK]`. *(Cerrar `Main` antes: comparten el puerto 5000.)*

Si alguna falla, parar acá. Las siguientes pruebas asumen que la base y la red están sanas.

---

## B. Flujo funcional completo

Ejecutar `Main.java`.

### Usuarios

- [ ] Registrar un usuario nuevo → en Workbench, `SELECT * FROM usuario;` muestra un hash de **64 caracteres hexadecimales**, nunca la contraseña.
- [ ] Registrar el mismo email otra vez → mensaje de duplicado, sin fila nueva.
- [ ] El campo *Fecha de registro* aparece precargado y **no se puede editar**.
- [ ] Login correcto → entra al menú.
- [ ] Login con contraseña errada → *Acceso denegado*.
- [ ] Login con email inexistente → **el mismo mensaje** que el anterior (no revela qué cuentas existen).

### CRUD de audiovisuales

- [ ] El catálogo carga las 6 obras iniciales.
- [ ] Filtrar por cada tipo → 2 obras en cada uno.
- [ ] **Crear una película** → aparece en la tabla y en `SELECT * FROM pelicula;`.
- [ ] **Crear un documental** → fila en `documental`.
- [ ] **Crear una serie** → fila en `serie`.
- [ ] Al cambiar el combo *Tipo* en el formulario, los campos específicos se intercambian.
- [ ] **Editar** una obra: cambiar título y un campo específico → ambos cambian en la base.
- [ ] En modo edición, el combo *Tipo* está **deshabilitado**.
- [ ] **Eliminar** con confirmación → desaparece de `audiovisual` y de su tabla hija.

### Comentarios

- [ ] Publicar un comentario en una obra → aparece con autor y fecha.
- [ ] Abrir *Comentarios* en **otra** obra → está vacía. *(Prueba de que el bug del `static` murió.)*
- [ ] Eliminar una obra con comentarios → `SELECT COUNT(*) FROM comentario;` baja. *(Cascada.)*

---

## C. Concurrencia — el requisito explícito de la consigna

- [ ] Abrir **dos clientes** desde el lanzador y entrar con usuarios distintos.
- [ ] La bitácora del servidor muestra conexiones con nombres de hilo diferentes (`cliente-1`, `cliente-2`…).
- [ ] Cliente A crea una obra → cliente B pulsa *Actualizar* y **la ve**.
      → *Prueba que el estado vive en el servidor, no en cada cliente.*
- [ ] A y B crean obras casi simultáneamente → **ambas** existen, ninguna se perdió.
- [ ] A elimina una obra que B tiene seleccionada; B intenta editarla → mensaje *"otro usuario pudo haberla eliminado"*, sin excepción.

---

## D. Robustez

- [ ] Detener el servidor desde su ventana y operar desde un cliente → mensaje claro, **sin stack trace en pantalla**.
- [ ] Detener MySQL (`net stop MySQL80`, ajustar al nombre de tu servicio) y operar → error controlado en la bitácora; la aplicación no se cae.
- [ ] Reiniciar MySQL y volver a operar → funciona sin reiniciar la aplicación.
- [ ] Campos inválidos: año `"abc"`, IMDb `15`, título vacío → rechazados con mensaje.
- [ ] Cerrar todo y volver a abrir → **los datos siguen ahí**. *(Esto es lo que prueba que la persistencia es real.)*

---

## E. Seguridad

- [ ] **Inyección SQL por la interfaz**: crear una obra con título
      ```
      Prueba'); DROP TABLE comentario; --
      ```
      → se guarda como texto literal y `comentario` sigue existiendo.

- [ ] **Texto que rompe el protocolo**: en la descripción, escribir varias líneas con `|` en el medio
      → se guarda y se recupera idéntico, con los saltos intactos.

- [ ] La bitácora del servidor **nunca** muestra una contraseña (aparecen `*****`).

- [ ] Provocar un error de base (detener MySQL y operar) → el cliente recibe un mensaje genérico;
      el `SQLState` y el detalle quedan **solo** en la bitácora del servidor.

- [ ] Como `fideflix_app` en Workbench: `DROP TABLE usuario;` → **falla** por permisos.

- [ ] En la terminal: `git log -p | Select-String "PASSmysql"` → **sin resultados**.

> Si alguna de las de esta sección falla, no es un detalle cosmético: es la diferencia entre
> *"aplica conocimientos técnicos"* (nivel 2) y *"analiza buenas prácticas"* (nivel 3) en DD.1.

---

## F. Antes de entregar

- [ ] Exportar el diagrama ER: Workbench → *Database* → *Reverse Engineer* → guardar como `docs/modelo_er.png`.
- [ ] Completar los integrantes en el README.
- [ ] Borrar o cambiar la contraseña del usuario `prueba@fideflix.com`.
- [ ] `git status` limpio y todo empujado a `main`.
- [ ] Verificar en GitHub que **no** aparece `db.properties`.
- [ ] Comprimir `Fideflix/` para la plataforma, **sin** `build/`, `dist/` ni `db.properties`.
- [ ] Descomprimir el ZIP en otra carpeta y abrirlo en NetBeans → **debe compilar**.
      *(Esta última es la que más entregas ha salvado: verifica que el `.jar` viaje dentro del proyecto.)*
