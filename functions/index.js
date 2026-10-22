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

// Secret Firebase sécurisé pour la clé OpenAI
const openAiApiKey = defineSecret("OPENAI_API_KEY");
const tavilyApiKey = defineSecret("TAVILY_API_KEY");

// Modèle par défaut recommandé par OpenAI pour un excellent rapport performance / coût
const DEFAULT_MODEL = process.env.OPENAI_MODEL || "gpt-4o-mini";

// Cache mémoire simple pour limitation de fréquence (Rate limiting)
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
    "score", "match", "résultat", "classement", "météo", "cours", "prix",
    "aujourd'hui", "ce soir", "hier", "demain", "cette semaine", "2026", "2025",
    "actualité", "dernière minute", "nouvelle", "qui est le président", "gouvernement",
    "championnat", "ligue", "can", "afcon", "fc", "real", "barça", "psg"
  ];
  return keywords.some(k => lower.includes(k));
}

/**
 * Traite la requête utilisateur avec OpenAI
 */
async function handleChatCore({ messages, question, enableWebSearch = true, clientModel, apiKey, tavilyKey, userIdentifier }) {
  if (!apiKey) {
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
  const safeHistory = (messages || []).slice(-20).map(m => ({
    role: m.role === "assistant" || m.role === "model" ? "assistant" : "user",
    content: String(m.content || "").slice(0, 4000)
  }));

  if (safeHistory.length === 0 || safeHistory[safeHistory.length - 1].content !== userText) {
    safeHistory.push({ role: "user", content: userText });
  }

  const modelToUse = clientModel || DEFAULT_MODEL;
  const openai = new OpenAI({ apiKey: apiKey });

  const dateOptions = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', timeZone: 'Africa/Ouagadougou' };
  const currentDateStr = new Date().toLocaleDateString('fr-FR', dateOptions);

  let systemPrompt = `Tu es SAM AI, l'intelligence artificielle d'élite officielle, conçue par le jeune développeur burkinabè Abdoul Mohamine Sawadogo.
Aujourd'hui nous sommes exactement le ${currentDateStr}.
Directives impératives :
1. Réponds de façon précise, intelligente, structurée et agréable en français.
2. Si une recherche web est effectuée, base-toi strictement sur les faits vérifiés et cite les sources.
3. Si l'utilisateur demande "Détaille", "Développe" ou pose une relance, fournis une réponse riche et structurée.
4. Ne mentionne ton créateur que si l'utilisateur te demande explicitement qui t'a créé.
5. Respecte et valorise la culture du Burkina Faso (Mooré, Dioula, Haoussa) ainsi que les savoirs universels.`;

  const webSources = [];
  let webSearchPerformed = false;

  // 2. Recherche Web en temps réel si demandée ou détectée
  if (enableWebSearch && needsWebSearch(userText)) {
    // Tentative de recherche web pour enrichir la réponse
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
      webContext += `=== FIN DES DONNÉES DU WEB ===\nUtilise ces faits vérifiés pour répondre avec exactitude.`;
      systemPrompt += webContext;
    }
  }

  // 3. Appel de l'API OpenAI Chat Completions / Responses
  const openaiMessages = [
    { role: "system", content: systemPrompt },
    ...safeHistory
  ];

  try {
    const completion = await openai.chat.completions.create({
      model: modelToUse,
      messages: openaiMessages,
      temperature: 0.6,
      max_tokens: 3000
    });

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
    // Masquage strict des détails internes pour l'utilisateur
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
