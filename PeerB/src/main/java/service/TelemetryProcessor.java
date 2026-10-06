package service;

import model.TelemetryData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROCESADOR DE TELEMETRÍA - Este es el "cerebro" del sistema
 *
 * ¿QUÉ HACE?
 * Recibe mensajes de texto (del servidor UDP)
 * Los procesa (valida, analiza, decide qué responder)
 * Devuelve una respuesta
 *
 * ¿CUÁNDO USARLO?
 * - En el servidor UDP, cuando recibe un paquete
 * - El servidor llama: processor.process(mensaje)
 *
 * IMPORTANTE: Esta clase NO toca UDP directamente
 *             Solo procesa TEXTO
 */
public class TelemetryProcessor {

    /**
     * MAPA DE "MEMORIA"
     *
     * ¿QUÉ ES?
     * Un diccionario que guarda las ÚLTIMAS lecturas de cada sensor
     *
     * ¿POR QUÉ?
     * Cuando el cliente pida STATUS, tienes la información guardada
     *
     * EJEMPLO:
     * lastReadings = {
     *     "sensor-01" → TelemetryData{TEMP, 25.5}
     *     "sensor-02" → TelemetryData{HUMIDITY, 60.0}
     * }
     *
     * ConcurrentHashMap = Es thread-safe (múltiples clientes simultáneamente)
     */
    private final Map<String, TelemetryData> lastReadings = new ConcurrentHashMap<>();

    /**
     * MÉTODO PRINCIPAL: PROCESAR MENSAJE
     *
     * @param rawMessage Mensaje recibido del cliente (sin procesar)
     *                   EJEMPLO: "sensor-01;TEMP;25.5"
     *                   EJEMPLO: "STATUS;sensor-01"
     *
     * @return Respuesta a enviar al cliente
     *         EJEMPLO: "OK;TEMP_RECORDED;25.5"
     *         EJEMPLO: "ERROR;INVALID_FORMAT"
     *
     * FLUJO:
     * 1. Validar que no sea null/vacío
     * 2. Dividir por ";"
     * 3. Si es STATUS → manejar consulta
     * 4. Si es telemetría → procesar datos
     * 5. Retornar respuesta
     */
    public String process(String rawMessage) {
        // ═══════════════════════════════════════════════════════════════
        // PASO 1: VALIDACIÓN INICIAL
        // ═══════════════════════════════════════════════════════════════

        /**
         * ¿QUÉ PASA SI NO VALIDAMOS?
         * rawMessage = null
         * arreglo = rawMessage.split(";")  ← ¡NullPointerException!
         *
         * El programa CRASHEA
         *
         * ¿SOLUCIÓN?
         * Verificar PRIMERO que no sea null/vacío
         */
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            // Si es null o vacío (después de quitar espacios)
            return "ERROR;INVALID_FORMAT";
        }

        // ═══════════════════════════════════════════════════════════════
        // PASO 2: DIVIDIR MENSAJE POR ";"
        // ═══════════════════════════════════════════════════════════════

        /**
         * SPLIT: Divide un String por un delimitador
         *
         * EJEMPLO:
         * "sensor-01;TEMP;25.5".split(";")
         * Resultado: ["sensor-01", "TEMP", "25.5"]
         *
         * ¿POR QUÉ USAR split(";", -1)?
         * El -1 es IMPORTANTE
         *
         * split(";")    → Descarta elementos vacíos al final
         * split(";", -1) → Mantiene TODOS, incluso vacíos
         *
         * EJEMPLO:
         * "sensor-01;;25.5".split(";")     → ["sensor-01", "25.5"]
         * "sensor-01;;25.5".split(";", -1) → ["sensor-01", "", "25.5"]
         *
         * Necesitamos -1 para DETECTAR el error (elemento vacío)
         */
        String[] arreglo = rawMessage.trim().split(";", -1);

        // Validación: ¿Split funcionó?
        if (arreglo.length == 0) {
            // Esto casi nunca pasa, pero por si acaso
            return "ERROR;INVALID_FORMAT";
        }

        // ═══════════════════════════════════════════════════════════════
        // PASO 3: ¿ES CONSULTA STATUS?
        // ═══════════════════════════════════════════════════════════════

