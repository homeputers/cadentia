package com.cadentia.bot.telegram;

import java.util.Locale;

/** Small, explicit catalog for Telegram copy. Unknown locales intentionally fall back to English. */
final class TelegramI18n {
    private TelegramI18n() {}

    static Locale locale(String configuredLocale) {
        if (configuredLocale == null || configuredLocale.isBlank()) {
            return Locale.US;
        }
        Locale parsed = Locale.forLanguageTag(configuredLocale.replace('_', '-'));
        return parsed.getLanguage().isBlank() ? Locale.US : parsed;
    }

    static String text(String key, Locale locale) {
        String language = locale == null ? "en" : locale.getLanguage().toLowerCase(Locale.ROOT);
        if ("es".equals(language)) {
            return spanish(key);
        }
        if ("pt".equals(language)) {
            return portuguese(key);
        }
        return english(key);
    }

    static String format(String key, Locale locale, Object... arguments) {
        return text(key, locale).formatted(arguments);
    }

    private static String english(String key) {
        return switch (key) {
            case "welcome" -> "Welcome to Cadentia.";
            case "help" -> "Use /newsetlist to begin.";
            case "newSetlist" -> "New setlist flow started.";
            case "cancelled" -> "Session cancelled.";
            case "settings" -> "Settings opened.";
            case "confirmNotReady" -> "Session is not ready to confirm. Reply with scripture, theme, or setlist details first.";
            case "unsupportedSelection" -> "Unsupported guided menu selection.";
            case "setlistConfirmed" -> "Setlist confirmed.";
            case "proposalGenerated" -> "Deterministic setlist proposal generated. %s";
            case "sessionState" -> "Session state: %s.";
            case "recommendation" -> " Recommendation: %s.";
            case "setlist" -> " Setlist: %s.";
            case "version" -> " Version: %s.";
            case "authorized" -> "Authorized.";
            case "linkAccount" -> "Please link your Cadentia account before using this bot.";
            case "accessRevoked" -> "Telegram access has been revoked. Please contact your Cadentia administrator.";
            case "accessDisabled" -> "Telegram access is disabled for this church instance.";
            case "notAuthorizedInstance" -> "This Telegram account is not authorized for this church instance.";
            case "notAuthorizedAction" -> "Your Cadentia role cannot perform this action from Telegram.";
            case "notAuthorized" -> "This Telegram account is not authorized for that action.";
            case "cadentia" -> "Cadentia";
            case "cadentiaUpdate" -> "Cadentia update";
            case "cancelledHeading" -> "Cancelled";
            case "setlistReady" -> "Setlist ready";
            case "accessNeeded" -> "Access needed";
            case "unavailable" -> "Unavailable";
            case "couldNotProcess" -> "Could not process that Telegram update";
            case "unsupportedAction" -> "Unsupported Telegram action";
            case "expiredAction" -> "Expired action";
            case "retryHelp" -> "Please retry or use /help.";
            case "useHelp" -> "Use /help for supported commands.";
            case "buttonInactive" -> "That button is no longer active. Use /status or /newsetlist.";
            case "useNewSetlist" -> "You can start again with /newsetlist.";
            case "openReview" -> "Open Cadentia to review approved references before publishing.";
            case "selectedSoFar" -> "Selected so far:";
            case "sendScripture" -> "Send your Scripture focus or use /newsetlist.";
            case "structure" -> "Structure";
            case "language" -> "Language";
            case "energyArc" -> "Energy arc";
            case "serviceMoment" -> "Service moment";
            case "keyPolicy" -> "Key policy";
            case "tempoPolicy" -> "Tempo policy";
            case "praise" -> "praise";
            case "worship" -> "worship";
            case "tightKeys" -> "Tight keys";
            case "sameKey" -> "Same key";
            case "flexibleKeys" -> "Flexible keys";
            case "tightTempo" -> "Tight tempo";
            case "smoothTempo" -> "Smooth tempo";
            case "openTempo" -> "Open tempo";
            case "english" -> "English";
            case "spanish" -> "Spanish";
            case "portuguese" -> "Portuguese";
            case "rising" -> "Rising";
            case "steady" -> "Steady";
            case "falling" -> "Falling";
            case "opening" -> "Opening";
            case "response" -> "Response";
            case "sending" -> "Sending";
            case "confirm" -> "Confirm";
            case "revise" -> "Revise";
            case "cancel" -> "Cancel";
            case "proposal" -> "Setlist proposal";
            case "noApprovedSongs" -> "No approved songs were returned yet.";
            case "result" -> "Result: <code>%s</code>\n";
            case "setlistId" -> "Setlist: <code>%s</code>\n";
            case "versionId" -> "Version: <code>%s</code>\n";
            case "recommendedSongs" -> "\n<b>Recommended songs</b>\n";
            case "reviewProposal" -> "\nReview the proposal in Cadentia before publishing. Only approved catalog evidence is shown.";
            case "notes" -> "\n<b>Notes</b>\n";
            case "ref" -> "   • ref: <code>%s</code>\n";
            case "approvedSelection" -> "Approved selection %s";
            case "approvedSelectionGeneric" -> "Approved selection";
            case "received" -> "Received.";
            case "proposalAck" -> "Proposal generated.";
            case "cancelledAck" -> "Cancelled.";
            case "expiredAck" -> "That action expired.";
            case "unauthorizedAck" -> "Not authorized.";
            default -> key;
        };
    }

