/**
 * Firebase Cloud Functions pour Sam AI
 * Intégration officielle de l'API OpenAI (modèle configurable, recherche Web et sécurité)
 */

const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const OpenAI = require("openai");

// Initialisation de Firebase Admin
if (!admin.apps.length) {
  admin.initializeApp();
}

// Secret Firebase sécurisé pour la clé OpenAI et recherche Web
const openAiApiKey = defineSecret("OPENAI_API_KEY");
const tavilyApiKey = defineSecret("TAVILY_API_KEY");

// Liste des familles et modèles officiels OpenAI compatibles Chat & Responses
const COMPATIBLE_CHAT_MODELS = [
  "gpt-4o",
  "chatgpt-4o-latest",
  "o3-mini",
  "o1",
  "o1-mini",
  "o1-preview",
  "gpt-4-turbo",
  "gpt-4",
  "gpt-4o-mini",
  "gpt-3.5-turbo"
];

/**
 * Résout et valide le modèle OpenAI à utiliser.
 * Priorise la variable d'environnement OPENAI_MODEL du serveur sans l'écraser arbitrairement.
 * Modèle phare par défaut : "gpt-4o" (modèle le plus puissant avec vision et outils).
 */
function resolveAndValidateModel(clientModel) {
  const envModel = (process.env.OPENAI_MODEL || "").trim();
  const rawModel = envModel || (clientModel || "").trim() || "gpt-4o";

  const isCompatible = COMPATIBLE_CHAT_MODELS.some(
    (m) => rawModel === m || rawModel.startsWith(`${m}-`) || rawModel.startsWith(m)
  ) || rawModel.startsWith("gpt-") || rawModel.startsWith("o1") || rawModel.startsWith("o3");

  if (!isCompatible) {
    console.warn(
      `[SAM AI] Attention : Le modèle "${rawModel}" n'est pas dans la liste des modèles officiels vérifiés. Utilisation du modèle de référence "gpt-4o".`
    );
    return "gpt-4o";
  }

  return rawModel;
}

// Rate limiting : Limitation du nombre de requêtes par fenêtre temporelle
const requestRateMap = new Map();
const RATE_LIMIT_WINDOW_MS = 60 * 1000; // 1 minute
const MAX_REQUESTS_PER_WINDOW = 30; // Max 30 requêtes par minute par utilisateur/IP

function checkRateLimit(identifier) {
  const now = Date.now();
  const entry = requestRateMap.get(identifier) || { count: 0, resetAt: now + RATE_LIMIT_WINDOW_MS };
  if (now > entry.resetAt) {
    entry.count = 1;
    entry.resetAt = now + RATE_LIMIT_WINDOW_MS;
  } else {
    entry.count += 1;
  }
  requestRateMap.set(identifier, entry);
  return entry.count <= MAX_REQUESTS_PER_WINDOW;
}

// Nettoyage périodique du rate limiter
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of requestRateMap.entries()) {
    if (now > value.resetAt) {
      requestRateMap.delete(key);
    }
  }
}, 5 * 60 * 1000);

/**
 * Effectue une recherche web via Tavily si nécessaire pour enrichir le contexte
 */
async function performWebSearchIfAvailable(query, apiKey) {
  if (!apiKey || apiKey.startsWith("tvly-your")) return null;
  try {
    const response = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        query: query,
        max_results: 5,
        search_depth: "basic",
        include_answer: true
      })
    });
    if (!response.ok) return null;
    const data = await response.json();
    return data;
  } catch (err) {
    console.warn("Échec recherche web Tavily:", err.message);
    return null;
  }
}

/**
 * Détermine si la question de l'utilisateur a besoin d'une recherche web en temps réel
 */
function needsWebSearch(text) {
  if (!text) return false;
  const lower = text.toLowerCase();
  const keywords = [
    "score", "match", "résultat", "resultat", "classement", "météo", "meteo",
    "cours", "prix", "aujourd'hui", "ce soir", "hier", "demain", "cette semaine",
    "2026", "2025", "actualité", "actualite", "dernière minute", "nouvelle",
    "qui est le président", "gouvernement", "championnat", "ligue", "can",
    "afcon", "fc", "real", "barça", "barca", "psg"
  ];
  return keywords.some((k) => lower.includes(k));
}

/**
 * Traite la requête utilisateur avec OpenAI
 */
