const https = require('https');
const req = https.request({
  hostname: 'generativelanguage.googleapis.com',
  path: '/v1beta/models?key=' + process.env.GEMINI_API_KEY,
  method: 'GET'
}, (res) => {
  let chunks = '';
  res.on('data', (d) => chunks += d);
  res.on('end', () => {
      const data = JSON.parse(chunks);
      data.models.forEach(m => console.log(m.name));
  });
});
req.end();