    private static String spanish(String key) {
        return switch (key) {
            case "welcome" -> "Bienvenido a Cadentia.";
            case "help" -> "Usa /newsetlist para comenzar.";
            case "newSetlist" -> "Se inició el flujo de una nueva lista.";
            case "cancelled" -> "Sesión cancelada.";
            case "settings" -> "Configuración abierta.";
            case "confirmNotReady" -> "La sesión aún no está lista para confirmar. Responde con la Escritura, el tema o los detalles de la lista.";
            case "unsupportedSelection" -> "Selección guiada no compatible.";
            case "setlistConfirmed" -> "Lista confirmada.";
            case "proposalGenerated" -> "Se generó una propuesta determinista de lista. %s";
            case "sessionState" -> "Estado de la sesión: %s.";
            case "recommendation" -> " Recomendación: %s.";
            case "setlist" -> " Lista: %s.";
            case "version" -> " Versión: %s.";
            case "authorized" -> "Autorizado.";
            case "linkAccount" -> "Vincula tu cuenta de Cadentia antes de usar este bot.";
            case "accessRevoked" -> "El acceso de Telegram fue revocado. Contacta al administrador de Cadentia.";
            case "accessDisabled" -> "El acceso de Telegram está deshabilitado para esta instancia de iglesia.";
            case "notAuthorizedInstance" -> "Esta cuenta de Telegram no está autorizada para esta instancia de iglesia.";
            case "notAuthorizedAction" -> "Tu rol de Cadentia no puede realizar esta acción desde Telegram.";
            case "notAuthorized" -> "Esta cuenta de Telegram no está autorizada para esa acción.";
            case "cadentia" -> "Cadentia";
            case "cadentiaUpdate" -> "Actualización de Cadentia";
            case "cancelledHeading" -> "Cancelada";
            case "setlistReady" -> "Lista lista";
            case "accessNeeded" -> "Se necesita acceso";
            case "unavailable" -> "No disponible";
            case "couldNotProcess" -> "No se pudo procesar esa actualización de Telegram";
            case "unsupportedAction" -> "Acción de Telegram no compatible";
            case "expiredAction" -> "Acción vencida";
            case "retryHelp" -> "Inténtalo de nuevo o usa /help.";
            case "useHelp" -> "Usa /help para ver los comandos compatibles.";
            case "buttonInactive" -> "Ese botón ya no está activo. Usa /status o /newsetlist.";
            case "useNewSetlist" -> "Puedes comenzar de nuevo con /newsetlist.";
            case "openReview" -> "Abre Cadentia para revisar las referencias aprobadas antes de publicar.";
            case "selectedSoFar" -> "Seleccionado hasta ahora:";
            case "sendScripture" -> "Envía tu enfoque bíblico o usa /newsetlist.";
            case "structure" -> "Estructura";
            case "language" -> "Idioma";
            case "energyArc" -> "Arco de energía";
            case "serviceMoment" -> "Momento del servicio";
            case "keyPolicy" -> "Política de tonalidad";
            case "tempoPolicy" -> "Política de tempo";
            case "praise" -> "alabanza";
            case "worship" -> "adoración";
            case "tightKeys" -> "Tonalidades cercanas";
            case "sameKey" -> "Misma tonalidad";
            case "flexibleKeys" -> "Tonalidades flexibles";
            case "tightTempo" -> "Tempo ajustado";
            case "smoothTempo" -> "Tempo fluido";
            case "openTempo" -> "Tempo abierto";
            case "english" -> "Inglés";
            case "spanish" -> "Español";
            case "portuguese" -> "Portugués";
            case "rising" -> "Ascendente";
            case "steady" -> "Constante";
            case "falling" -> "Descendente";
            case "opening" -> "Apertura";
            case "response" -> "Respuesta";
            case "sending" -> "Envío";
            case "confirm" -> "Confirmar";
            case "revise" -> "Revisar";
            case "cancel" -> "Cancelar";
            case "proposal" -> "Propuesta de lista";
            case "noApprovedSongs" -> "Aún no se devolvieron canciones aprobadas.";
            case "result" -> "Resultado: <code>%s</code>\n";
            case "setlistId" -> "Lista: <code>%s</code>\n";
            case "versionId" -> "Versión: <code>%s</code>\n";
            case "recommendedSongs" -> "\n<b>Canciones recomendadas</b>\n";
            case "reviewProposal" -> "\nRevisa la propuesta en Cadentia antes de publicar. Solo se muestran referencias aprobadas del catálogo.";
            case "notes" -> "\n<b>Notas</b>\n";
            case "ref" -> "   • ref: <code>%s</code>\n";
            case "approvedSelection" -> "Selección aprobada %s";
            case "approvedSelectionGeneric" -> "Selección aprobada";
            case "received" -> "Recibido.";
            case "proposalAck" -> "Propuesta generada.";
            case "cancelledAck" -> "Cancelada.";
            case "expiredAck" -> "Esa acción venció.";
            case "unauthorizedAck" -> "No autorizado.";
            default -> english(key);
        };
    }

