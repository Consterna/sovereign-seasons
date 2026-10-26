# 🛡️ Política de Seguridad

En **Sovereign Seasons**, la seguridad, integridad y estabilidad de los servidores Minecraft que utilizan este ecosistema son de máxima prioridad. Agradecemos la colaboración de la comunidad en la divulgación coordinada y responsable de vulnerabilidades.

---

## 📦 Versiones Soportadas

Actualmente se proporciona soporte activo y parches de seguridad para las siguientes versiones del proyecto:

| Versión | Soportada | Notas |
| :--- | :---: | :--- |
| `1.0.x` | :white_check_mark: | Versión activa (Paper / Folia 1.20.4–1.21.x) |
| `< 1.0.0` | :x: | Versiones preliminares descontinuadas |

---

## 🎯 Alcance de Seguridad

Consideramos vulnerabilidades de seguridad los siguientes escenarios:
- **Denegación de Servicio (DoS / Crash):** Bloqueos de hilos regionales en Folia o el hilo principal de Paper causados por cálculos no acotados en la caché espacial o escaneos infinitos de invernaderos.
- **Corrupción o Inyección de Datos:** Pérdida de integridad en el archivo de guardado atómico `calendar_data.yml`.
- **Bypass de Permisos:** Ejecución no autorizada de comandos administrativos (`/season set`, `/season setday`, `/season reload`) sin los permisos configurados.
- **Explotación Económica / Duplicación:** Exploits de clonación o duplicación masiva de cosechas/alimentos asociados a los módulos complementarios.

---

## 🚨 Reporte Responsable de Vulnerabilidades

Si descubres una posible vulnerabilidad de seguridad, por favor **NO abras un issue público** ni lo comentes en canales de chat abiertos.

### Canales de Contacto Privado

1. **GitHub Security Advisories (Canal Preferido):**
   Envía un reporte confidencial a través de la pestaña de **Security** del repositorio:
   [https://github.com/Consterna/sovereign-seasons/security/advisories/new](https://github.com/Consterna/sovereign-seasons/security/advisories/new)

2. **Correo Electrónico de Seguridad:**
   Envía los detalles cifrados o directos a: `cristianguerrero1958@outlook.com` con el asunto `[SECURITY] Reporte de Vulnerabilidad - Sovereign Seasons`.

### Información a Incluir en el Reporte

Para evaluar y reproducir el vector de ataque eficazmente, por favor incluye:
- Descripción detallada de la vulnerabilidad y vector de ataque.
- Módulos afectados (`sovereign-seasons-core`, `pantry`, `logistics`, `commerce`).
- Pasos reproducibles o script/comando de prueba (PoC o reproductor mínimo).
- Entorno de prueba: Versión de servidor (Paper o Folia), compilación específica y versión de Java.
- Impacto potencial estimado en el servidor y sus jugadores.

---

## ⏱️ Tiempos de Respuesta y Coordinación (SLA)

- **Confirmación inicial:** En un plazo máximo de 48 horas tras el reporte.
- **Evaluación técnica:** Diagnóstico de severidad (CVSS) y mitigación preliminar en 7 días hábiles.
- **Despliegue de parche:** Creación de un parche en una bifurcación privada de GitHub Security Advisories.
- **Divulgación pública:** Publicación coordinada de un GitHub Security Advisory junto con una versión menor correctiva (`v1.0.x`) en GitHub Releases.

---

## 🤖 Prevención y Análisis Automatizado Continuo

Para garantizar un código blindado antes de cada despliegue, este repositorio implementa controles automatizados de seguridad:
- **GitHub CodeQL SAST:** Análisis semántico estático continuo en cada push y PR para detectar vulnerabilidades en código Java (CWEs, deserialización insegura, inyecciones de ruta, etc.).
- **Dependency Review & Dependabot:** Escaneo semanal de dependencias de Gradle, GitHub Actions y contenedores Docker para neutralizar CVEs de forma preventiva.
- **Secret Scanning:** Detección automática para prevenir la filtración accidental de tokens o credenciales.

---

## 🔒 Buenas Prácticas Recomendadas para Administradores de Servidores

1. **Principio de Mínimo Privilegio:** Asigna el permiso `sovereignseasons.admin` únicamente a administradores de plena confianza; nunca lo otorgues a grupos generales de jugadores por defecto.
2. **Entorno Docker Aislado:** Si utilizas nuestra imagen de Docker (`ghcr.io/consterna/sovereign-seasons`), ejecuta el contenedor con privilegios mínimos y monta volúmenes dedicados en `./docker/data`.
3. **Persistencia Atómica:** Asegúrate de que el servidor cuente con permisos de escritura adecuados en el directorio `plugins/SovereignSeasons-Core` para permitir escrituras y reemplazos atómicos de archivos temporales.
