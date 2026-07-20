'use strict';

// El boton "Delete pad" de Etherpad (core, ver src/templates/pad.html #delete-pad) se muestra
// a TODOS los usuarios conectados al pad -- el servidor solo deja borrar de verdad al autor de
// la primera revision (ver handlePadDelete en PadMessageHandler.ts), pero el boton igual
// aparece para todos y el resto solo ve un aviso al hacer click. En InsightBloom el pad de
// notas es un documento colaborativo OFICIAL de la conferencia (uno solo, compartido por todo
// el evento, ver GetOrCreateEventPadUseCase) -- nadie debe poder borrarlo desde la UI, ni
// siquiera quien resulto ser el primer editor sin querer.
//
// Se oculta con CSS via el hook eejsBlock_customStyles (mecanismo oficial de plugins para
// inyectar contenido en el <head> de pad.html, ver eejs/index.ts), sin tocar el core de
// Etherpad ni depender de un fork.
exports.eejsBlock_customStyles = (hookName, args) => {
  args.content += '<style>#delete-pad{display:none !important;}</style>';
};