        /**
         * FORMATO STATUS:
         * "STATUS;sensor-01"
         *
         * ¿QUÉ QUIERE EL CLIENTE?
         * "¿Cuál fue la última lectura de sensor-01?"
         *
         * ¿QUÉ RESPONDEMOS?
         * Si existe: "STATUS_OK;sensor-01;TEMP;25.5"
         * Si NO existe: "ERROR;DEVICE_NOT_FOUND"
         */
        if (arreglo[0].equalsIgnoreCase("STATUS")) {
            // El primer elemento es "STATUS" (sin importar mayúsculas)
            // Ejemplo: "STATUS", "status", "Status" → todos OK

            /**
             * VALIDACIÓN 1: ¿Tiene exactamente 2 partes?
             *
             * ✓ "STATUS;sensor-01"     → 2 partes → OK
             * ✗ "STATUS;sensor-01;abc" → 3 partes → ERROR
             * ✗ "STATUS;"              → 2 partes pero 2ª vacía → ERROR
             */
            if (arreglo.length != 2 || arreglo[1].trim().isEmpty()) {
                return "ERROR;INVALID_FORMAT";
            }

            // Extraer el device ID
            String deviceId = arreglo[1].trim();
            // Ejemplo: deviceId = "sensor-01"

            /**
             * ¿EXISTE ESTE DISPOSITIVO EN NUESTRO MAPA?
             *
             * lastReadings.containsKey(deviceId)
             *
             * ✓ SI está guardado → tiene una lectura anterior
             * ✗ NO está guardado → nunca envió datos
             */
            if (!lastReadings.containsKey(deviceId)) {
                // Este sensor NUNCA envió datos
                // No sabemos su última lectura
                return "ERROR;DEVICE_NOT_FOUND";
            }

            /**
             * OBTENER LA ÚLTIMA LECTURA
             *
             * lastReadings.get(deviceId)
             * Devuelve el objeto TelemetryData guardado
             *
             * TelemetryData contiene:
             * - deviceId (ej: "sensor-01")
             * - sensorType (ej: "TEMP")
             * - value (ej: 25.5)
             */
            TelemetryData data = lastReadings.get(deviceId);

            // Construir respuesta
            // Formato: "STATUS_OK;deviceId;sensorType;value"
            return "STATUS_OK;" + data.getDeviceId() + ";" +
                    data.getSensorType() + ";" + data.getValue();
        }

        // ═══════════════════════════════════════════════════════════════
        // PASO 4: SI NO ES STATUS, DEBE SER TELEMETRÍA
        // ═══════════════════════════════════════════════════════════════

        /**
         * FORMATO TELEMETRÍA:
         * "sensor-01;TEMP;25.5"
         *
         * Partes:
         * [0] = deviceId (identificador único del sensor)
         * [1] = sensorType (TEMP, HUMIDITY, BATTERY, etc)
         * [2] = value (el número de la lectura)
         *
         * VALIDACIONES:
         * 1. ¿Tiene exactamente 3 partes?
         * 2. ¿Ninguna parte está vacía?
         * 3. ¿El valor se puede convertir a número?
         */
        if (arreglo.length != 3 || arreglo[0].trim().isEmpty() ||
                arreglo[1].trim().isEmpty() || arreglo[2].trim().isEmpty()) {
            /**
             * CASOS DE ERROR:
             * ✗ "sensor-01;TEMP"           → Solo 2 partes
             * ✗ "sensor-01;;25.5"          → Parte 2 vacía
             * ✗ ";;25.5"                   → Parte 1 y 2 vacías
             * ✗ "sensor-01; TEMP ;25.5"    → Después de trim() OK, pero mejor rechazar
             */
            return "ERROR;INVALID_FORMAT";
        }

        // Extraer las 3 partes
        String deviceId = arreglo[0].trim();       // "sensor-01"
        String sensorType = arreglo[1].trim();     // "TEMP"
        String valueStr = arreglo[2].trim();       // "25.5" (aún es STRING)

        // ═══════════════════════════════════════════════════════════════
        // PASO 5: CONVERTIR VALOR A NÚMERO
        // ═══════════════════════════════════════════════════════════════

