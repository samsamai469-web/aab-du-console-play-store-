const https = require('https');

async function testQuery(model, enableSearch) {
  const start = Date.now();
  const payloadObj = {
    "contents": [{"role": "user", "parts": [{"text": "Qui est le premier ministre du Burkina Faso ?"}]}]
  };
  if (enableSearch) {
    payloadObj.tools = [{"googleSearch": {}}];
  }
  const payload = JSON.stringify(payloadObj);

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
        const duration = Date.now() - start;
        console.log(`Model: ${model}, Search: ${enableSearch}, Status: ${res.statusCode}, Time: ${duration}ms`);
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
  console.log("=== Testing without search ===");
  await testQuery("gemini-2.5-flash-lite", false);
  await testQuery("gemini-3.5-flash", false);
  await testQuery("gemini-3.6-flash", false);

  console.log("\n=== Testing WITH search ===");
  await testQuery("gemini-2.5-flash-lite", true);
  await testQuery("gemini-3.5-flash", true);
  await testQuery("gemini-3.6-flash", true);
})();