    private static String portuguese(String key) {
        return switch (key) {
            case "welcome" -> "Boas-vindas ao Cadentia.";
            case "help" -> "Use /newsetlist para começar.";
            case "newSetlist" -> "O fluxo de uma nova lista foi iniciado.";
            case "cancelled" -> "Sessão cancelada.";
            case "settings" -> "Configurações abertas.";
            case "confirmNotReady" -> "A sessão ainda não está pronta para confirmar. Responda com a Escritura, o tema ou detalhes da lista.";
            case "unsupportedSelection" -> "Seleção guiada não compatível.";
            case "setlistConfirmed" -> "Lista confirmada.";
            case "proposalGenerated" -> "Proposta determinística de lista gerada. %s";
            case "sessionState" -> "Estado da sessão: %s.";
            case "recommendation" -> " Recomendação: %s.";
            case "setlist" -> " Lista: %s.";
            case "version" -> " Versão: %s.";
            case "authorized" -> "Autorizado.";
            case "linkAccount" -> "Vincule sua conta Cadentia antes de usar este bot.";
            case "accessRevoked" -> "O acesso do Telegram foi revogado. Fale com o administrador do Cadentia.";
            case "accessDisabled" -> "O acesso do Telegram está desativado para esta instância da igreja.";
            case "notAuthorizedInstance" -> "Esta conta do Telegram não está autorizada para esta instância da igreja.";
            case "notAuthorizedAction" -> "Seu papel no Cadentia não pode realizar esta ação pelo Telegram.";
            case "notAuthorized" -> "Esta conta do Telegram não está autorizada para essa ação.";
            case "cadentia" -> "Cadentia";
            case "cadentiaUpdate" -> "Atualização do Cadentia";
            case "cancelledHeading" -> "Cancelada";
            case "setlistReady" -> "Lista pronta";
            case "accessNeeded" -> "Acesso necessário";
            case "unavailable" -> "Indisponível";
            case "couldNotProcess" -> "Não foi possível processar essa atualização do Telegram";
            case "unsupportedAction" -> "Ação do Telegram não compatível";
            case "expiredAction" -> "Ação expirada";
            case "retryHelp" -> "Tente novamente ou use /help.";
            case "useHelp" -> "Use /help para ver os comandos compatíveis.";
            case "buttonInactive" -> "Esse botão não está mais ativo. Use /status ou /newsetlist.";
            case "useNewSetlist" -> "Você pode começar novamente com /newsetlist.";
            case "openReview" -> "Abra o Cadentia para revisar as referências aprovadas antes de publicar.";
            case "selectedSoFar" -> "Selecionado até agora:";
            case "sendScripture" -> "Envie seu foco bíblico ou use /newsetlist.";
            case "structure" -> "Estrutura";
            case "language" -> "Idioma";
            case "energyArc" -> "Arco de energia";
            case "serviceMoment" -> "Momento do culto";
            case "keyPolicy" -> "Política de tonalidade";
            case "tempoPolicy" -> "Política de tempo";
            case "praise" -> "louvor";
            case "worship" -> "adoração";
            case "tightKeys" -> "Tonalidades próximas";
            case "sameKey" -> "Mesma tonalidade";
            case "flexibleKeys" -> "Tonalidades flexíveis";
            case "tightTempo" -> "Tempo ajustado";
            case "smoothTempo" -> "Tempo fluido";
            case "openTempo" -> "Tempo aberto";
            case "english" -> "Inglês";
            case "spanish" -> "Espanhol";
            case "portuguese" -> "Português";
            case "rising" -> "Crescente";
            case "steady" -> "Constante";
            case "falling" -> "Descendente";
            case "opening" -> "Abertura";
            case "response" -> "Resposta";
            case "sending" -> "Envio";
            case "confirm" -> "Confirmar";
            case "revise" -> "Revisar";
            case "cancel" -> "Cancelar";
            case "proposal" -> "Proposta de lista";
            case "noApprovedSongs" -> "Nenhuma música aprovada foi retornada ainda.";
            case "result" -> "Resultado: <code>%s</code>\n";
            case "setlistId" -> "Lista: <code>%s</code>\n";
            case "versionId" -> "Versão: <code>%s</code>\n";
            case "recommendedSongs" -> "\n<b>Músicas recomendadas</b>\n";
            case "reviewProposal" -> "\nRevise a proposta no Cadentia antes de publicar. Somente referências aprovadas do catálogo são exibidas.";
            case "notes" -> "\n<b>Notas</b>\n";
            case "ref" -> "   • ref: <code>%s</code>\n";
            case "approvedSelection" -> "Seleção aprovada %s";
            case "approvedSelectionGeneric" -> "Seleção aprovada";
            case "received" -> "Recebido.";
            case "proposalAck" -> "Proposta gerada.";
            case "cancelledAck" -> "Cancelada.";
            case "expiredAck" -> "Essa ação expirou.";
            case "unauthorizedAck" -> "Não autorizado.";
            default -> english(key);
        };
    }
}
