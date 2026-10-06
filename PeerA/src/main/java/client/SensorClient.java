package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * CLIENTE UDP - La parte que ENVÍA y RECIBE
 *
 * ¿QUÉ HACE?
 * 1. Se conecta al servidor UDP (sin conexión real, solo envía)
 * 2. Envía un mensaje
 * 3. ESPERA respuesta (con timeout)
 * 4. Recibe y devuelve la respuesta
 *
 * IMPORTANTE: No hay conexión establecida como en TCP
 *             Solo envía y espera
 *
 * SIMULA: Un sensor IoT que envía datos al servidor
 *
 * FLUJO:
 * sendTelemetry() → Construye mensaje → sendAndReceive()
 * queryStatus()   → Construye mensaje → sendAndReceive()
 * sendAndReceive() → Envía → Espera → Recibe → Devuelve
 */
public class SensorClient {

    /**
     * DIRECCIÓN DEL SERVIDOR
     *
     * ¿QUÉ ES?
     * El nombre del servidor al que se conecta
     *
     * EJEMPLOS:
     * - "localhost"      → Tu mismo computador
     * - "127.0.0.1"      → Tu mismo computador (IP)
     * - "192.168.1.5"    → Otra computadora en la red
     * - "server.com"     → Servidor en internet
     *
     * IMPORTANTE: Este se convierte a IP con InetAddress.getByName()
     */
    private final String serverHost;

    /**
     * PUERTO DEL SERVIDOR
     *
     * ¿QUÉ ES?
     * El puerto en el que escucha el servidor
     *
     * DEBE COINCIDIR CON:
     * El puerto en que creó el servidor
     * new BaseStationServer(5000, processor)
     *
     * EJEMPLO:
     * Servidor: new DatagramSocket(5000)
     * Cliente: serverPort = 5000  ✓ Coincide
     */
    private final int serverPort;

    /**
     * TIMEOUT EN MILISEGUNDOS
     *
     * ¿QUÉ ES?
     * Tiempo máximo de espera para recibir respuesta
     *
     * EJEMPLO:
     * timeoutMs = 2000
     *
     * FLUJO:
     * 1. Envía mensaje
     * 2. Espera respuesta...
     * 3. Pasan 2000 milisegundos (2 segundos)
     * 4. Sin respuesta aún
     * 5. Lanza SocketTimeoutException
     * 6. El cliente no se cuelga para siempre
     *
     * SIN TIMEOUT:
     * Cliente esperaría eternamente si servidor está apagado
     * El programa se quedaría congelado
     */
    private final int timeoutMs;

