# Google Maps API Key Setup

To enable Google Maps functionality in the WiFi Scanner app, you need to obtain a Google Maps API Key.

## Steps to get your API Key:

1. **Go to Google Cloud Console:**
   - Visit: https://console.cloud.google.com/

2. **Create or select a project:**
   - Create a new project or select an existing one

3. **Enable Maps SDK for Android:**
   - Go to "APIs & Services" > "Library"
   - Search for "Maps SDK for Android"
   - Click on it and press "ENABLE"

4. **Create API Key:**
   - Go to "APIs & Services" > "Credentials"
   - Click "CREATE CREDENTIALS" > "API key"
   - Copy the generated API key

5. **Restrict the API Key (recommended):**
   - Click on your API key to edit it
   - Under "Application restrictions":
     - Select "Android apps"
     - Add package name: `com.wlanscanner`
     - Add SHA-1 certificate fingerprint (get it from Android Studio or using keytool)
   - Under "API restrictions":
     - Select "Restrict key"
     - Choose "Maps SDK for Android"

6. **Add the key to your app:**
   - Replace the placeholder in `app/src/main/AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_ACTUAL_API_KEY_HERE" />
   ```

## Important Notes:

- The current key in the app is a placeholder and won't work
- Google Maps requires a valid API key to display maps
- Keep your API key secure and don't commit it to public repositories
- Consider using build variants or gradle.properties for API key management

## Without API Key:

The app will still work but the MAP tab will show a blank/error state instead of the actual map with WiFi network locations.