async function handleChatCore({ messages, question, enableWebSearch = true, clientModel, apiKey, tavilyKey, userIdentifier }) {
  if (!apiKey || apiKey.includes("MY_OPENAI_API_KEY")) {
    throw new Error("La clé OPENAI_API_KEY n'est pas configurée dans Firebase Secrets.");
  }

  // 1. Validation de la taille et de la structure des messages
  const userText = (question || (messages && messages.length > 0 ? messages[messages.length - 1].content : "") || "").trim();
  if (!userText) {
    throw new Error("Le message envoyé est vide.");
  }

  if (userText.length > 4000) {
    throw new Error("Le message dépasse la taille maximale autorisée (4 000 caractères).");
  }

  // Limitation à 20 messages d'historique maximum
  const safeHistory = (messages || []).slice(-20).map((m) => ({
    role: m.role === "assistant" || m.role === "model" ? "assistant" : "user",
    content: String(m.content || "").slice(0, 4000)
  }));

  if (safeHistory.length === 0 || safeHistory[safeHistory.length - 1].content !== userText) {
    safeHistory.push({ role: "user", content: userText });
  }

  const modelToUse = resolveAndValidateModel(clientModel);
  const openai = new OpenAI({ apiKey: apiKey });

  const dateOptions = { weekday: "long", year: "numeric", month: "long", day: "numeric", timeZone: "Africa/Ouagadougou" };
  const currentDateStr = new Date().toLocaleDateString("fr-FR", dateOptions);

  let systemPrompt = `Tu es SAM AI, l'assistant intelligent d'élite propulsé par OpenAI, conçu par le jeune développeur burkinabè Abdoul Mohamine Sawadogo.
Aujourd'hui nous sommes exactement le ${currentDateStr}.

Directives impératives de réponse :
1. RÉPONSE DIRECTE ET IMMÉDIATE : Réponds directement à la question dès la toute première phrase. Ne commence JAMAIS par des formules creuses, robotiques ou répétitives (bannir formellement : "Merci pour votre question : ...", "En tant qu'assistant intelligent SAM AI...", "Voici les éléments clés à retenir :", etc.).
2. DÉVELOPPEMENT UTILE ET APPROFONDI : Si l'utilisateur demande "Détaille", "Développe", "Peux-tu approfondir ta réponse" ou pose une relance, apporte une explication riche, concrète, avec des exemples pratiques, des données précises et les étapes concrètes, sans te limiter à des généralités abstraites.
3. TRANSPARENCE ET GESTION DES INCERTITUDES : Explique clairement les points importants. Si une information est incertaine, récente ou non vérifiée, signale expressément l'incertitude plutôt que d'affirmer un fait douteux.
4. VÉRACITÉ DES SOURCES : Quand une recherche web est effectuée, base-toi strictement sur les faits vérifiés et cite les sources réelles. N'invente aucun lien ni aucune source inexistante.
5. IDENTITÉ ET CULTURE : Ne mentionne ton créateur Abdoul Mohamine Sawadogo que si l'utilisateur te demande expressément qui t'a créé ou qui tu es. Sois respectueux, bienveillant, et valorise le savoir burkinabè, africain et universel en français fluide et élégant.`;

  const webSources = [];
  let webSearchPerformed = false;

  // 2. Recherche Web en direct si activée et pertinente
  const shouldSearchWeb = enableWebSearch && needsWebSearch(userText);

  // OPTION A : Tentative via la nouvelle API Responses officielle d'OpenAI avec l'outil officiel web_search_preview
  const isReasoningModel = modelToUse.startsWith("o1") || modelToUse.startsWith("o3");
  if (shouldSearchWeb && typeof openai.responses?.create === "function" && !isReasoningModel) {
    try {
      const responseResult = await openai.responses.create({
        model: modelToUse,
        input: [
          { role: "system", content: systemPrompt },
          ...safeHistory
        ],
        tools: [{ type: "web_search_preview" }]
      });

      let textOutput = responseResult.output_text || "";
      if (!textOutput && responseResult.output) {
        for (const item of responseResult.output) {
          if (item.type === "message" && item.content) {
            for (const c of item.content) {
              if (c.type === "output_text") {
                textOutput += c.text || "";
                if (Array.isArray(c.annotations)) {
                  c.annotations.forEach((ann) => {
                    if (ann.type === "url_citation" && ann.url) {
                      try {
                        const domain = new URL(ann.url).hostname.replace(/^www\./, "");
                        webSources.push({
                          title: ann.title || domain,
                          url: ann.url,
                          domain: domain
                        });
                      } catch (_) {
                        webSources.push({
                          title: ann.title || "Source Web",
                          url: ann.url,
                          domain: "web"
                        });
                      }
                    }
                  });
                }
              }
            }
          }
        }
      }

      if (textOutput && textOutput.trim().length > 0) {
        return {
          success: true,
          text: textOutput.trim(),
          sources: webSources,
          model: modelToUse,
          searchPerformed: webSources.length > 0
        };
      }
    } catch (responsesErr) {
      console.warn("[SAM AI] OpenAI Responses API web_search_preview non supporté ou indisponible, bascule sur Chat Completions:", responsesErr.message);
    }
  }

  // OPTION B : Chat Completions standard OpenAI avec enrichissement web vérifié
  if (shouldSearchWeb) {
    const searchData = await performWebSearchIfAvailable(userText, tavilyKey);
    if (searchData && searchData.results && searchData.results.length > 0) {
      webSearchPerformed = true;
      let webContext = `\n\n=== INFORMATIONS VÉRIFIÉES DU WEB EN DIRECT ===\n`;
      if (searchData.answer) {
        webContext += `Synthèse Web : ${searchData.answer}\n`;
      }
      searchData.results.forEach((r, idx) => {
        webContext += `[Source ${idx + 1}] ${r.title || "Page Web"} : ${r.url}\n${r.content}\n`;
        try {
          const domain = new URL(r.url).hostname.replace(/^www\./, "");
          webSources.push({
            title: r.title || domain,
            url: r.url,
            domain: domain
          });
        } catch (_) {
          webSources.push({
            title: r.title || "Lien Web",
            url: r.url,
            domain: "web"
          });
        }
      });
      webContext += `=== FIN DES DONNÉES DU WEB ===\nConsigne : Utilise ces faits vérifiés pour répondre avec exactitude et clarté.`;
      systemPrompt += webContext;
    }
  }

  const openaiMessages = [
    { role: isReasoningModel ? "developer" : "system", content: systemPrompt },
    ...safeHistory
  ];

  try {
    const requestParams = {
      model: modelToUse,
      messages: openaiMessages
    };

    if (isReasoningModel) {
      requestParams.max_completion_tokens = 3000;
    } else {
      requestParams.temperature = 0.6;
      requestParams.max_tokens = 3000;
    }

    const completion = await openai.chat.completions.create(requestParams);

    const choice = completion.choices && completion.choices[0];
    const textOutput = (choice && choice.message && choice.message.content) ? choice.message.content.trim() : "";

    if (!textOutput) {
      throw new Error("Réponse vide renvoyée par le modèle OpenAI.");
    }

    return {
      success: true,
      text: textOutput,
      sources: webSources,
      model: modelToUse,
      searchPerformed: webSearchPerformed
    };
  } catch (apiErr) {
    console.error("Erreur OpenAI API:", apiErr.message || apiErr);
    let userMsg = "Une erreur est survenue lors de la communication avec OpenAI.";
    if (apiErr.status === 401) {
      userMsg = "Clé API OpenAI non valide. Veuillez vérifier votre configuration dans Firebase Secrets.";
    } else if (apiErr.status === 429) {
      userMsg = "Limite de requêtes OpenAI atteinte ou solde insuffisant sur votre compte OpenAI.";
    } else if (apiErr.status === 500 || apiErr.status === 503) {
      userMsg = "Les serveurs d'OpenAI rencontrent actuellement une surcharge temporaire. Veuillez réessayer.";
    }
    return {
      success: false,
      text: "",
      errorMessage: userMsg,
      sources: []
    };
  }
}

