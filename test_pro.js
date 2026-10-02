const https = require('https');

const model = "gemini-3.1-pro-preview";
const payload = JSON.stringify({
  "contents": [{"role": "user", "parts": [{"text": "Who is the prime minister of Burkina Faso?"}]}],
  "tools": [{"googleSearch": {}}]
});

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
    console.log(`Status: ${res.statusCode}`);
    console.log(chunks);
  });
});
req.write(payload);
req.end();
