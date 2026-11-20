# IvalonaMines

Mine géante (style OP Prison) pour Ivalona : zones de blocs régénérées toutes les 2 h, pioche évolutive
(Vitesse, Taille, Profondeur, Récompenses), MineCoins, Cristaux et boutique, inventaire séparé dans la mine.
Paper 26.2 · Java 25 · compatible Multiverse-Core, Nexo, PlaceholderAPI / CustomNamePlates, LuckPerms.

## Compilation
Comme AzuriumVote : GitHub Actions (`.github/workflows/build.yml`) ou `mvn package` avec Java 25
→ `target/IvalonaMines-1.0.0.jar` dans `plugins/`.

## Mise en place (dans l'ordre)
1. Redémarre le serveur avec le plugin.
2. Crée le monde vide : `/mv create IvalonaMines normal -g IvalonaMines`
   (le générateur du plugin fait un monde vide ; la mine est remplie par le plugin).
3. Va dans le monde, construis ton spawn (hors de la zone) puis `/ivalonamines setspawn`.
4. Sélectionne les coins de la zone :
   - `/ivalonazone pos1 -2500 -2500` puis `/ivalonazone pos2 2499 2499` (coordonnées X Z), ou
   - `/ivalonazone baguette` (clic gauche = pos1, clic droit = pos2).
5. `/ivalonazone create mine` (toute la hauteur du monde) ou `/ivalonazone create mine -64 100`.
6. `/ivalonazone generate mine 60%stone,20%gold_ore,20%grass`
7. (conseillé avant l'ouverture) place-toi au centre et `/ivalonazone preremplir mine 15`.
8. LuckPerms : `ivalonamines.acces` est donnée à tout le monde par défaut. Pour la réserver :
   `/lp group default permission set ivalonamines.acces false` puis donne-la au groupe voulu.

## Régénération (important)
5.000 x 5.000 sur toute la hauteur = ~9,6 milliards de blocs : impossible à reposer d'un coup.
Le plugin régénère **chunk par chunk** :
- toutes les 2 h (`/ivalonazone intervalle mine 120`), la zone change de "génération" ;
- les chunks chargés sont refaits tout de suite, **les plus proches des joueurs d'abord** ;
- les autres chunks sont refaits quand un joueur s'en approche ;
- `regeneration.budget-ms-par-tick` limite le temps de calcul par tick (pas de lag) ;
- les joueurs dans la zone sont téléportés au spawn de la mine au début de la régénération ;
- un sol (`regeneration.sol`, BEDROCK par défaut) empêche de tomber dans le vide.

## Commandes
| Commande | Permission | |
|---|---|---|
| `/mine` | `ivalonamines.acces` | aller à la mine |
| `/boutiquemine` (`/cristaux`) | `ivalonamines.boutique` | boutique des Cristaux (hors mine) |
| `/minecoins [joueur]` | — / `ivalonamines.minecoins.autres` | solde |
| `/ivalonazone ...` | `ivalonamines.admin.zone` | baguette, pos1, pos2, create, generate, regen, intervalle, preremplir, delete, list, info |
| `/ivalonamines ...` | `ivalonamines.admin` | reload, setspawn, give, minecoins, cristaux, niveau, info, reset |

Autres permissions : `ivalonamines.admin.build` (casser/poser hors zone, jeter des items),
`ivalonamines.bypass.inventaire` (staff : pas d'échange d'inventaire), et `permission:` optionnelle
par niveau d'amélioration ou par offre de la boutique.

## Placeholders (PlaceholderAPI → CustomNamePlates)
`%ivalonamines_minecoins%` `%ivalonamines_minecoins_brut%` `%ivalonamines_cristaux%` `%ivalonamines_cristaux_brut%`
`%ivalonamines_niveau_<vitesse|taille|profondeur|recompenses>%` `%ivalonamines_valeur_<...>%`
`%ivalonamines_dans_mine%` `%ivalonamines_regen_<zone>%` (temps avant la prochaine régénération)

## Fichiers (plugins/IvalonaMines/)
    config.yml                  réglages généraux (monde, protections, régénération, sons, logs)
    messages/messages.yml       tous les messages
    pioche/pioche.yml           l'item pioche (nexo:xxx possible)
    pioche/ameliorations.yml    niveaux, coûts et effets des améliorations
    menus/ameliorations.yml     menu d'amélioration (clic droit avec la pioche)
    menus/boutique.yml          boutique des Cristaux (prix, commandes)
    recompenses/blocs.yml       MineCoins / chance de Cristaux par bloc
    zones/<nom>.yml             zones (gérées par /ivalonazone)
    data/joueurs/<uuid>.yml     MineCoins, Cristaux, niveaux, inventaire sauvegardé
    data/serveur.yml            spawn de la mine
    logs/<date>.log             journaux (admin, zones, achats, améliorations, entrées/sorties)

## Inventaire
En entrant dans le monde de la mine, l'inventaire (et l'XP) est enregistré **sur le disque**, vidé,
puis la pioche est donnée. En sortant (téléportation, portail, déconnexion puis retour ailleurs),
la pioche est retirée et l'inventaire d'avant est rendu. Les niveaux de la pioche sont enregistrés
sur le joueur : il la retrouve à chaque retour. Mort dans la mine : rien n'est perdu.
