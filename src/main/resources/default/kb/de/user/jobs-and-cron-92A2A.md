---
code: 92A2A
lang: de
title: Jobs und ihre geplante Ausführung
description: Erklärt wie Jobs über die Nutzerschnittstelle gestartet werden können, und wie ihre Ausführung für einen späteren Zeitpunkt oder Wiederholung vorgemerkt werden kann.
parent: SLEOK
priority: 100
permissions: ""
chapter: false
---
Manche Aktivitäten – etwa der Import größerer Datenmengen – können nicht sofort erledigt werden, da sie zu
viel Zeit benötigen. In solchen Fällen stehen sogenannte *„Jobs“* zur Verfügung, die die Aktion im Hintergrund
ausführen und protokollieren. Die Einrichtung eines solchen Jobs erfolgt gewöhnlich entweder über die
[Übersichtsseite aller Jobs](/jobs) oder über den Menüpunkt „Passende Jobs“, der sich an diversen Stellen im
System findet.

Nach der Auswahl eines Jobs fragt Sie das System nach Werten für etwaige Parameter und bietet einen Button
zum eigentlichen Starten des Jobs an. Wird dieser betätigt, dann wird für den Job ein sogenannter Prozess
erzeugt, der den Fortschritt und die Resultate der Ausführung wiedergibt. Auch nach Abschluss der Arbeiten
bleibt der Prozess – im Rahmen der eingestellten Aufbewahrungsfrist – über die
[Übersichtsseite aller Prozesse](/ps) verfügbar.

Normalerweise werden Jobs erstellt und direkt ausgeführt. Bisweilen bietet es sich jedoch auch an, einen Job
wiederkehrend oder für die zukünftige Ausführung einzurichten. Hierfür sieht das System *„Geplante
Ausführungen“* vor, die über ihre [eigene Übersichtsseite](/jobs/scheduler) zugänglich sind. Diese Übersicht
erreichen Sie auch über die Schaltfläche „Geplante Ausführungen“ oberhalb der Job-Übersicht.

## Einrichtung einer geplanten Ausführung

Wenn Sie eine geplante Ausführung [neu einrichten](/jobs/scheduler/entry/new), dann gelangen Sie nach der
Auswahl des Jobs zu den eigentlichen Einstellungen. Auf die gleiche Seite gelangen Sie, wenn Sie eine
bestehende geplante Ausführung bearbeiten, indem Sie auf die entsprechende Karte auf der
[Übersichtsseite](/jobs/scheduler) klicken. Diese Einrichtungsseite gliedert sich in bis zu drei Abschnitte.

Zuerst finden sich in einem ersten Abschnitt allgemeine Einstellungen, darunter das ausführende Nutzerkonto,
ein genereller An/Aus-Schalter, sowie ein Zähler für verbleibende Ausführungen – sollte die Ausführung nach
einer gewissen Anzahl an Wiederholung automatisch wieder deaktiviert werden.

![Allgemeine Einstellungen einer geplanten Job-Ausführung](/kb/de/user/images/jobs-general.png)

