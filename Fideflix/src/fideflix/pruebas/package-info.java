/*
 * ═══════════════════════════════════════════════════════════════════
 * PAQUETE DE PRUEBAS  —  fideflix.pruebas
 * ═══════════════════════════════════════════════════════════════════
 *
 * QUE ES ESTO
 * Tres programas ejecutables que VERIFICAN el sistema. No forman parte
 * del flujo de la aplicacion: ninguna clase de interfaz, red, logica o
 * persistencia los invoca. Se ejecutan a mano, con clic derecho ->
 * Run File (Shift+F6), y cada uno tiene su propio main.
 *
 * POR QUE ESTAN EN EL PROYECTO Y NO BORRADOS
 * Porque son la evidencia de que el sistema fue VALIDADO, no solamente
 * escrito. Afirmar "la transaccion funciona" y poder ejecutar una clase
 * que fuerza el fallo del segundo INSERT y comprueba que no quedo fila
 * huerfana son dos cosas distintas. La segunda es verificable; la
 * primera es una promesa.
 *
 * POR QUE ESTAN EN UN PAQUETE APARTE
 * Para que la separacion sea explicita en la estructura y no haya que
 * deducirla del nombre de los archivos. Codigo de produccion y codigo
 * de verificacion cumplen funciones distintas y conviene que se vea.
 *
 * ─── LAS TRES CLASES, EN ORDEN DE ALCANCE ──────────────────────────
 *
 *   PruebaConexion   Conectividad JDBC: driver en el classpath,
 *                    credenciales correctas, base alcanzable.
 *
 *   PruebaDAO        La capa de persistencia completa: CRUD, mapeo de
 *                    la herencia, atomicidad de la transaccion,
 *                    resistencia a inyeccion SQL. 30 verificaciones.
 *
 *   PruebaRed        Extremo a extremo sobre sockets TCP reales:
 *                    protocolo, escape de caracteres y atencion
 *                    concurrente de 8 clientes simultaneos.
 *
 * El orden no es casual: cada una aisla UNA capa. Si algo falla mas
 * arriba, la que pasa elimina sospechosos. Depurar es reducir la lista
 * de culpables posibles, y por eso conviene tener pruebas de distinto
 * alcance en vez de una sola que lo abarque todo.
 *
 * ─── ADVERTENCIAS ──────────────────────────────────────────────────
 *
 * 1. Estas pruebas ESCRIBEN en la base de datos. Cada una limpia lo que
 *    inserta, pero no deben ejecutarse contra datos que importen. Una
 *    prueba que ensucia el estado obliga a resetear a mano, y una
 *    prueba incomoda de correr termina no corriendose.
 *
 * 2. PruebaRed levanta su PROPIO servidor en el puerto 5000. Hay que
 *    cerrar la aplicacion principal (Main) antes de ejecutarla, o
 *    recibira un BindException por puerto ocupado.
 *
 * 3. Requieren la base en estado conocido: ejecutar 01_schema.sql y
 *    02_datos_iniciales.sql antes de la primera corrida.
 *
 * ─── NOTA SOBRE HERRAMIENTAS ───────────────────────────────────────
 * En un proyecto profesional esto seria JUnit 5, con anotaciones @Test,
 * aserciones y ejecucion automatica en cada compilacion. Aqui se
 * escribieron a mano para no agregar dependencias externas al proyecto
 * Ant y para que el mecanismo de verificacion quede a la vista en vez
 * de escondido detras de un framework. JUnit + Testcontainers es el
 * paso siguiente natural, y esta anotado como tal en el README.
 */
package fideflix.pruebas;
