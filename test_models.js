const https = require('https');

const models = ["gemini-1.5-flash", "gemini-2.0-flash-exp", "gemini-2.5-flash", "gemini-3.1-flash-lite-preview", "gemini-3.5-flash", "gemini-1.5-pro"];
const payload = JSON.stringify({
  "contents": [{"role": "user", "parts": [{"text": "Who is the prime minister of Burkina Faso?"}]}],
  "tools": [{"googleSearch": {}}]
});

async function testModel(model) {
  return new Promise((resolve) => {
    const req = https.request({
      hostname: 'generativelanguage.googleapis.com',
      path: '/v1beta/models/' + model + ':generateContent?key=' + process.env.GEMINI_API_KEY,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': payload.length
      }
    }, (res) => {
      let chunks = '';
      res.on('data', (d) => chunks += d);
      res.on('end', () => {
        console.log(`Model: ${model}, Status: ${res.statusCode}`);
        if (res.statusCode !== 200) console.log(chunks);
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
  for (const m of models) {
    await testModel(m);
  }
})();
