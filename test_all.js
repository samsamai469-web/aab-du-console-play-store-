const https = require('https');

const models = [
  "gemini-flash-lite-latest",
  "gemini-3.5-flash",
  "gemini-3.6-flash",
  "gemini-flash-latest",
  "gemini-2.5-flash-lite",
  "gemini-3.1-flash-lite-preview",
  "gemini-3.5-flash-lite",
  "gemini-1.5-flash"
];

async function testModel(model, search) {
  const start = Date.now();
  const payloadObj = {
    "contents": [{"role": "user", "parts": [{"text": "Hello"}]}],
    "generationConfig": { "maxOutputTokens": 50 }
  };
  if (search) payloadObj.tools = [{"googleSearch": {}}];
  const payload = JSON.stringify(payloadObj);

  return new Promise((resolve) => {
    const req = https.request({
      hostname: 'generativelanguage.googleapis.com',
      path: '/v1beta/models/' + model + ':generateContent?key=' + process.env.GEMINI_API_KEY,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      }
    }, (res) => {
      let chunks = '';
      res.on('data', (d) => chunks += d);
      res.on('end', () => {
        const duration = Date.now() - start;
        let errStr = '';
        if (res.statusCode !== 200) {
          try { errStr = JSON.parse(chunks).error.message; } catch(e) { errStr = chunks; }
        }
        console.log(`[${duration}ms] Model: ${model}, Search: ${search}, Status: ${res.statusCode} ${errStr ? '('+errStr.substring(0, 60)+'...)' : ''}`);
        resolve();
      });
    });
    req.on('error', (e) => {
      console.log(`Model: ${model}, Error: ${e.message}`);
      resolve();
    });
    req.write(payload);
    req.end();
  });
}

(async () => {
  console.log("=== Testing Standard ===");
  for (const m of models) await testModel(m, false);
  console.log("\n=== Testing Search ===");
  for (const m of models) await testModel(m, true);
})();
