# WiFi Scanner - OpenStreetMap Integration

WiFi Scanner wykorzystuje OpenStreetMap (OSM) poprzez bibliotekę osmdroid do wizualizacji sieci WiFi na mapie.

## Funkcjonalność

### Zakładka MAP
- **Mapa OpenStreetMap**: Darmowa mapa bez wymagania kluczy API
- **Markery WiFi**: Kolorowe markery reprezentujące sieci WiFi z GPS
- **Info popup**: Kliknij marker by zobaczyć szczegóły sieci
- **Refresh**: Odświeża markery na mapie
- **Center**: Automatycznie centruje mapę na wszystkich sieciach

### Kolory markerów (na podstawie siły sygnału):
- 🟢 **Zielony**: Silny sygnał (>-50 dBm)
- 🟡 **Żółty**: Średni sygnał (-50 do -70 dBm) 
- 🔴 **Czerwony**: Słaby sygnał (<-70 dBm)

### Informacje o markerach:
- **Tytuł**: Nazwa sieci (lub "Hidden Network")
- **Szczegóły**: Siła sygnału, liczba skanów, adres GPS

## Techniczne

### Biblioteka osmdroid
- **Wersja**: 6.1.18
- **Licencja**: Apache 2.0 (darmowa)
- **Źródło map**: OpenStreetMap
- **Brak wymagań**: Żadnych kluczy API, rejestracji czy płatności

### Uprawnienia
- `INTERNET`: Pobieranie kafelków map z serwerów OSM
- `ACCESS_FINE_LOCATION`: GPS dla lokalizacji sieci WiFi

### Funkcje
- Zoom i przewijanie mapy
- Automatyczne centrowanie
- Persistentne przechowywanie ustawień mapy
- Obsługa lifecycle (onResume/onPause)

## Zalety względem Google Maps

✅ **Całkowicie darmowe** - bez limitów, bez płatności
✅ **Bez rejestracji** - nie wymaga Google Cloud Console
✅ **Bez kluczy API** - aplikacja działa dla każdego użytkownika
✅ **Open Source** - transparentne i społecznościowe
✅ **Pełna funkcjonalność** - markery, zoom, info popup

## Dla deweloperów

### Inicjalizacja
```kotlin
Configuration.getInstance().load(applicationContext, 
    android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))
```

### Konfiguracja mapy
```kotlin
osmMapView?.apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setBuiltInZoomControls(true)
    setMultiTouchControls(true)
}
```

### Dodawanie markerów
```kotlin
val marker = Marker(map)
marker.position = GeoPoint(latitude, longitude)
marker.title = "Network Name"
marker.snippet = "Signal: -45dBm | Scans: 5"
map.overlays.add(marker)
```

Aplikacja automatycznie pobiera kafelki map z serwerów OpenStreetMap i działa bez żadnych dodatkowych konfiguracji!
