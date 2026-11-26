# Omni — blasters pour NeoForge 1.21.1

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

## Ajouter une arme

Créer `src/main/resources/data/omni/omni_weapons/<nom>.json` (ou dans n'importe quel datapack,
`/reload` suffit côté serveur) puis ajouter `weapon.omni.<nom>` dans les fichiers de langue.
Seul `damage` est obligatoire (les commentaires ci-dessous sont explicatifs, à retirer dans un vrai fichier) :

```jsonc
{
  "fire_mode": "auto",              // "semi" | "auto"
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
  "fire_sound": "minecraft:entity.firework_rocket.blast"
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
- Modes `burst` et `charged` (tir chargé du sniper)
- Cartouches de gaz (munitions) et refroidissement actif (QTE)
- Vrais sons (`sounds.json` + `.ogg`) et modèles Blockbench
- Déviation au sabre via `ProjectileDeflection` (déjà géré dans `BoltEntity#tick`)