        /**
         * ¿POR QUÉ TRY-CATCH?
         *
         * Double.parseDouble("25.5")   → ✓ Funciona, devuelve 25.5
         * Double.parseDouble("abc")    → ✗ Lanza NumberFormatException
         * Double.parseDouble("25.5a")  → ✗ Lanza NumberFormatException
         *
         * Sin try-catch:
         *   El programa CRASHEA
         *
         * Con try-catch:
         *   Atrapamos el error y respondemos con ERROR;INVALID_FORMAT
         */
        double value;
        try {
            value = Double.parseDouble(valueStr);
            // Ahora value es un número: 25.5
        } catch (NumberFormatException e) {
            // No pudimos convertir a número
            // valueStr contenía algo como "abc" o "25.5a"
            return "ERROR;INVALID_FORMAT";
        }

        // ═══════════════════════════════════════════════════════════════
        // PASO 6: GUARDAR EN LA "BASE DE DATOS"
        // ═══════════════════════════════════════════════════════════════

        /**
         * GUARDAR PARA RESPONDER STATUS DESPUÉS
         *
         * lastReadings.put(clave, valor)
         *
         * Clave: deviceId (ej: "sensor-01")
         * Valor: TelemetryData con tipo y valor
         *
         * ANTES:
         * lastReadings = {
         *     "sensor-01" → {TEMP, 20.5}
         * }
         *
         * DESPUÉS de recibir "sensor-01;TEMP;25.5":
         * lastReadings = {
         *     "sensor-01" → {TEMP, 25.5}  ← ACTUALIZADO
         * }
         *
         * ¿POR QUÉ?
         * Si el cliente pide "STATUS;sensor-01"
         * Nosotros tenemos la última lectura guardada
         */
        lastReadings.put(deviceId, new TelemetryData(deviceId, sensorType, value));

        // ═══════════════════════════════════════════════════════════════
        // PASO 7: EVALUAR SEGÚN TIPO DE SENSOR
        // ═══════════════════════════════════════════════════════════════