/**
 * 1. Cloud Function Callable officielle pour le SDK Android Firebase Functions
 */
exports.samAiChat = onCall(
  {
    secrets: [openAiApiKey, tavilyApiKey],
    cors: true,
    maxInstances: 10
  },
  async (request) => {
    const userIdentifier = (request.auth && request.auth.uid) ? request.auth.uid : (request.rawRequest ? request.rawRequest.ip : "anonymous_user");

    // Protection anti-abus (Rate limiting)
    if (!checkRateLimit(userIdentifier)) {
      throw new HttpsError(
        "resource-exhausted",
        "Trop de requêtes en peu de temps. Veuillez patienter une minute avant de renvoyer un message."
      );
    }

    const { messages, question, enableWebSearch, model } = request.data || {};
    const effectiveApiKey = openAiApiKey.value();
    const effectiveTavilyKey = tavilyApiKey.value();

    try {
      const result = await handleChatCore({
        messages,
        question,
        enableWebSearch,
        clientModel: model,
        apiKey: effectiveApiKey,
        tavilyKey: effectiveTavilyKey,
        userIdentifier
      });

      if (!result.success) {
        throw new HttpsError("internal", result.errorMessage || "Erreur interne OpenAI");
      }

      return result;
    } catch (err) {
      if (err instanceof HttpsError) throw err;
      throw new HttpsError("internal", err.message || "Erreur serveur");
    }
  }
);

/**
 * 2. Cloud Function HTTPS REST endpoint direct (pour clients REST ou fallback réseau)
 */
