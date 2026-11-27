const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Déclenchée automatiquement dès que l'administrateur (samsamai469@gmail.com)
 * publie un message dans Firestore `announcements/{announcementId}`.
 * Envoie une notification push FCM haute priorité au topic `all_users`
 * pour que tous les téléphones la reçoivent même lorsque l'application est fermée.
 */
exports.onAnnouncementCreated = onDocumentCreated(
  {
    document: "announcements/{announcementId}",
    region: "europe-west1",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data() || {};
    if (data.active === false) return;

    const announcementId = event.params.announcementId;
    const rawTitle = String(data.title || "Annonce Sam AI").trim();
    const body = String(data.message || "").trim();
    if (!body) return;

    const category = String(data.category || "info").toLowerCase();
    const targetUri =
      typeof data.targetUri === "string" && data.targetUri.startsWith("sam-ai://")
        ? data.targetUri
        : "sam-ai://notifications";

    const prefix =
      category === "important" || category === "urgent"
        ? "🚨 "
        : category === "update" || category === "mise_a_jour"
          ? "🆕 "
          : "📢 ";
    const displayTitle =
      rawTitle.startsWith("📢") || rawTitle.startsWith("🚨") || rawTitle.startsWith("🆕")
        ? rawTitle
        : `${prefix}${rawTitle}`;

    const message = {
      topic: "all_users",
      notification: {
        title: displayTitle,
        body: body,
      },
      data: {
        announcementId: String(announcementId),
        title: displayTitle,
        body: body,
        category: category,
        targetUri: targetUri,
      },
      android: {
        priority: "high",
        ttl: 60 * 60 * 24 * 7 * 1000, // 7 jours pour atteindre les appareils hors ligne
        notification: {
          channelId: "sam_admin_announcements_v1",
          sound: "default",
          defaultVibrateTimings: true,
        },
      },
    };

    const response = await getMessaging().send(message);
    console.log("Push FCM diffusé à all_users:", announcementId, response);
  }
);
