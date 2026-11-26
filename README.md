# Omni — blasters pour NeoForge 1.21.1

[![Build et GameTests](https://github.com/LeclercLudwig/Omni/actions/workflows/build.yml/badge.svg)](https://github.com/LeclercLudwig/Omni/actions/workflows/build.yml)

Squelette d'un système d'armes à énergie piloté par données : projectiles visibles,
types de tirs, surchauffe, visée avec zoom, recul et dispersion.

## Lancer

```bash
./gradlew runClient
```

Java 21 est téléchargé automatiquement par Gradle si besoin. En jeu : onglet créatif **Omni**.

- **Clic gauche** : tirer (maintenu pour les armes `auto`)
- **Clic droit maintenu** : viser (zoom, dispersion et recul réduits, sensibilité réduite)
- La jauge sous le réticule montre la chaleur ; en surchauffe l'arme est bloquée jusqu'à refroidissement complet.

## Tests

```bash
./gradlew runGameTestServer
```

45 GameTests (`gametest/`) vérifient la cadence, la dispersion, la surchauffe, les dégâts,
les headshots, les effets stun/ion, la prédiction, les munitions, les modes rafale et chargé, la ventilation, les accessoires, le comportement des soldats et la parade. Ils tournent aussi sur GitHub Actions à chaque push.

## Modes de tir

| Mode | Comportement | Exemple |
|---|---|---|
| `semi` | un tir par clic | pistolet blaster |
| `auto` | tire tant que le bouton est maintenu | fusil blaster, répéteur lourd |
| `burst` | un clic = une rafale (bloc `"burst"` : `count`, `interval_ticks`), puis `fire_delay_ticks` | carabine à rafales |
| `charged` | maintenir pour charger, relâcher pour tirer ; dégâts ×`min_multiplier` → ×`max_multiplier` en `ticks` (bloc `"charge"`), tir plus épais, jauge au-dessus du réticule | fusil de précision |

La durée de charge est mesurée par le serveur (le client ne peut pas annoncer une charge qu'il n'a pas faite).

## Munitions

Chaque arme a un chargeur (bloc `"magazine"` du JSON : capacité, durée de rechargement, munition) :

- **R** (modifiable dans Options → Commandes) : recharger ; un chargeur vide se recharge tout seul au tir ;
- un rechargement consomme une **cartouche de gaz** (gratuit en créatif) : 4 cartouches = lingot de fer
  + poudre à canon + poudre de glowstone ; les soldats en lâchent 0 à 2 ;
- compteur « tirs/capacité ×réserve » sous le réticule, barre de rechargement, arme qui s'abaisse ;
- les soldats rechargent (munitions illimitées) en se mettant à couvert.

## Refroidissement actif

En surchauffe, une zone apparaît sur la jauge (35–55 % de chaleur) : appuyer sur **R** quand la jauge
la traverse **ventile** l'arme (froide tout de suite) ; en dehors, l'arme **s'enraye** (chaleur au maximum).
Hors surchauffe, R recharge.

## Accessoires

| Accessoire | Emplacement | Effet | Recette (sans forme) |
|---|---|---|---|
| Lunette | viseur | zoom ×0,6 en visée, dispersion en visée −50 % | fer + 2 vitres + redstone |
| Stabilisateur | canon | dispersion −30 %, recul −15 % | 2 fer + cuivre |
| Dissipateur thermique | canon | chaleur par tir −30 %, refroidissement +25 % | 3 cuivre + fer |
| Crosse | crosse | recul −40 % | 2 planches + cuir + fer |

Dans l'inventaire, **clic droit avec l'accessoire sur le blaster** pour le poser (l'ancien du même
emplacement revient en main) ; **clic droit main vide** pour en retirer un. Les soldats en lâchent parfois.

## Sons

Sons propres au mod, synthétisés (`assets/omni/sounds`, déclarés dans `sounds.json`) : un tir par arme,
surchauffe, ventilation, rechargement, clic à vide, charge, impact, parade, garde brisée.
Remplaçables par un pack de ressources.

## Soldats

Ennemis armés des mêmes blasters que le joueur (fusil, pistolet ou répéteur lourd) :

- gardent leurs distances, se déplacent latéralement et tirent par rafales après un temps de réaction ;
- vont se mettre à couvert (hors de vue) quand leur arme surchauffe ou qu'ils sont blessés ;
- préviennent les soldats proches quand ils repèrent une cible ; leurs tirs traversent leurs alliés ;
- précision selon la difficulté ; apparaissent la nuit par groupes de 2 à 4 (`data/omni/neoforge/biome_modifier`).

Œuf d'apparition dans l'onglet créatif **Omni**.

## Lame à énergie

Arme de mêlée qui pare les tirs de blaster :

- **clic droit maintenu** : garde ; un tir arrivant de face (cône de 55°) repart vers son origine ;
- **parade parfaite** (garde levée moins de 0,3 s avant l'impact) : le tir repart **là où vous visez**,
  plus vite et plus fort ;
- **endurance** (jauge sous le réticule) : la garde tenue s'épuise en ~3 s, une parade normale coûte 20,
  une parade parfaite rend 10 ; à zéro, la garde se brise pendant 2 s ;
- chaque parade use la lame ; un tir renvoyé change de camp et peut toucher son tireur ;
- face à une garde, les soldats retiennent leur tir 1 à 2 s en la contournant, puis tirent pour l'épuiser.

## Ajouter une arme

Créer `src/main/resources/data/omni/omni_weapons/<nom>.json` (ou dans n'importe quel datapack,
`/reload` suffit côté serveur) puis ajouter `weapon.omni.<nom>` dans les fichiers de langue.
Seul `damage` est obligatoire (les commentaires ci-dessous sont explicatifs, à retirer dans un vrai fichier) :

```jsonc
{
  "fire_mode": "auto",              // "semi" | "auto" | "burst" | "charged"
  "bolt_type": "standard",          // "standard" | "stun" | "ion"
  "damage": 5.0,
  "bolt_speed": 3.0,                // blocs/tick, 3.9 max
  "range": 96,                      // blocs avant disparition
  "fire_delay_ticks": 3,            // 20 ticks = 1 s
  "projectiles_per_shot": 1,        // >1 pour un fusil à dispersion
  "spread": { "hip": 4.0, "aim": 1.0 },                // degrés (approx.)
  "recoil": { "pitch": 1.2, "yaw_jitter": 0.6 },       // degrés
  "heat": { "per_shot": 7, "max": 100, "cooling_per_tick": 1.5 },
  "aim_fov_multiplier": 0.75,       // 0.3 = lunette
  "headshot_multiplier": 1.5,
  "bolt_color": "#FF3B30",
  "fire_sound": "minecraft:entity.firework_rocket.blast",
  "magazine": { "capacity": 30, "reload_ticks": 45, "ammo": "omni:gas_cartridge" }  // absent = illimité
}
```

## Architecture

| Fichier | Rôle |
|---|---|
| `weapon/WeaponDefinition` | Format JSON (codec) d'une arme |
| `weapon/WeaponManager` | Chargement des JSON, copie serveur + copie client synchronisée |
| `weapon/BlasterItem` | Item unique ; l'arme est choisie par le composant `omni:weapon` |
| `weapon/BlasterState` | Chaleur / surchauffe / dernier tir (composant `omni:state`, refroidissement calculé à la lecture) |
| `weapon/BlasterLogic` | Tir côté serveur : validation cadence + chaleur, apparition des projectiles |
| `entity/BoltEntity` | Projectile sans gravité, détection par rayon, headshots, effets stun/ion |
| `network/*` | `FirePayload` (client → serveur), `SyncWeaponsPayload` (serveur → client) |
| `client/ClientWeaponControls` | Entrées, visée, zoom, sensibilité, recul |
| `client/BoltRenderer` | Trait lumineux additif orienté selon la vitesse |
| `client/HeatHud` | Jauge de chaleur |
| `client/BlasterClientExtensions` | Position de l'arme en première personne (centrée en visée) |

Le serveur fait autorité : le client ne fait qu'envoyer « je tire » et pré-filtre pour le confort.

## Pistes suivantes

- Prédiction client : afficher le tir immédiatement côté tireur, avant la réponse du serveur
- Recul lissé sur plusieurs frames au lieu d'un à-coup instantané

## Licence

MIT — voir [LICENSE](LICENSE).
