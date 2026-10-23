/**
 * Firebase Cloud Functions pour Sam AI
 * Intégration officielle et directe de l'API OpenAI
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

// Liste des familles et modèles officiels OpenAI compatibles Chat
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
 * Priorise la variable d'environnement OPENAI_MODEL du serveur.
 */
function resolveAndValidateModel(clientModel) {
  const envModel = (process.env.OPENAI_MODEL || "").trim();
  const rawModel = envModel || (clientModel || "").trim() || "gpt-4o";

  const isCompatible = COMPATIBLE_CHAT_MODELS.some(
    (m) => rawModel === m || rawModel.startsWith(`${m}-`) || rawModel.startsWith(m)
  ) || rawModel.startsWith("gpt-") || rawModel.startsWith("o1") || rawModel.startsWith("o3");

  if (!isCompatible) {
    console.warn(`[SAM AI] Modèle "${rawModel}" non vérifié, utilisation de "gpt-4o".`);
    return "gpt-4o";
  }

  return rawModel;
}

// Rate limiting
const requestRateMap = new Map();
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 30;

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

setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of requestRateMap.entries()) {
    if (now > entry.resetAt) {
      requestRateMap.delete(key);
    }
  }
}, 5 * 60 * 1000);

/**
 * Traite la requête utilisateur directement avec OpenAI
 */
async function handleChatCore({ messages, question, clientModel, apiKey }) {
  if (!apiKey || apiKey.includes("MY_OPENAI_API_KEY")) {
    throw new Error("La clé OPENAI_API_KEY n'est pas configurée dans Firebase Secrets.");
  }

  const userText = (question || (messages && messages.length > 0 ? messages[messages.length - 1].content : "") || "").trim();
  if (!userText) {
    throw new Error("Le message envoyé est vide.");
  }

  const modelToUse = resolveAndValidateModel(clientModel);
  const openai = new OpenAI({ apiKey: apiKey });

  const safeHistory = (messages || [])
    .filter((m) => m && m.content && m.content.trim().length > 0)
    .slice(-16)
    .map((m) => {
      const role = m.role === "assistant" || m.role === "model" ? "assistant" : "user";
      return {
        role: role,
        content: String(m.content).trim().slice(0, 4000)
      };
    });

  const isReasoningModel = modelToUse.startsWith("o1") || modelToUse.startsWith("o3");

  const todayStr = new Intl.DateTimeFormat("fr-FR", {
    dateStyle: "full",
    timeZone: "Africa/Ouagadougou"
  }).format(new Date());

  const systemPrompt = `Tu es SAM AI, l'assistant d'élite officiel propulsé par OpenAI et conçu par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.
Aujourd'hui nous sommes exactement le ${todayStr}.
Consignes impératives de qualité :
1. RÉPONSE DIRECTE : Réponds immédiatement et directement à la question sans introduction robotique ni formule stéréotypée.
2. APPROFONDISSEMENT UTILE : Si l'utilisateur demande de détailler ou développer, donne des explications concrètes, structurées et complètes.
3. INCERTITUDE : Signale clairement si un fait récent est incertain ou non confirmé.
4. Ne mentionne ton créateur que si expressément demandé.`;

  const openaiMessages = [
    { role: isReasoningModel ? "developer" : "system", content: systemPrompt },
    ...safeHistory
  ];

  try {
    const requestParams = {
      model: modelToUse,
      messages: openaiMessages,
      max_completion_tokens: 3000
    };

    const completion = await openai.chat.completions.create(requestParams);
    const choice = completion.choices && completion.choices[0];
    const textOutput = (choice && choice.message && choice.message.content) ? choice.message.content.trim() : "";

    if (!textOutput) {
      throw new Error("Réponse vide renvoyée par le modèle OpenAI.");
    }

    return {
      success: true,
      text: textOutput,
      sources: [],
      model: modelToUse,
      searchPerformed: false
    };
  } catch (apiErr) {
    console.error("Erreur OpenAI API:", apiErr.message || apiErr);
    let userMsg = "Une erreur est survenue lors de la communication avec OpenAI.";
    if (apiErr.status === 401) {
      userMsg = "Clé API OpenAI non valide. Vérifiez votre clé sur platform.openai.com.";
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
    secrets: [openAiApiKey],
    cors: true,
    maxInstances: 10
  },
  async (request) => {
    const userIdentifier = (request.auth && request.auth.uid) ? request.auth.uid : (request.rawRequest ? request.rawRequest.ip : "anonymous_user");

    if (!checkRateLimit(userIdentifier)) {
      throw new HttpsError(
        "resource-exhausted",
        "Trop de requêtes en peu de temps. Veuillez patienter une minute."
      );
    }

    const { messages, question, model } = request.data || {};
    const effectiveApiKey = openAiApiKey.value();

    try {
      const result = await handleChatCore({
        messages,
        question,
        clientModel: model,
        apiKey: effectiveApiKey
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
 * 2. Cloud Function HTTPS REST endpoint direct
 */
exports.apiSamAiChat = onRequest(
  {
    secrets: [openAiApiKey],
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

    const { messages, question, model } = req.body || {};
    const effectiveApiKey = openAiApiKey.value();

    try {
      const result = await handleChatCore({
        messages,
        question,
        clientModel: model,
        apiKey: effectiveApiKey
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
 * 3. Diagnostic et vérification du modèle OpenAI
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
      return {
        configuredModel: currentConfigured,
        isConfiguredAccessible: isConfiguredAccessible,
        totalModelsFound: modelIds.length,
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
      res.status(200).json({
        configuredModel: currentConfigured,
        isConfiguredAccessible: isConfiguredAccessible,
        totalModelsFound: modelIds.length,
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
