# WiFi Scanner - Optymalizacja Skanowania i Mapy (v2.1)

## 🚀 Główne Optymalizacje

### 1. **Agresywne Skanowanie WiFi**
- **Poprzednio**: Interwał skanowania 5 sekund
- **Teraz**: Interwał skanowania 1.5 sekundy  
- **Skutek**: ~3x więcej sieci wykrytych podczas jazdy rowerem

### 2. **Inteligentna Optymalizacja GPS**
- **Problem**: Za dużo lokalizacji GPS (1203 lokalizacje na 133 sieci)
- **Rozwiązanie**: Lokalizacja zapisywana tylko gdy:
  - Przemieszczenie > 5 metrów ORAZ
  - Czas > 3 sekundy od ostatniego zapisu
- **Skutek**: Eliminacja duplikatów GPS, precyzyjniejsze mapowanie

### 3. **Cache Map OSM**
- **Problem**: Mapa przestawała działać po kilku minutach ciągłego skanowania
- **Rozwiązanie**: 
  - Cache plików map OSM (100MB, 30 dni)
  - Preferencyjne pobieranie z cache
  - Ustawienia offline'owe dla lepszej wydajności
- **Lokalizacja cache**: `/storage/emulated/0/Android/data/com.wlanscanner/files/osmdroid/tiles/`

## 🔧 Szczegóły Techniczne

### NetworkDatabase Optymalizacje
- `shouldAddLocation()` - sprawdza dystans i czas przed dodaniem lokalizacji
- `calculateDistance()` - formuła Haversine dla precyzyjnego pomiaru dystansu
- Ograniczenia: MIN_DISTANCE_METERS = 5.0f, MIN_TIME_MS = 3000L

### MainActivity Zmiany
- `SCAN_INTERVAL_MS`: 5000L → 1500L
- Dodano obsługę cache OSM z auto-konfiguracją
- Ustawienia pamięci cache: 4 wątki pobierania, 100MB limit

### Konfiguracja OSM
```kotlin
osmConfig.tileDownloadThreads = 4
osmConfig.tileFileSystemCacheMaxBytes = 100L * 1024L * 1024L // 100MB
osmConfig.expirationOverrideDuration = 1000L * 60L * 60L * 24L * 30L // 30 dni
```

## 📊 Oczekiwane Rezultaty

### Przed Optymalizacją (2km rower)
- 133 unikalne sieci
- 1203 lokalizacje GPS 
- ~9 lokalizacji na sieć
- Problemy z mapą po kilku minutach

### Po Optymalizacji (oczekiwane)
- 300-500+ unikalnych sieci (starsze wersje wykrywały kilkaset)
- Optymalna liczba lokalizacji GPS
- ~1-3 lokalizacje na sieć
- Stabilna praca mapy przez godziny

## 🧪 Test Plan

1. **Test Wydajności Skanowania**
   - Jazda rowerem 2km w zurbanizowanej części
   - Porównanie liczby wykrytych sieci vs poprzednia wersja
   - Oczekiwany wynik: 2-3x więcej sieci

2. **Test Optymalizacji GPS**
   - Sprawdzenie stosunku lokalizacji GPS do sieci
   - Oczekiwany wynik: ~1-3 lokalizacje na sieć

3. **Test Stabilności Mapy**
   - 30+ minut ciągłego skanowania
   - Oczekiwany wynik: Mapa działa bez problemów

## 🐛 Known Issues & Monitoring

- **Android WiFi API Limitations**: Niektóre urządzenia mogą nadal ograniczać częstotliwość skanowania
- **GPS Accuracy**: W obszarach z słabym sygnałem GPS może być mniej precyzyjny
- **Cache Storage**: Duże cache'e mogą zajmować miejsce - automatyczne czyszczenie po 30 dniach

## 📱 Instrukcje Użytkowania

1. **Pierwsze Uruchomienie**: Aplikacja skonfiguruje cache automatycznie
2. **Skanowanie Mobilne**: Idealne dla roweru/chodzenia - szybkie wykrywanie sieci
3. **Długie Sesje**: Mapa pozostanie stabilna przez dłuższy czas
4. **Eksport Danych**: Sprawdź jakość danych w zakładce Database

## 📈 Metryki do Monitorowania

- Liczba unikalnych sieci na km
- Stosunek lokalizacji GPS do sieci 
- Stabilność działania mapy
- Użycie pamięci cache
