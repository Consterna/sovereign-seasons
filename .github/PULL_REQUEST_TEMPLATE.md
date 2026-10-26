## 📌 Resumen del Cambio

<!-- Describe de forma concisa qué problema resuelve este Pull Request o qué característica añade. -->

---

## 🧩 Módulos Afectados

- [ ] `sovereign-seasons-core`
- [ ] `sovereign-seasons-pantry`
- [ ] `sovereign-seasons-logistics`
- [ ] `sovereign-seasons-commerce`
- [ ] `sovereign-seasons-resourcepack`
- [ ] Infraestructura / Docker / CI / Documentación

---

## 🏷️ Tipo de Cambio

- [ ] **Corrección de error (fix):** Cambio que soluciona un bug reportado sin romper compatibilidad.
- [ ] **Nueva funcionalidad (feat):** Añade una nueva característica o comando al ecosistema.
- [ ] **Mejora de rendimiento (perf):** Optimización de memoria, cachés o tiempos de CPU.
- [ ] **Refactorización (refactor):** Cambio de código sin modificar comportamiento público.
- [ ] **Documentación (docs):** Actualizaciones en README, CHANGELOG o guías.
- [ ] **Cambio disruptivo (breaking change):** Modificación en la API pública que requiere cambios en módulos dependientes.

---

## 🧪 Pruebas Realizadas

- [ ] Pruebas unitarias ejecutadas localmente con éxito (`./gradlew test`).
- [ ] Pruebas de integración o estrés añadidas para cubrir los nuevos casos.
- [ ] Verificación manual en servidor Paper/Folia (o contenedor Docker).

Detalla los escenarios probados:
<!-- Ejemplo: Verificada la invalidación de caché al mover fogata con pistón en Paper 1.21.4 -->

---

## ✅ Lista de Verificación (Checklist)

- [ ] El código sigue las pautas descritas en [`CONTRIBUTING.md`](CONTRIBUTING.md).
- [ ] La compilación y empaquetado de producción (`./gradlew shadowJar`) se ejecutan sin errores.
- [ ] Se han actualizado las pruebas unitarias pertinentes manteniendo una cobertura completa.
- [ ] Si corresponde, se ha actualizado [`CHANGELOG.md`](CHANGELOG.md) siguiendo Semantic Versioning.
- [ ] No se incluyen firmas de coautoría automatizada ni menciones de herramientas de IA en commits o documentación.
