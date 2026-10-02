import re

with open("app/src/main/AndroidManifest.xml", "r") as f:
    manifest = f.read()

intent_filter = """
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="sam-ai" android:host="auth" android:path="/callback" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="ais-dev-h5qu2a6gtl2p4qcuhid25d-151105670688.europe-west2.run.app" android:pathPrefix="/auth/callback" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="ais-pre-h5qu2a6gtl2p4qcuhid25d-151105670688.europe-west2.run.app" android:pathPrefix="/auth/callback" />
            </intent-filter>
"""

manifest = manifest.replace("</activity>", intent_filter + "        </activity>")

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(manifest)