        /**
         * DIFERENTES REGLAS PARA DIFERENTES SENSORES
         *
         * TEMP (Temperatura):
         *   > 40.0°C → ALERTA (demasiado caliente)
         *   < 0.0°C  → ALERTA (congelamiento)
         *   0-40°C   → OK
         *
         * HUMIDITY (Humedad):
         *   > 90% → ALERTA (demasiado húmedo)
         *   < 20% → ALERTA (muy seco)
         *   20-90% → OK
         *
         * BATTERY (Batería):
         *   < 20% → ALERTA (batería baja)
         *   ≥ 20% → OK
         *
         * UNKNOWN → ERROR
         */
        switch (sensorType.toUpperCase()) {

            // ────────────────────────────────────────────────────────
            // CASO 1: TEMPERATURA
            // ────────────────────────────────────────────────────────
            case "TEMP":
                if (value > 40.0) {
                    // TOO HOT - Ejemplo: 42.5°C
                    return "ALERT;HIGH_TEMPERATURE;" + value;
                } else if (value < 0.0) {
                    // FREEZING - Ejemplo: -5.0°C
                    return "ALERT;FREEZING_TEMPERATURE;" + value;
                } else {
                    // NORMAL - Ejemplo: 25.5°C
                    return "OK;TEMP_RECORDED;" + value;
                }

                // ────────────────────────────────────────────────────────
                // CASO 2: HUMEDAD
                // ────────────────────────────────────────────────────────
            case "HUMIDITY":
                if (value > 90.0) {
                    // TOO HUMID - Ejemplo: 95%
                    return "ALERT;HIGH_HUMIDITY;" + value;
                } else if (value < 20.0) {
                    // TOO DRY - Ejemplo: 15%
                    return "ALERT;LOW_HUMIDITY;" + value;
                } else {
                    // NORMAL - Ejemplo: 60%
                    return "OK;HUMIDITY_RECORDED;" + value;
                }

                // ────────────────────────────────────────────────────────
                // CASO 3: BATERÍA
                // ────────────────────────────────────────────────────────
            case "BATTERY":
                if (value < 20.0) {
                    // LOW BATTERY - Ejemplo: 15%
                    return "ALERT;LOW_BATTERY;" + value;
                } else {
                    // GOOD BATTERY - Ejemplo: 80%
                    return "OK;BATTERY_OK;" + value;
                }

                // ────────────────────────────────────────────────────────
                // CASO POR DEFECTO: SENSOR DESCONOCIDO
                // ────────────────────────────────────────────────────────
            default:
                /**
                 * ¿QUÉ PASÓ?
                 * El cliente envió un tipo de sensor que no reconocemos
                 *
                 * EJEMPLO:
                 * "sensor-01;PRESION;1013.25"
                 *
                 * PRESION no es uno de nuestros tipos
                 * (solo soportamos TEMP, HUMIDITY, BATTERY)
                 */
                return "ERROR;UNKNOWN_SENSOR_TYPE";
        }
    }

    /**
     * MÉTODO AUXILIAR: OBTENER TODAS LAS LECTURAS
     *
     * ¿CUÁNDO SE USA?
     * En las pruebas (tests)
     * Para verificar que se guardaron correctamente
     *
     * @return El mapa completo de lecturas
     */
    public Map<String, TelemetryData> getLastReadings() {
        return lastReadings;
    }

    /**
     * MÉTODO AUXILIAR: LIMPIAR LA BASE DE DATOS
     *
     * ¿CUÁNDO SE USA?
     * Entre pruebas
     * Para empezar con un estado limpio
     *
     * Sin este método, las pruebas se interfieren una con otra
     */
    public void clear() {
        lastReadings.clear();
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * POSIBLES VARIACIONES EN EL PARCIAL
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. CAMBIAR LÍMITES DE TEMPERATURA
 *    En lugar de > 40, podría ser > 35 o > 50
 *    ¿CÓMO ADAPTARTE?
 *    - Leer el problema
 *    - Cambiar el número en el if
 *    - La lógica sigue igual
 *
 * 2. AGREGAR NUEVO TIPO DE SENSOR
 *    Ejemplo: "PRESSURE" (presión)
 *    ¿CÓMO ADAPTARTE?
 *    - Añadir nuevo case en el switch
 *    - Implementar sus reglas (rangos)
 *    - El resto del código NO cambia
 *
 * 3. CAMBIAR FORMATO DEL MENSAJE
 *    En lugar de deviceId;sensorType;value
 *    Podría ser: timestamp;deviceId;sensorType;value
 *    ¿CÓMO ADAPTARTE?
 *    - Cambiar el split (buscar otro delimitador)
 *    - Ajustar las validaciones (length)
 *    - El resto de la lógica NO cambia
 *
 * 4. AGREGAR VALIDACIÓN DE RANGO PARA DISPOSITIVO
 *    "El deviceId debe empezar con sensor-"
 *    ¿CÓMO ADAPTARTE?
 *    - Después de extraer deviceId
 *    - Añadir: if (!deviceId.startsWith("sensor-"))
 *    - return "ERROR;INVALID_DEVICE_ID"
 *
 * 5. CAMBIAR EL FORMATO DE RESPUESTA
 *    En lugar de "OK;TEMP_RECORDED;25.5"
 *    Podría ser: "SUCCESS;TEMP;25.5"
 *    ¿CÓMO ADAPTARTE?
 *    - Solo cambiar los strings de respuesta
 *    - La lógica NO cambia
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * ERRORES COMUNES QUE NO COMETER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ❌ NO VALIDAR NULL
 * if (rawMessage.trim().isEmpty()) → NullPointerException si rawMessage es null
 * ✅ VALIDAR NULL PRIMERO
 * if (rawMessage == null || rawMessage.trim().isEmpty())
 *
 * ❌ USAR split(";") en lugar de split(";", -1)
 * No detectarás elementos vacíos
 * ✅ USAR split(";", -1)
 *
 * ❌ NO USAR try-catch EN Double.parseDouble()
 * Si no es número, crash
 * ✅ ENVOLVER EN try-catch
 *
 * ❌ GUARDAR SIN VALIDAR
 * lastReadings.put(deviceId, data)
 * Pero ¿y si deviceId era vacío?
 * ✅ VALIDAR PRIMERO, LUEGO GUARDAR
 *
 * ❌ OLVIDAR ALGÚN TIPO DE SENSOR EN SWITCH
 * El cliente envía "PRESSURE" pero no hay case
 * Cae en default → "ERROR;UNKNOWN_SENSOR_TYPE"
 * ✅ REVISAR TODOS LOS TIPOS EN EL PROBLEMA
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */