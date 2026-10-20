KRUX CLIENT - LICENCJE (dla dewelopera)

1) PIERWSZE URUCHOMIENIE (konfiguracja kluczy)
   - Zbuduj mod (GitHub Actions), wrzuć jar do folderu mods i uruchom grę.
   - Na ekranie tytułowym pojawi się okno "Developer tools" -> kliknij "Generate keys".
   - Klucz PRYWATNY zapisze się w .minecraft/config/krux/dev.key (to Twój sekret - zrób kopię, nigdy nie wysyłaj).
   - Klucz PUBLICZNY jest skopiowany do schowka.
   - Na GitHubie otwórz src/main/java/com/example/storagefinder/KruxPublicKey.java
     i wklej klucz publiczny w miejsce PASTE_YOUR_PUBLIC_KEY_HERE. Zatwierdź (Commit changes).
   - Pobierz nowego jara z Actions - to jest wersja DLA KLIENTÓW (nie zawiera Twojego klucza prywatnego).

2) TWOJA WERSJA (developer)
   - Ten sam jar + plik config/krux/dev.key na Twoim komputerze = tryb dewelopera:
     bez pytania o licencję i z zakładką "Developer" w menu (K -> pasek boczny -> Developer).
   - Klienci nie mają dev.key, więc dostają ekran z prośbą o klucz licencji.

3) WYSTAWIANIE LICENCJI
   - K -> pasek boczny -> Developer -> wybierz Lifetime / 30 days / Custom (liczba dni),
     wpisz notatkę (np. nick kupującego) -> "Generate license".
   - Klucz jest kopiowany do schowka i zapisywany w config/krux/issued.log (id, typ, data, notatka).
   - Wyślij klucz klientowi. Klient wkleja go raz - zapamiętuje się na stałe w config/krux/license.key.

4) OGRANICZENIA (uczciwie)
   - Licencje są podpisane (Ed25519) i działają offline: nikt bez Twojego klucza prywatnego nie wygeneruje
     działającego klucza. Ale mod to zwykły plik jar - zdeterminowana osoba może go zmodyfikować (crack).
     To zniechęca, nie jest pancerne DRM. Silniejsza ochrona wymaga serwera online (blokady, HWID).
   - Ten sam klucz można wysłać znajomemu (brak powiązania z komputerem). Do banowania/odwoływania
     konkretnych kluczy potrzebny jest serwer - da się dodać później.
   - Licencja czasowa wygasa po dacie zapisanej w kluczu (sprawdzana także podczas gry).