Dann folgen in einem zweiten Abschnitt die Einstellungen zur Terminierung der Ausführung. Die Logik wird im
[nachfolgenden Abschnitt](#zeitliche-planung) genau beschrieben.

Schließlich finden sich in einem dritten Abschnitt die eigentlichen Job-Parameter. Sollte der Job keine
Parameter haben, wird dieser Abschnitt nicht angezeigt.

## Zeitliche Planung

Das System verwendet für die Terminierung eine Logik, die dem bekannten UNIX-Dienst `cron` ähnelt, in Details
davon jedoch abweicht.

Grundsätzlich überprüft das System jede Minute bei jeder geplanten Ausführung, ob der Job gestartet werden
soll. Ist dies der Fall, wird der Job eingeplant und – sobald Ressourcen zur Verfügung stehen – ausgeführt.
Ab diesem Zeitpunkt steht der neue Prozess über die [entsprechende Übersichtsseite](/ps) zur Verfügung.

Um zu entscheiden, ob der Job gestartet werden soll, definiert jede geplante Ausführung sechs Muster für
Jahreszahl, Monat, Tag, Wochentag, Stunde und Minute, die mit der aktuellen Zeit verglichen werden. Stimmen
*gleichzeitig alle* sechs Muster mit der aktuellen Zeit überein, wird die Ausführung gestartet. Die sechs
Muster sind insofern `UND`-verknüpft.

![Zeiteinstellungen für eine geplante Job-Ausführung](/kb/de/user/images/jobs-scheduling.png)

Jedes der Muster kann verschiedene Formen annehmen:

- Ein Asterisk `*` oder ein leerer String passt immer. Geben Sie etwa `*` für das Jahr an, dann definieren Sie
  keine Einschränkung für das Jahr.
- Ein fester Wert erfordert genaue Übereinstimmung. Geben Sie etwa `5` für den Monat an, dann müssen wir uns
  im *Mai* befinden.
- Ein Zeitraum, also zwei Werte getrennt durch einen Bindestrich, erweitert dies auf mehrere aufeinander
  folgende Werte. Geben Sie etwa `5-7` für den Tag an, dann müssen wir uns *am 5., 6. oder 7. Tag des Monats*
  befinden.
- Eine Moduloangabe, also ein Schrägstrich gefolgt von einem Wert, erzwingt Teilbarkeit ohne Rest. Geben Sie
  etwa `/3` für die Stunde an, so wird eingeschränkt auf die *Stunden 0, 3, 6, 9, und jede weitere genau durch
  drei teilbare Stunde*.
- Diese Angaben können, durch Kommata getrennt, kombiniert werden. In diesem Fall genügt es, wenn eine der
  Angaben der Liste zutrifft. Es handelt sich also um eine `ODER`-Verknüpfung. Geben Sie etwa `2,9-11,/10` für
  die Minute an, so müssen wir uns in *Minute 0 (wegen der Moduloangabe), 2 (wegen des festen Wertes), 9 (wegen
  des Zeitraums), 10 (wegen der Moduloangabe bzw. des Zeitraumes), 11 (wegen des Zeitraumes), 20, 30, 40 oder
  50 (jeweils wegen der Moduloangabe)* befinden.

Die in den entsprechenden Mustern zu verwendenden Werte beziehen sich auf die jeweilige Zeiteinheit:

- Jahresangaben sind nicht eingeschränkt. Eine Ausführung ist jedoch nur dann möglich, wenn das aktuelle oder
  ein zukünftiges Jahr verwendet wird.
- Monatsangaben müssen zwischen 1 für Januar und 12 für Dezember liegen.
- Tagesangaben müssen zwischen 1 und 31 liegen, wobei in Kombination mit Monatsangaben die entsprechende
  Monatslänge bedacht werden muss.
- Angaben des Wochentages müssen zwischen 1 für Montag und 7 für Sonntag liegen.
- Stundenangaben müssen zwischen 0 und 23 liegen. Nachmittagsstunden werden ab 12 Uhr fortlaufend hochgezählt.
- Minutenangaben müssen zwischen 0 und 59 liegen.

> [!WARNING] Hinweis
> Bitte planen Sie Jobs vernünftig und nicht zu oft ein, sodass das System nicht überlastet wird. Nutzen Sie
> bevorzugt Nachtzeiten. Im Konfliktfall werden geplante Ausführungen ohne weitere Rücksprache zurückgestellt
> oder ausgedünnt, sodass das System für alle Nutzer verfügbar bleibt.

## Beispiele

| Jahr | Monat | Tag | Wochentag | Stunde | Minute | |
| --- | --- | --- | --- | --- | --- | --- |
| `*` | `*` | `15` | `*` | `1` | `30` | am 15. jeden Monats um 01:30 Uhr früh |
| `/2` | `1` | `1` | `*` | `12` | `0` | am 1. Januar jeden geraden Jahres um 12:00 Uhr mittags |
| `*` | `*` | `*` | `3` | `9` | `0` | an jedem Mittwoch um 09:00 Uhr früh |
| `*` | `*` | `1` | `1` | `18` | `0` | am 1. jeden Monats, wenn dieser gleichzeitig ein Montag ist, um 18:00 Uhr abends |
| `*` | `*` | `*` | `*` | `*` | `/30` | alle 30 Minuten |
| `*` | `12` | `31` | `*` | `23` | `*` | am 31. Dezember jeden Jahres, jede Minute zwischen 23:00 und 23:59 Uhr nachts |
| `*` | `*` | `*` | `*` | `*` | `*` | jede Minute |
