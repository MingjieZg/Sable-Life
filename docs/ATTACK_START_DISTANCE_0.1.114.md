# Attack start distance — 0.1.114

All weapon modules expose Start distance. Ground strike retains its existing strikeStart setting and placement behavior. The other ten weapons use startDistance (0–128 blocks, default 0), stored by the normal virtual module serializer. The setting is on the first settings page and has EN/ZH help.

Arrow, gun, mortar and flame move their nozzle in the configured body-relative direction before spawning; mortar targeting and preview solve their trajectory from the shifted nozzle. Homing shifts both the deployment origin and formation. Laser offsets along the actual aimed or swept beam. Melee shifts the contact volume along the first marked face; pulse shifts its radial center. Pull adds the distance to its marked-face destination offset. Quake shifts its marked origin horizontally before finding the floor; target-feet casts retain the target center. Attack lengths/ranges are measured from the shifted origin, and offsetting does not disable subsequent wall collisions.

Preview uses the corresponding attack origins, including the shifted oriented melee volume. Default 0 keeps previous behavior.

Validation: Gradle build and all 58 current GameTests passed in gametest-wave114-final.log. New regression covers all ten field defaults and serialization, six marked faces, melee center, homing formation, and an oversized bullet blocked at its old outlet but able to travel from an offset origin.
