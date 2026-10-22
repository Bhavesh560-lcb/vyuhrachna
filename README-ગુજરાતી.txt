વ્યૂહરચના — મોબાઇલ (Android APK) + વેબ, એક જ ફોલ્ડરમાં
=========================================================

  docs/   → વેબ એપ (બ્રાઉઝરમાં ખૂલે, મોબાઇલમાં "ઇન્સ્ટોલ" થાય). GitHub Pages થી લાઇવ.
  app/    → Android એપ (APK). GitHub Actions આપમેળે બનાવે.
  tools/sync_web.py → docs/ ની વેબ એપ Android માં નાખે (GitHub આપમેળે ચલાવે).

કોઈ ફાઇલ ભરવાની જરૂર નથી. પહેલી વાર એપ ખોલતાં તમારો યુઝરનેમ અને પાસવર્ડ બનાવો;
પછી દર વખતે એપ એનાથી જ ખુલશે. પૂરી માર્ગદર્શિકા Word ફાઇલમાં છે.

 1. GitHub → New repository "vyuhrachna" (Public) → આ ફોલ્ડરનું બધું અપલોડ (.github સાથે).
 2. Settings → Pages → main / docs → Save  →  વેબ લિંક.
 3. Actions → Build APK → Releases → apk-latest → vyuhrachna.apk.
