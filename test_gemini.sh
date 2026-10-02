#!/bin/bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY" \
-H 'Content-Type: application/json' \
-d '{
    "contents": [{"role": "user", "parts": [{"text": "Who is the prime minister of Burkina Faso right now in 2026?"}]}],
    "tools": [{"googleSearch": {}}]
}'
