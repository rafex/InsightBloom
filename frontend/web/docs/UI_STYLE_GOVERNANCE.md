# Gobierno de estilos locales

El gate `npm run lint:ui-governance` mantiene dos inventarios ejecutables:

- [`../scripts/scoped-style-classification.json`](../scripts/scoped-style-classification.json): clasifica cada `<style scoped>` como `shell`, `shared-component`, `canonical-component`, `domain-screen`, `visualization` o `embedded-tool`.
- [`../scripts/visual-style-exceptions.json`](../scripts/visual-style-exceptions.json): fija por archivo y conteo las excepciones hex que pertenecen a mapas, ilustraciones de sandbox o documentos de certificados.

El gate falla si aparece un estilo scoped sin categoría, una clasificación obsoleta, un literal hex fuera de las excepciones o cambia el conteo de una excepción sin actualizarla deliberadamente.

Estado del baseline en 2026-07-29:

- 75 estilos scoped clasificados: 32 pantallas, 14 herramientas embebidas, 10 componentes compartidos, 10 visualizaciones, 4 shell y 5 componentes canónicos.
- 80 literales hex fijados en 6 superficies con intención documentada.
- 1 redefinición legacy de selector canónico reportada temporalmente por el gate.

Las excepciones no autorizan crear nuevos estilos locales: los controles comunes deben usar `BaseButton`, `BaseModal`, `FormField`, `ToggleSwitch`, `StatusBadge` y tokens semánticos.