    /**
     * CONSTRUCTOR
     *
     * @param serverHost Host/IP del servidor (ej: "localhost")
     * @param serverPort Puerto del servidor (ej: 5000)
     * @param timeoutMs Timeout en milisegundos (ej: 2000)
     */
    public SensorClient(String serverHost, int serverPort, int timeoutMs) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.timeoutMs = timeoutMs;
    }

    /**
     * ENVIAR TELEMETRÍA
     *
     * ¿QUÉ HACE?
     * Construye un mensaje de telemetría y lo envía
     *
     * FORMATO:
     * "deviceId;sensorType;value"
     *
     * EJEMPLO:
     * sendTelemetry("sensor-01", "TEMP", 25.5)
     *
     * Construye: "sensor-01;TEMP;25.5"
     * Llama: sendAndReceive("sensor-01;TEMP;25.5")
     *
     * @param deviceId ID del sensor (ej: "sensor-01")
     * @param sensorType Tipo de sensor (ej: "TEMP", "HUMIDITY", "BATTERY")
     * @param value Valor numérico (ej: 25.5)
     * @return Respuesta del servidor
     * @throws IOException Si hay error de red o timeout
     */
    public String sendTelemetry(String deviceId, String sensorType, double value) throws IOException {
        // Construir el mensaje
        // Ejemplo: "sensor-01" + ";" + "TEMP" + ";" + 25.5
        // Resultado: "sensor-01;TEMP;25.5"
        String message = deviceId + ";" + sensorType + ";" + value;

        // Enviar y recibir (ver método sendAndReceive abajo)
        return sendAndReceive(message);
    }

    /**
     * CONSULTAR ESTADO
     *
     * ¿QUÉ HACE?
     * Pregunta al servidor: "¿Cuál fue la última lectura de este sensor?"
     *
     * FORMATO:
     * "STATUS;deviceId"
     *
     * EJEMPLO:
     * queryStatus("sensor-01")
     *
     * Construye: "STATUS;sensor-01"
     * Llama: sendAndReceive("STATUS;sensor-01")
     *
     * RESPUESTA ESPERADA:
     * "STATUS_OK;sensor-01;TEMP;25.5"
     * o
     * "ERROR;DEVICE_NOT_FOUND"
     *
     * @param deviceId ID del sensor a consultar
     * @return Respuesta del servidor (con última lectura o error)
     * @throws IOException Si hay error de red o timeout
     */
    public String queryStatus(String deviceId) throws IOException {
        // Construir el mensaje de consulta
        // Ejemplo: "STATUS" + ";" + "sensor-01"
        // Resultado: "STATUS;sensor-01"
        String message = "STATUS;" + deviceId;

        // Enviar y recibir
        return sendAndReceive(message);
    }

    /**
     * ENVIAR Y RECIBIR (EL MÉTODO PRINCIPAL)
     *
     * ¿QUÉ HACE?
     * 1. Crear socket UDP
     * 2. Configurar timeout
     * 3. Enviar mensaje al servidor
     * 4. ESPERAR respuesta (bloqueado, con timeout)
     * 5. Recibir respuesta
     * 6. Convertir bytes → String
     * 7. Retornar la respuesta
     *
     * IMPORTANTE: Este es el método que hace TODO el trabajo
     *
     * @param message El mensaje a enviar (ej: "sensor-01;TEMP;25.5")
     * @return La respuesta del servidor
     * @throws SocketTimeoutException Si no llega respuesta en el tiempo límite
     * @throws IOException Si hay otro error de red
     */
    public String sendAndReceive(String message) throws IOException {

        // ═══════════════════════════════════════════════════════════════
        // PASO 1: CREAR SOCKET UDP CON try-with-resources
        // ═══════════════════════════════════════════════════════════════

        /**
         * TRY-WITH-RESOURCES: Cierra automáticamente
         *
         * try (recurso) {
         *     // Usa el recurso
         * } // El recurso se cierra automáticamente
         *
         * EQUIVALENTE SIN try-with-resources:
         * DatagramSocket socket = new DatagramSocket();
         * try {
         *     // Código
         * } finally {
         *     socket.close();
         * }
         *
         * ¿POR QUÉ ES IMPORTANTE?
         * El socket usa recursos del sistema
         * Debes CERRARLO cuando termines
         * try-with-resources garantiza que se cierre
         *
         * ¿QUÉ PASA SIN CERRAR?
         * Recursos se agotan
         * No puedes crear más sockets
         * El sistema se vuelve lento
         */
        try(DatagramSocket socket = new DatagramSocket()) {
            // Aquí el socket está creado y listo

            // ═══════════════════════════════════════════════════════════
            // PASO 2: CONFIGURAR TIMEOUT
            // ═══════════════════════════════════════════════════════════

            /**
             * TIMEOUT: Tiempo máximo de espera
             *
             * setSoTimeout(2000)
             *
             * Significa: "Espera máximo 2000 milisegundos (2 segundos)"
             *
             * ¿QUÉ PASA SI CONFIGURO TIMEOUT?
             * socket.receive() esperará máximo 2 segundos
             * Si no llega respuesta, lanza SocketTimeoutException
             *
             * ¿QUÉ PASA SIN TIMEOUT?
             * socket.receive() espera PARA SIEMPRE
             * Si el servidor está apagado, el cliente SE CUELGA
             * El programa nunca continúa
             *
             * ⚠️ ¡¡ ESTO ES CRÍTICO !! ⚠️
             * SI OLVIDAS setSoTimeout() EN EL PARCIAL:
             * Tu código parecerá estar "congelado"
             * El profesor pensará que no funciona
             */
            socket.setSoTimeout(this.timeoutMs);
            // Ahora socket.receive() tiene timeout configurado

            // ═══════════════════════════════════════════════════════════
            // PASO 3: CONVERTIR MENSAJE A BYTES
            // ═══════════════════════════════════════════════════════════

            /**
             * CONVERSIÓN: STRING → BYTES
             *
             * message = "sensor-01;TEMP;25.5"
             *
             * Después de getBytes():
             * enviarDatos = [115, 101, 110, 115, 111, 114, ...]
             *
             * ¿POR QUÉ BYTES?
             * UDP solo puede enviar bytes
             * Los strings son texto
             * Necesitamos convertir
             *
             * StandardCharsets.UTF_8:
             * Codificación de caracteres
             * Incluye letras, números, acentos, símbolos
             * UTF-8 es el estándar
             */
            byte[] enviarDatos = message.getBytes(StandardCharsets.UTF_8);

            // ═══════════════════════════════════════════════════════════
            // PASO 4: CREAR PAQUETE UDP PARA ENVIAR
            // ═══════════════════════════════════════════════════════════

            /**
             * ESTRUCTURA: new DatagramPacket(datos, tamaño, IP, puerto)
             *
             * datos = enviarDatos (los bytes a enviar)
             * tamaño = enviarDatos.length (cuántos bytes)
             * IP = InetAddress.getByName(this.serverHost)
             *      Convierte "localhost" → 127.0.0.1
             * puerto = this.serverPort (dónde escucha el servidor)
             *
             * EJEMPLO:
             * InetAddress.getByName("localhost")  → 127.0.0.1
             * InetAddress.getByName("192.168.1.5") → 192.168.1.5
             *
             * ¿CÓMO SABE DÓNDE ENVIAR?
             * El nombre del servidor se convierte a IP
             * Se envía a esa IP y puerto
             * El servidor está escuchando en ese puerto
             * El paquete llega
             */
            DatagramPacket datagramito = new DatagramPacket(
                    enviarDatos,                                    // Los bytes a enviar
                    enviarDatos.length,                             // Cuántos bytes
                    InetAddress.getByName(this.serverHost),        // IP del servidor
                    this.serverPort                                 // Puerto del servidor
            );

            // ═══════════════════════════════════════════════════════════
            // PASO 5: ENVIAR EL PAQUETE
            // ═══════════════════════════════════════════════════════════

            /**
             * socket.send(paquete)
             *
             * ENVÍA el paquete al servidor
             *
             * IMPORTANTE: UDP no espera confirmación
             * El paquete se envía y listo
             * (Es diferente a TCP que verifica que llegó)
             *
             * DESPUÉS DE ENVIAR:
             * El control vuelve a la siguiente línea
             * No hay demora
             */
            socket.send(datagramito);

            // ═══════════════════════════════════════════════════════════
            // PASO 6: CREAR BUFFER PARA RECIBIR RESPUESTA
            // ═══════════════════════════════════════════════════════════

            /**
             * BUFFER: Un "contenedor" para recibir datos
             *
             * new byte[1024]
             *
             * Crea un array vacío de 1024 bytes
             * Cuando llega la respuesta, los datos llenan este array
             *
             * ¿POR QUÉ 1024?
             * Es el tamaño máximo de un datagrama UDP
             * Podría ser más pequeño si sabes que los mensajes son cortos
             * Pero 1024 es seguro
             */
            byte[] responsebuffer = new byte[1024];

            /**
             * CREAR PAQUETE PARA RECIBIR
             *
             * new DatagramPacket(buffer, buffer.length)
             *
             * Sin IP ni puerto (aún no sabemos dónde llega)
             * El socket.receive() llena este paquete con los datos que llegan
             */
            DatagramPacket respuestaDatagramito = new DatagramPacket(
                    responsebuffer,              // Buffer donde guardar los datos
                    responsebuffer.length        // Tamaño del buffer
            );

            // ═══════════════════════════════════════════════════════════
            // PASO 7: RECIBIR RESPUESTA (¡¡ PUNTO CRÍTICO !!)
            // ═══════════════════════════════════════════════════════════

            /**
             * socket.receive(paquete)
             *
             * ¡¡ SE BLOQUEA AQUÍ !!
             *
             * El programa SE DETIENE en esta línea
             * Espera a que llegue un paquete del servidor
             *
             * ¿QUÉ PASA?
             * OPCIÓN A: Llega respuesta en < 2 segundos
             *           → Se desbloquea, continúa al siguiente paso
             *
             * OPCIÓN B: Pasan 2 segundos sin respuesta
             *           → Lanza SocketTimeoutException
             *           → Se salta al catch
             *           → El método termina con excepción
             *
             * OPCIÓN C: Servidor está apagado (SIN TIMEOUT)
             *           → Espera PARA SIEMPRE
             *           → El programa se cuelga
             *
             * ¿CÓMO SE BLOQUEA?
             * La línea socket.receive() NO retorna
             * El hilo se pausa
             * Cuando llega el paquete, se reanuda
             *
             * IMPORTANTE: Sin setTimeout(), esto es:
             * socket.receive(respuestaDatagramito);
             *     ↑
             *     Se bloquea para siempre si no llega respuesta
             *
             * CON setTimeout(), es seguro:
             * socket.setSoTimeout(2000);
             * socket.receive(respuestaDatagramito);
             *     ↑
             *     Se desbloquea en 2 segundos si no llega
             */
            socket.receive(respuestaDatagramito);

            // ═══════════════════════════════════════════════════════════
            // PASO 8: CONVERTIR BYTES RECIBIDOS → STRING
            // ═══════════════════════════════════════════════════════════

            /**
             * CONVERSIÓN: BYTES → STRING
             *
             * respuestaDatagramito contiene:
             * - getData()    → Array de bytes
             * - getOffset()  → Posición inicial de datos válidos
             * - getLength()  → Cuántos bytes son válidos
             *
             * EJEMPLO:
             * Servidor envía: "OK;TEMP_RECORDED;25.5"
             *
             * Se convierte a bytes:
             * bytes = [79, 75, 59, 84, 69, ...]
             *
             * Cliente recibe en buffer:
             * buffer = [79, 75, 59, 84, 69, 0, 0, 0, ...] (1024 bytes)
             *
             * new String() con offset/length:
             * Lee SOLO los datos válidos
             * Ignora los ceros al final
             *
             * Resultado: "OK;TEMP_RECORDED;25.5"
             */
            String respuesta = new String(
                    respuestaDatagramito.getData(),              // Array de bytes
                    respuestaDatagramito.getOffset(),            // Posición inicial
                    respuestaDatagramito.getLength(),            // Cuántos bytes
                    StandardCharsets.UTF_8                       // Codificación
            );

            // ═══════════════════════════════════════════════════════════
            // PASO 9: LIMPIAR Y RETORNAR
            // ═══════════════════════════════════════════════════════════

            /**
             * respuesta.trim()
             *
             * Quita espacios al inicio y final
             *
             * EJEMPLO:
             * respuesta = "OK;TEMP_RECORDED;25.5   "  (espacios al final)
             * Después de trim():
             * respuesta = "OK;TEMP_RECORDED;25.5"
             *
             * ¿POR QUÉ?
             * Los espacios pueden causar errores al procesar la respuesta
             * Es más seguro quitarlos
             */
            return respuesta.trim();

            // IMPORTANTE: El try-with-resources cierra automáticamente aquí
            // socket se cierra aunque haya excepciones
        }
        // FIN DEL try-with-resources
        // El socket se cerró automáticamente
    }

    /**
     * GETTER: OBTENER HOST DEL SERVIDOR
     *
     * @return El nombre/IP del servidor (ej: "localhost")
     */
    public String getServerHost() {
        return serverHost;
    }

    /**
     * GETTER: OBTENER PUERTO DEL SERVIDOR
     *
     * @return El puerto del servidor (ej: 5000)
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * GETTER: OBTENER TIMEOUT
     *
     * @return El timeout en milisegundos (ej: 2000)
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * POSIBLES VARIACIONES EN EL PARCIAL
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. CAMBIAR SERVIDOR/PUERTO
 *    En lugar de "localhost:5000"
 *    Podría ser "192.168.1.10:8000"
 *    ¿CÓMO ADAPTARTE?
 *    - Cambiar serverHost y serverPort en constructor
 *    - TODO EL CÓDIGO igual
 *
 * 2. AUMENTAR TIMEOUT
 *    Si la red es lenta
 *    timeoutMs = 5000 (5 segundos) en lugar de 2000
 *    ¿CÓMO ADAPTARTE?
 *    - Cambiar el valor en sendAndReceive()
 *    - socket.setSoTimeout(5000)
 *
 * 3. AGREGAR REINTENTOS
 *    "Si no llega respuesta, reintentar 3 veces"
 *    ¿CÓMO ADAPTARTE?
 *    - Envolver el try-catch en un for/while
 *    - Contar intentos
 *    - Si SocketTimeoutException, reintentar
 *
 * 4. PROCESAR RESPUESTA DENTRO DEL CLIENTE
 *    "Verifica que la respuesta empiece con OK"
 *    ¿CÓMO ADAPTARTE?
 *    - Antes de return respuesta.trim()
 *    - Añadir: if (!respuesta.startsWith("OK"))
 *    - Lanzar excepción o retornar error
 *
 * 5. AGREGAR LOGGING
 *    "Muestra qué se envía y qué se recibe"
 *    ¿CÓMO ADAPTARTE?
 *    - Después de enviar: System.out.println("Enviado: " + message)
 *    - Después de recibir: System.out.println("Recibido: " + respuesta)
 *
 * 6. MÚLTIPLES MENSAJES CONSECUTIVOS
 *    "Envía 3 lecturas, luego una consulta STATUS"
 *    ¿CÓMO ADAPTARTE?
 *    - Llamar sendTelemetry() 3 veces
 *    - Luego llamar queryStatus() una vez
 *    - El socket se cierra/abre para cada llamada (try-with-resources)
 *
 * 7. MANEJO DIFERENTE DE TIMEOUT
 *    "Si no llega respuesta, mostrar error específico"
 *    ¿CÓMO ADAPTARTE?
 *    - El try-with-resources está dentro de sendAndReceive()
 *    - Las excepciones se propagan al llamador
 *    - El llamador (main) puede catch SocketTimeoutException
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * ERRORES COMUNES QUE NO COMETER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ❌ NO CONFIGURAR TIMEOUT
 * socket.receive(packet);
 * → Se cuelga si servidor está apagado
 * ✅ CONFIGURAR TIMEOUT
 * socket.setSoTimeout(2000);
 * socket.receive(packet);
 *
 * ❌ NO CERRAR SOCKET
 * DatagramSocket socket = new DatagramSocket();
 * socket.send(...);
 * socket.receive(...);
 * // No cerrar → Recursos se agotan
 * ✅ USAR try-with-resources
 * try (DatagramSocket socket = new DatagramSocket()) {
 *     socket.send(...);
 *     socket.receive(...);
 * } // Se cierra automáticamente
 *
 * ❌ OLVIDAR OFFSET/LENGTH EN RECEPCIÓN
 * String msg = new String(packet.getData(), UTF_8);
 * → Lee todos los 1024 bytes (basura)
 * ✅ USAR OFFSET Y LENGTH
 * String msg = new String(packet.getData(), packet.getOffset(),
 *                         packet.getLength(), UTF_8);
 *
 * ❌ NO CONVERTIR IP
 * new DatagramPacket(bytes, bytes.length, "localhost", 5000)
 * → Error: espera InetAddress, no String
 * ✅ CONVERTIR CON getByName()
 * new DatagramPacket(bytes, bytes.length,
 *     InetAddress.getByName("localhost"), 5000)
 *
 * ❌ ENVIAR SIN CONVERTIR A BYTES
 * socket.send(message)  // message es String
 * → Error: espera DatagramPacket
 * ✅ CONVERTIR PRIMERO
 * byte[] bytes = message.getBytes(UTF_8);
 * DatagramPacket packet = new DatagramPacket(...);
 * socket.send(packet);
 *
 * ❌ CONFUNDIR ENVÍO CON RECEPCIÓN
 * socket.receive(sendPacket);  // Intentas recibir en sendPacket
 * → Error, debería ser un paquete vacío
 * ✅ USAR PAQUETES DIFERENTES
 * DatagramPacket sendPacket = new DatagramPacket(...datos...);
 * DatagramPacket receivePacket = new DatagramPacket(buffer, length);
 * socket.send(sendPacket);
 * socket.receive(receivePacket);
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * FLUJO COMPLETO: CLIENTE → SERVIDOR → CLIENTE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * CLIENTE:
 * 1. sendTelemetry("sensor-01", "TEMP", 25.5)
 * 2. Construye: "sensor-01;TEMP;25.5"
 * 3. sendAndReceive("sensor-01;TEMP;25.5")
 * 4. Crea socket
 * 5. setSoTimeout(2000)
 * 6. Convierte → bytes
 * 7. Crea DatagramPacket destino localhost:5000
 * 8. socket.send(packet)
 * 9. Crea buffer para recibir
 * 10. socket.receive(packet)  ← SE BLOQUEA AQUÍ
 *
 * RED:
 * → Paquete viaja
 *
 * SERVIDOR (escuchando):
 * 1. socket.receive() se desbloquea
 * 2. handlePacket()
 * 3. Convierte bytes → String
 * 4. processor.process("sensor-01;TEMP;25.5")
 * 5. Devuelve: "OK;TEMP_RECORDED;25.5"
 * 6. Convierte → bytes
 * 7. Crea DatagramPacket destino (IP/puerto del cliente)
 * 8. socket.send(response)
 *
 * RED:
 * → Paquete respuesta viaja
 *
 * CLIENTE (desbloqueado):
 * 11. socket.receive() se desbloquea
 * 12. Paquete lleno con respuesta
 * 13. Convierte bytes → String: "OK;TEMP_RECORDED;25.5"
 * 14. trim()
 * 15. return respuesta
 * 16. main() recibe: "OK;TEMP_RECORDED;25.5"
 * 17. Imprime
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */