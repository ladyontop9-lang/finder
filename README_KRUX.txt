KRUX CLIENT 1.8

Jeden build na GitHubie (Actions -> Artifacts -> krux-client) daje DWA pliki:
  krux-client-1.8.jar            -> dla klientow (okno z licencja, wpisuje sie raz)
  krux-client-1.8-developer.jar  -> tylko dla Ciebie (pelny dostep + generator licencji)

WAZNE: folder "dev-secret" zawiera klucz do podpisywania licencji i trafia TYLKO do jara developer.
Repozytorium na GitHubie MUSI byc PRYWATNE (Settings -> Danger Zone -> Change visibility -> Make private).
Nie wysylaj nikomu wersji developer ani folderu dev-secret.
Nie ma zadnych plikow dev.key ani konfiguracji kluczy.

Licencje: K -> panel boczny -> Developer -> typ (Lifetime / 30 days / Custom) -> notatka -> Generate license.
Klucz kopiuje sie do schowka i zapisuje w config/krux/issued.log.
Klient wkleja klucz raz (zapisuje sie w config/krux/license.key, poza jarem - aktualizacje go nie kasuja).