exports.apiSamAiChat = onRequest(
  {
    secrets: [openAiApiKey, tavilyApiKey],
    cors: true,
    maxInstances: 10
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Méthode non autorisée. Seul POST est accepté." });
      return;
    }

    const ip = req.headers["x-forwarded-for"] || req.socket.remoteAddress || "ip_unknown";
    if (!checkRateLimit(ip)) {
      res.status(429).json({ error: "Trop de requêtes. Veuillez patienter une minute." });
      return;
    }

    const { messages, question, enableWebSearch, model } = req.body || {};
    const effectiveApiKey = openAiApiKey.value();
    const effectiveTavilyKey = tavilyApiKey.value();

    try {
      const result = await handleChatCore({
        messages,
        question,
        enableWebSearch,
        clientModel: model,
        apiKey: effectiveApiKey,
        tavilyKey: effectiveTavilyKey,
        userIdentifier: ip
      });

      if (!result.success) {
        res.status(500).json(result);
        return;
      }

      res.status(200).json(result);
    } catch (err) {
      res.status(500).json({
        success: false,
        text: "",
        errorMessage: err.message || "Erreur lors du traitement OpenAI.",
        sources: []
      });
    }
  }
);

/**
 * 3. Cloud Function Callable de diagnostic et vérification du modèle OpenAI
 * Permet de vérifier sans risque si la clé API a bien accès au modèle configuré
 * et de lister les modèles d'élite disponibles sur le compte OpenAI.
 */
exports.checkSamAiModel = onCall(
  {
    secrets: [openAiApiKey],
    cors: true,
    maxInstances: 5
  },
  async () => {
    const effectiveApiKey = openAiApiKey.value();
    const currentConfigured = (process.env.OPENAI_MODEL || "gpt-4o").trim();

    if (!effectiveApiKey || effectiveApiKey.includes("MY_OPENAI_API_KEY")) {
      return {
        configuredModel: currentConfigured,
        isConfiguredAccessible: false,
        error: "OPENAI_API_KEY n'est pas encore définie dans Firebase Secrets."
      };
    }

    try {
      const openai = new OpenAI({ apiKey: effectiveApiKey });
      const modelsPage = await openai.models.list();
      const modelIds = (modelsPage.data || []).map((m) => m.id);

      const isConfiguredAccessible = modelIds.includes(currentConfigured);
      const hasGpt4o = modelIds.some((id) => id === "gpt-4o");
      const hasO3Mini = modelIds.some((id) => id.startsWith("o3-mini"));
      const hasO1 = modelIds.some((id) => id === "o1" || id === "o1-2024-12-17");
      const hasChatGpt4oLatest = modelIds.some((id) => id === "chatgpt-4o-latest");

      return {
        configuredModel: currentConfigured,
        isConfiguredAccessible: isConfiguredAccessible,
        totalModelsFound: modelIds.length,
        hasGpt4o,
        hasO3Mini,
        hasO1,
        hasChatGpt4oLatest,
        status: isConfiguredAccessible ? "VERIFIED_ACCESSIBLE" : "MODEL_NOT_FOUND_ON_ACCOUNT"
      };
    } catch (err) {
      return {
        configuredModel: currentConfigured,
        isConfiguredAccessible: false,
        error: err.message || "Impossible de contacter l'API OpenAI pour vérifier les modèles."
      };
    }
  }
);

/**
 * 4. Endpoint HTTPS REST de diagnostic
 */
exports.apiCheckSamAiModel = onRequest(
  {
    secrets: [openAiApiKey],
    cors: true,
    maxInstances: 5
  },
  async (req, res) => {
    const effectiveApiKey = openAiApiKey.value();
    const currentConfigured = (process.env.OPENAI_MODEL || "gpt-4o").trim();

    if (!effectiveApiKey || effectiveApiKey.includes("MY_OPENAI_API_KEY")) {
      res.status(200).json({
        configuredModel: currentConfigured,
        isConfiguredAccessible: false,
        error: "OPENAI_API_KEY n'est pas encore définie dans Firebase Secrets."
      });
      return;
    }

    try {
      const openai = new OpenAI({ apiKey: effectiveApiKey });
      const modelsPage = await openai.models.list();
      const modelIds = (modelsPage.data || []).map((m) => m.id);

      const isConfiguredAccessible = modelIds.includes(currentConfigured);
      const hasGpt4o = modelIds.some((id) => id === "gpt-4o");
      const hasO3Mini = modelIds.some((id) => id.startsWith("o3-mini"));
      const hasO1 = modelIds.some((id) => id === "o1" || id === "o1-2024-12-17");

      res.status(200).json({
        configuredModel: currentConfigured,
        isConfiguredAccessible: isConfiguredAccessible,
        totalModelsFound: modelIds.length,
        hasGpt4o,
        hasO3Mini,
        hasO1,
        status: isConfiguredAccessible ? "VERIFIED_ACCESSIBLE" : "MODEL_NOT_FOUND_ON_ACCOUNT"
      });
    } catch (err) {
      res.status(500).json({
        configuredModel: currentConfigured,
        isConfiguredAccessible: false,
        error: err.message || "Erreur vérification modèles."
      });
    }
  }
);

