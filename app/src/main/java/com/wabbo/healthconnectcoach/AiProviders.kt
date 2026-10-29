package com.wabbo.healthconnectcoach

internal fun AiConfiguration.keyFor(p: CoachProvider)=when(p) {CoachProvider.GROQ->groqKey;CoachProvider.MISTRAL->mistralKey;CoachProvider.OPEN_ROUTER->routerKey;CoachProvider.GEMINI->geminiKey}
internal fun AiConfiguration.modelFor(p: CoachProvider)=when(p) {CoachProvider.GROQ->groqModel;CoachProvider.MISTRAL->mistralModel;CoachProvider.OPEN_ROUTER->routerModel;CoachProvider.GEMINI->geminiModel}
internal fun AiConfiguration.withProvider(p: CoachProvider,key: String=keyFor(p),model: String=modelFor(p))=when(p) {
    CoachProvider.GROQ->copy(groqKey=key,groqModel=model)
    CoachProvider.MISTRAL->copy(mistralKey=key,mistralModel=model)
    CoachProvider.OPEN_ROUTER->copy(routerKey=key,routerModel=model)
    CoachProvider.GEMINI->copy(geminiKey=key,geminiModel=model)
}
internal fun CoachProvider.modelHelp()=when(this) {
    CoachProvider.GROQ->"Modell-ID aus console.groq.com/docs/models. Dein Free-Plan und seine Limits gelten; die Modellliste enthält keine verbindlichen Kontopreise."
    CoachProvider.MISTRAL->"Modell-ID aus docs.mistral.ai/models. Verfügbarkeit bedeutet nicht kostenlos: enthaltene Nutzung im Mistral-Studio unter Limits prüfen."
    CoachProvider.OPEN_ROUTER->"Modell-ID aus openrouter.ai/models. Die Gratisliste prüft veröffentlichte Ein-/Ausgabepreise und Tool-Unterstützung."
    CoachProvider.GEMINI->"Modell-ID aus ai.google.dev/gemini-api/docs/models. Verfügbares Gratisbudget im AI-Studio prüfen; die Modell-API liefert keine Preise."
}
